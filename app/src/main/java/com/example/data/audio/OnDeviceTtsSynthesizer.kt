package com.example.data.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.coroutines.resume

class OnDeviceTtsSynthesizer(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady: Boolean = false

    private suspend fun ensureInitialized(): Boolean {
        if (isReady && tts != null) return true

        // Try default system engine first, then Google TTS package explicitly
        for (enginePackage in listOf<String?>(null, "com.google.android.tts")) {
            val initialized = initEngine(enginePackage)
            if (initialized) {
                isReady = true
                return true
            }
        }
        isReady = false
        return false
    }

    private suspend fun initEngine(enginePackage: String?): Boolean {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null

        val deferred = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            val listener = TextToSpeech.OnInitListener { status ->
                if (!deferred.isCompleted) {
                    deferred.complete(status == TextToSpeech.SUCCESS)
                }
            }
            tts = if (enginePackage == null) {
                TextToSpeech(context.applicationContext, listener)
            } else {
                TextToSpeech(context.applicationContext, listener, enginePackage)
            }
        }
        val ok = withTimeoutOrNull(6_000L) { deferred.await() } ?: false
        if (!ok) {
            runCatching { tts?.shutdown() }
            tts = null
        }
        return ok
    }

    suspend fun synthesizeToWav(
        text: String,
        languageCode: String,
        speed: Float,
        pitch: Float = 1.0f
    ): Result<File> {
        val ready = ensureInitialized()
        if (!ready) {
            return Result.failure(
                IllegalStateException(
                    "Device TTS engine is not installed or disabled. Install Google Speech Services or connect a FastAPI server in Settings."
                )
            )
        }

        val engine = tts ?: return Result.failure(IllegalStateException("TTS engine not initialized"))
        val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceIn(500, 3500)
        val chunks = splitTextIntoChunks(text.trim(), maxLen)
        if (chunks.isEmpty()) {
            return Result.failure(IllegalArgumentException("Script text is empty"))
        }

        val outDir = File(context.filesDir, "outputs").apply { mkdirs() }
        val finalOutFile = File(outDir, "tts_${System.currentTimeMillis()}.wav")

        if (chunks.size == 1) {
            return synthesizeSingleChunk(engine, chunks.first(), languageCode, speed, pitch, finalOutFile)
        }

        val tempFiles = mutableListOf<File>()
        return try {
            for ((index, chunk) in chunks.withIndex()) {
                val partFile = File(context.cacheDir, "tts_part_${System.currentTimeMillis()}_$index.wav")
                val res = synthesizeSingleChunk(engine, chunk, languageCode, speed, pitch, partFile)
                if (res.isFailure) {
                    return res
                }
                tempFiles.add(partFile)
            }

            withContext(Dispatchers.IO) {
                mergeWavFiles(tempFiles, finalOutFile)
            }
            Result.success(finalOutFile)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            tempFiles.forEach { runCatching { it.delete() } }
        }
    }

    private fun selectSupportedLocale(engine: TextToSpeech, languageCode: String): Locale? {
        val candidates = buildList {
            add(Locale.forLanguageTag(languageCode))
            if (languageCode == "bn") {
                add(Locale("bn", "BD"))
                add(Locale("bn", "IN"))
            } else if (languageCode == "hi") {
                add(Locale("hi", "IN"))
            }
            add(Locale.getDefault())
            add(Locale.US)
            add(Locale.UK)
            runCatching { engine.defaultVoice?.locale }.getOrNull()?.let { add(it) }
            runCatching { engine.availableLanguages }.getOrNull()?.firstOrNull()?.let { add(it) }
        }
        for (candidate in candidates) {
            val status = runCatching { engine.setLanguage(candidate) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (status != TextToSpeech.LANG_MISSING_DATA && status != TextToSpeech.LANG_NOT_SUPPORTED) {
                return candidate
            }
        }
        return null
    }

    private suspend fun synthesizeSingleChunk(
        engine: TextToSpeech,
        chunkText: String,
        languageCode: String,
        speed: Float,
        pitch: Float,
        outFile: File
    ): Result<File> = withContext(Dispatchers.Main) {
        if (!outFile.exists()) {
            runCatching { outFile.parentFile?.mkdirs(); outFile.createNewFile() }
        }
        val selectedLocale = selectSupportedLocale(engine, languageCode)
        if (selectedLocale == null) {
            return@withContext Result.failure(
                IllegalStateException("No offline TTS voice data found on this phone. Download a voice pack in Android TTS Settings or use FastAPI mode.")
            )
        }

        engine.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
        engine.setPitch(pitch.coerceIn(0.75f, 1.35f))

        suspendCancellableCoroutine { cont ->
            val utteranceId = "vox_${System.nanoTime()}"

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        if (outFile.exists() && outFile.length() > 44L) {
                            cont.resume(Result.success(outFile))
                        } else {
                            cont.resume(Result.failure(IllegalStateException("TTS engine generated an empty audio file")))
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(Result.failure(IllegalStateException("On-device TTS synthesis failed")))
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(Result.failure(IllegalStateException("On-device TTS error (code $errorCode)")))
                    }
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val res = engine.synthesizeToFile(chunkText, params, outFile, utteranceId)
            if (res != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resume(Result.failure(IllegalStateException("Failed to queue TTS synthesis (status $res)")))
            }
        }
    }

    private fun mergeWavFiles(parts: List<File>, output: File) {
        val first = parts.firstOrNull { it.exists() && it.length() > 44L }
            ?: throw IllegalStateException("No audio parts generated")

        val header = ByteArray(44)
        FileInputStream(first).use { fis ->
            if (fis.read(header) < 44) {
                throw IllegalStateException("Invalid WAV header")
            }
        }

        var totalDataBytes = 0L
        FileOutputStream(output).use { fos ->
            fos.write(header)
            val buffer = ByteArray(8192)
            for (part in parts) {
                if (!part.exists() || part.length() <= 44L) continue
                FileInputStream(part).use { fis ->
                    fis.skip(44L)
                    while (true) {
                        val read = fis.read(buffer)
                        if (read <= 0) break
                        fos.write(buffer, 0, read)
                        totalDataBytes += read
                    }
                }
            }
        }

        val totalRiffSize = (totalDataBytes + 36L).coerceAtMost(0xFFFFFFFFL)
        val dataSize = totalDataBytes.coerceAtMost(0xFFFFFFFFL)
        RandomAccessFile(output, "rw").use { raf ->
            raf.seek(4)
            raf.write(intToLittleEndian(totalRiffSize.toInt()))
            raf.seek(40)
            raf.write(intToLittleEndian(dataSize.toInt()))
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        isReady = false
    }

    companion object {
        fun splitTextIntoChunks(text: String, maxLen: Int): List<String> {
            val cleaned = text.trim()
            if (cleaned.isEmpty()) return emptyList()
            if (cleaned.length <= maxLen) return listOf(cleaned)

            val chunks = mutableListOf<String>()
            var start = 0
            while (start < cleaned.length) {
                val endLimit = minOf(cleaned.length, start + maxLen)
                if (endLimit == cleaned.length) {
                    chunks.add(cleaned.substring(start).trim())
                    break
                }
                var splitAt = -1
                for (delims in listOf(".", "!", "?", "।", "\n", ",", " ")) {
                    val idx = cleaned.lastIndexOf(delims, endLimit - 1)
                    if (idx >= start + maxLen / 3) {
                        splitAt = idx + delims.length
                        break
                    }
                }
                if (splitAt <= start) {
                    splitAt = endLimit
                }
                val part = cleaned.substring(start, splitAt).trim()
                if (part.isNotEmpty()) chunks.add(part)
                start = splitAt
                while (start < cleaned.length && cleaned[start].isWhitespace()) {
                    start++
                }
            }
            return chunks
        }
    }
}
