package com.example.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.ReferenceAudioInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sqrt

class WavAudioRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_WAVE_BARS = 48
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var outputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _liveAmplitudes = MutableStateFlow<List<Float>>(emptyList())
    val liveAmplitudes: StateFlow<List<Float>> = _liveAmplitudes.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(scope: CoroutineScope): Result<Unit> {
        if (_isRecording.value) return Result.success(Unit)

        var selectedRate = SAMPLE_RATE
        var selectedBufferSize = 0
        var recorder: AudioRecord? = null

        for (candidateRate in intArrayOf(24000, 44100, 48000, 16000)) {
            val minBuffer = AudioRecord.getMinBufferSize(candidateRate, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBuffer <= 0) continue
            val bufSize = maxOf(minBuffer * 2, 4096)
            val candidateRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                candidateRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufSize
            )
            if (candidateRecorder.state == AudioRecord.STATE_INITIALIZED) {
                selectedRate = candidateRate
                selectedBufferSize = bufSize
                recorder = candidateRecorder
                break
            } else {
                candidateRecorder.release()
            }
        }

        if (recorder == null) {
            return Result.failure(IllegalStateException("Failed to initialize microphone AudioRecord on this device"))
        }

        val bufferSize = selectedBufferSize
        val actualSampleRate = selectedRate

        val refDir = File(context.filesDir, "references").apply { mkdirs() }
        val file = File(refDir, "mic_ref_${System.currentTimeMillis()}.wav")
        outputFile = file

        audioRecord = recorder
        _isRecording.value = true
        _elapsedMs.value = 0L
        _liveAmplitudes.value = emptyList()

        recordingJob = scope.launch(Dispatchers.IO) {
            val shortBuffer = ShortArray(bufferSize / 2)
            val byteBuffer = ByteArray(shortBuffer.size * 2)
            var totalAudioBytes = 0L
            val startTime = System.currentTimeMillis()
            var lastMeterTime = 0L

            FileOutputStream(file).use { fos ->
                // Reserve 44-byte WAV header
                fos.write(ByteArray(44))
                recorder.startRecording()

                while (isActive && _isRecording.value) {
                    val readCount = recorder.read(shortBuffer, 0, shortBuffer.size)
                    if (readCount > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until readCount) {
                            val s = shortBuffer[i]
                            sumSquares += s.toDouble() * s.toDouble()
                            byteBuffer[i * 2] = (s.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                        val bytesWritten = readCount * 2
                        fos.write(byteBuffer, 0, bytesWritten)
                        totalAudioBytes += bytesWritten

                        val now = System.currentTimeMillis()
                        _elapsedMs.value = now - startTime
                        if (now - lastMeterTime >= 60L) {
                            lastMeterTime = now
                            val rms = sqrt(sumSquares / readCount).toFloat()
                            val normalized = (rms / 9000f).coerceIn(0.06f, 1.0f)
                            val updated = (_liveAmplitudes.value + normalized).takeLast(MAX_WAVE_BARS)
                            _liveAmplitudes.value = updated
                        }
                    }
                }
            }

            runCatching {
                recorder.stop()
            }
            recorder.release()
            audioRecord = null
            writeWavHeader(file, totalAudioBytes, actualSampleRate, 1, 16)
        }

        return Result.success(Unit)
    }

    suspend fun stopRecording(): Result<ReferenceAudioInfo> = withContext(Dispatchers.IO) {
        if (!_isRecording.value) {
            return@withContext Result.failure(IllegalStateException("Not currently recording"))
        }
        _isRecording.value = false
        recordingJob?.join()
        recordingJob = null

        val file = outputFile
            ?: return@withContext Result.failure(IllegalStateException("No recorded file available"))

        if (!file.exists() || file.length() <= 44L) {
            return@withContext Result.failure(IllegalStateException("Recorded audio was empty"))
        }

        val info = inspectAudioFile(file, file.name, isRecorded = true)
        Result.success(info)
    }

    fun cancelAndRelease() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
    }

    suspend fun importAudioFromUri(uri: Uri): Result<ReferenceAudioInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var displayName = "uploaded_sample.wav"
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
            }

            val sanitized = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val refDir = File(context.filesDir, "references").apply { mkdirs() }
            val destFile = File(refDir, "ref_${System.currentTimeMillis()}_$sanitized")

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open selected audio file")

            if (destFile.length() == 0L) {
                throw IllegalStateException("Selected audio file is empty")
            }

            inspectAudioFile(destFile, displayName, isRecorded = false)
        }
    }

    fun inspectAudioFile(
        file: File,
        displayName: String = file.name,
        isRecorded: Boolean = false
    ): ReferenceAudioInfo {
        var durationMs = 0L
        var sampleRate = SAMPLE_RATE

        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                ?.toIntOrNull() ?: SAMPLE_RATE
        }
        runCatching { retriever.release() }

        if (durationMs == 0L && file.extension.equals("wav", ignoreCase = true) && file.length() > 44L) {
            val payloadBytes = file.length() - 44L
            durationMs = (payloadBytes * 1000L) / (SAMPLE_RATE * 2L)
        }

        val peaks = extractWaveformPeaks(file, 44)
        return ReferenceAudioInfo(
            filePath = file.absolutePath,
            fileName = displayName,
            durationMs = durationMs,
            sizeBytes = file.length(),
            sampleRateHz = sampleRate,
            isRecorded = isRecorded,
            waveformPeaks = peaks
        )
    }

    fun extractWaveformPeaks(file: File, barCount: Int = 44): List<Float> {
        if (!file.exists() || file.length() <= 64L) return List(barCount) { 0.2f }
        if (!file.extension.equals("wav", ignoreCase = true)) {
            // Deterministic envelope from compressed file bytes so MP3/M4A displays a rich visual fingerprint
            val bytes = runCatching { file.readBytes() }.getOrElse { return List(barCount) { 0.35f } }
            val step = maxOf(1, (bytes.size - 128) / barCount)
            return List(barCount) { idx ->
                val offset = minOf(bytes.size - 1, 128 + idx * step)
                val v = abs(bytes[offset].toInt()) / 128f
                (0.18f + v * 0.78f).coerceIn(0.15f, 1.0f)
            }
        }

        return runCatching {
            val bytes = file.readBytes()
            val start = 44
            val sampleCount = (bytes.size - start) / 2
            if (sampleCount <= 0) return List(barCount) { 0.2f }
            val samplesPerBar = maxOf(1, sampleCount / barCount)
            List(barCount) { barIdx ->
                var maxAmp = 0
                val baseSample = barIdx * samplesPerBar
                val stride = maxOf(1, samplesPerBar / 64)
                var s = 0
                while (s < samplesPerBar && baseSample + s < sampleCount) {
                    val byteIdx = start + (baseSample + s) * 2
                    if (byteIdx + 1 < bytes.size) {
                        val low = bytes[byteIdx].toInt() and 0xFF
                        val high = bytes[byteIdx + 1].toInt()
                        val sample = abs((high shl 8) or low)
                        if (sample > maxAmp) maxAmp = sample
                    }
                    s += stride
                }
                (maxAmp / 22000f).coerceIn(0.10f, 1.0f)
            }
        }.getOrElse { List(barCount) { 0.25f } }
    }

    private fun writeWavHeader(
        file: File,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8L
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }
}
