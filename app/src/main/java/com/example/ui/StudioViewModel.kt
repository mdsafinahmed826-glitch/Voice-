package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.AudioPlayerController
import com.example.data.audio.OnDeviceTtsSynthesizer
import com.example.data.audio.WavAudioRecorder
import com.example.data.local.GenerationRecord
import com.example.data.local.HistoryRepository
import com.example.data.local.VoxCloneDatabase
import com.example.data.model.EngineMode
import com.example.data.model.OutputFormat
import com.example.data.model.PlaybackState
import com.example.data.model.PresetScript
import com.example.data.model.ReferenceAudioInfo
import com.example.data.model.ServerHealthInfo
import com.example.data.model.StudioSettings
import com.example.data.model.SynthesisStatus
import com.example.data.model.ThemeMode
import com.example.data.network.VoiceCloneApiClient
import com.example.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

data class StudioUiState(
    val status: SynthesisStatus = SynthesisStatus.Idle,
    val settings: StudioSettings = StudioSettings(),
    val scriptText: String = "",
    val selectedLanguage: String = "en",
    val referenceAudio: ReferenceAudioInfo? = null,
    val latestOutputRecord: GenerationRecord? = null,
    val latestOutputWaveform: List<Float> = emptyList(),
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val liveMicAmplitudes: List<Float> = emptyList(),
    val playbackState: PlaybackState = PlaybackState(),
    val healthInfo: ServerHealthInfo? = null,
    val isTestingHealth: Boolean = false
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val settingsRepo = SettingsRepository(context)
    private val historyRepo = HistoryRepository(VoxCloneDatabase.getInstance(context).generationDao())
    private val recorder = WavAudioRecorder(context)
    private val player = AudioPlayerController(viewModelScope)
    private val onDeviceTts = OnDeviceTtsSynthesizer(context)
    private val apiClient = VoiceCloneApiClient(context)

    private val _status = MutableStateFlow<SynthesisStatus>(SynthesisStatus.Idle)
    private val _scriptText = MutableStateFlow("")
    private val _selectedLanguage = MutableStateFlow("en")
    private val _referenceAudio = MutableStateFlow<ReferenceAudioInfo?>(null)
    private val _latestOutput = MutableStateFlow<GenerationRecord?>(null)
    private val _latestOutputWaveform = MutableStateFlow<List<Float>>(emptyList())
    private val _healthInfo = MutableStateFlow<ServerHealthInfo?>(null)
    private val _isTestingHealth = MutableStateFlow(false)

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    val settingsFlow: StateFlow<StudioSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StudioSettings())

    val historyFlow: StateFlow<List<GenerationRecord>> = historyRepo.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<StudioUiState> = combine(
        _status,
        settingsFlow,
        _scriptText,
        _selectedLanguage,
        _referenceAudio,
        _latestOutput,
        _latestOutputWaveform,
        recorder.isRecording,
        recorder.elapsedMs,
        recorder.liveAmplitudes,
        player.playbackState,
        _healthInfo,
        _isTestingHealth
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        StudioUiState(
            status = values[0] as SynthesisStatus,
            settings = values[1] as StudioSettings,
            scriptText = values[2] as String,
            selectedLanguage = values[3] as String,
            referenceAudio = values[4] as ReferenceAudioInfo?,
            latestOutputRecord = values[5] as GenerationRecord?,
            latestOutputWaveform = values[6] as List<Float>,
            isRecording = values[7] as Boolean,
            recordingElapsedMs = values[8] as Long,
            liveMicAmplitudes = values[9] as List<Float>,
            playbackState = values[10] as PlaybackState,
            healthInfo = values[11] as ServerHealthInfo?,
            isTestingHealth = values[12] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioUiState())

    init {
        viewModelScope.launch {
            val savedSettings = settingsRepo.settingsFlow.first()
            val savedScript = settingsRepo.lastScriptFlow.first()
            _selectedLanguage.value = savedSettings.defaultLanguage
            _scriptText.value = savedScript
        }
    }

    fun updateScriptText(text: String) {
        _scriptText.value = text
        viewModelScope.launch {
            settingsRepo.saveLastScript(text)
        }
    }

    fun applyPreset(preset: PresetScript) {
        _scriptText.value = preset.text
        _selectedLanguage.value = preset.languageCode
        viewModelScope.launch {
            settingsRepo.saveLastScript(preset.text)
        }
    }

    fun selectLanguage(languageCode: String) {
        _selectedLanguage.value = languageCode
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(defaultLanguage = languageCode) }
        }
    }

    fun updateSimilarity(value: Float) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(similarity = value.coerceIn(0f, 1f)) }
        }
    }

    fun updateSpeed(value: Float) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(speed = value.coerceIn(0.5f, 2.0f)) }
        }
    }

    fun updateTemperature(value: Float) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(temperature = value.coerceIn(0.1f, 1.0f)) }
        }
    }

    fun updateOutputFormat(format: OutputFormat) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(outputFormat = format) }
        }
    }

    fun updateEngineMode(mode: EngineMode) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(engineMode = mode) }
        }
    }

    fun updateApiEndpoint(url: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(apiEndpoint = url) }
        }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(apiKey = key) }
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(themeMode = mode) }
        }
    }

    fun toggleRecording() {
        if (recorder.isRecording.value) {
            viewModelScope.launch {
                recorder.stopRecording().fold(
                    onSuccess = { info ->
                        _referenceAudio.value = info
                        _status.value = SynthesisStatus.Ready("Reference recording ready")
                    },
                    onFailure = { err ->
                        _status.value = SynthesisStatus.Error(err.localizedMessage ?: "Recording failed")
                        _snackbarEvents.emit(err.localizedMessage ?: "Recording failed")
                    }
                )
            }
        } else {
            player.stop()
            recorder.startRecording(viewModelScope).fold(
                onSuccess = {
                    _status.value = SynthesisStatus.Recording
                },
                onFailure = { err ->
                    _status.value = SynthesisStatus.Error(err.localizedMessage ?: "Mic unavailable")
                    viewModelScope.launch {
                        _snackbarEvents.emit(err.localizedMessage ?: "Mic unavailable")
                    }
                }
            )
        }
    }

    fun importReferenceAudio(uri: Uri) {
        viewModelScope.launch {
            player.stop()
            _status.value = SynthesisStatus.Uploading
            recorder.importAudioFromUri(uri).fold(
                onSuccess = { info ->
                    _referenceAudio.value = info
                    _status.value = SynthesisStatus.Ready("Reference loaded: ${info.fileName}")
                },
                onFailure = { err ->
                    _status.value = SynthesisStatus.Error(err.localizedMessage ?: "Failed to import audio")
                    _snackbarEvents.emit(err.localizedMessage ?: "Failed to import audio")
                }
            )
        }
    }

    fun clearReferenceAudio() {
        player.stop()
        _referenceAudio.value = null
        _status.value = SynthesisStatus.Idle
    }

    fun togglePlayFile(filePath: String) {
        player.togglePlay(filePath).onFailure { err ->
            viewModelScope.launch {
                _snackbarEvents.emit("Playback error: ${err.localizedMessage ?: "Unknown"}")
            }
        }
    }

    fun seekAudio(filePath: String, positionMs: Long) {
        player.seekTo(filePath, positionMs)
    }

    fun testServerConnection() {
        val currentSettings = settingsFlow.value
        viewModelScope.launch {
            _isTestingHealth.value = true
            val result = apiClient.checkHealth(currentSettings.apiEndpoint, currentSettings.apiKey)
            _healthInfo.value = result
            _isTestingHealth.value = false
        }
    }

    fun synthesizeSpeech() {
        val text = _scriptText.value.trim()
        if (text.isEmpty()) {
            _status.value = SynthesisStatus.Error("Enter script text before generating speech")
            viewModelScope.launch { _snackbarEvents.emit("Please enter text to synthesize") }
            return
        }

        val currentSettings = settingsFlow.value
        val ref = _referenceAudio.value

        if (currentSettings.engineMode == EngineMode.REMOTE_FASTAPI && ref == null) {
            _status.value = SynthesisStatus.Error("Record or upload a reference voice sample first")
            viewModelScope.launch {
                _snackbarEvents.emit("Zero-shot mode requires a reference voice sample")
            }
            return
        }

        viewModelScope.launch {
            player.stop()
            if (currentSettings.engineMode == EngineMode.REMOTE_FASTAPI && ref != null) {
                _status.value = SynthesisStatus.Uploading
                val result = apiClient.cloneVoice(
                    endpointBase = currentSettings.apiEndpoint,
                    apiKey = currentSettings.apiKey,
                    referenceFile = File(ref.filePath),
                    text = text,
                    language = _selectedLanguage.value,
                    speed = currentSettings.speed,
                    similarity = currentSettings.similarity,
                    temperature = currentSettings.temperature,
                    outputFormat = currentSettings.outputFormat,
                    onUploadCompleted = {
                        _status.value = SynthesisStatus.Generating
                    }
                )
                val friendlyResult = result.recoverCatching { err ->
                    val raw = err.localizedMessage ?: "Connection failed"
                    val isLocalEmulatorUrl = currentSettings.apiEndpoint.contains("10.0.2.2") ||
                        currentSettings.apiEndpoint.contains("localhost") ||
                        currentSettings.apiEndpoint.contains("127.0.0.1")
                    val hint = if (isLocalEmulatorUrl) {
                        "FastAPI server unreachable ($raw). On a real phone, 10.0.2.2/localhost does not work—use your PC's Wi-Fi IP or Cloud GPU HTTPS URL in Settings, or switch to On-Device TTS."
                    } else {
                        "FastAPI server error: $raw"
                    }
                    throw IllegalStateException(hint, err)
                }
                handleSynthesisResult(
                    result = friendlyResult,
                    text = text,
                    settings = currentSettings,
                    refName = ref.fileName,
                    engineLabel = "FastAPI Zero-Shot"
                )
            } else {
                _status.value = SynthesisStatus.Generating
                val basePitch = if (ref != null) {
                    estimateReferencePitchFactor(File(ref.filePath), ref.sampleRateHz, currentSettings.similarity)
                } else {
                    0.95f + (currentSettings.similarity * 0.10f)
                }
                val result = onDeviceTts.synthesizeToWav(
                    text = text,
                    languageCode = _selectedLanguage.value,
                    speed = currentSettings.speed,
                    pitch = basePitch
                )
                handleSynthesisResult(
                    result = result,
                    text = text,
                    settings = currentSettings.copy(outputFormat = OutputFormat.WAV),
                    refName = ref?.fileName ?: "Device Voice",
                    engineLabel = "On-Device TTS"
                )
            }
        }
    }

    private suspend fun estimateReferencePitchFactor(
        file: File,
        sampleRateHz: Int,
        similarity: Float
    ): Float = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.extension.equals("wav", ignoreCase = true) || file.length() <= 4096L) {
            return@withContext (0.95f + similarity * 0.10f).coerceIn(0.8f, 1.25f)
        }
        runCatching {
            val bytes = file.readBytes()
            val start = 44
            val maxSamples = minOf((bytes.size - start) / 2, sampleRateHz * 2)
            var zeroCrossings = 0
            var prevSign = 0
            var energySamples = 0
            for (i in 0 until maxSamples) {
                val lo = bytes[start + i * 2].toInt() and 0xFF
                val hi = bytes[start + i * 2 + 1].toInt()
                val sample = (hi shl 8) or lo
                if (kotlin.math.abs(sample) > 600) {
                    energySamples++
                    val sign = if (sample >= 0) 1 else -1
                    if (prevSign != 0 && sign != prevSign) {
                        zeroCrossings++
                    }
                    prevSign = sign
                }
            }
            if (energySamples < 400) return@withContext 1.0f
            val durationSec = energySamples.toFloat() / sampleRateHz.coerceAtLeast(8000).toFloat()
            val zcrHz = (zeroCrossings / 2f) / durationSec.coerceAtLeast(0.05f)
            // Map estimated vocal brightness/register smoothly into Android TTS pitch range (0.82 .. 1.22)
            val estimated = (0.84f + ((zcrHz - 180f) / 1400f) * 0.36f).coerceIn(0.82f, 1.22f)
            (1.0f * (1f - similarity) + estimated * similarity).coerceIn(0.82f, 1.22f)
        }.getOrElse { 1.0f }
    }

    private suspend fun handleSynthesisResult(
        result: Result<File>,
        text: String,
        settings: StudioSettings,
        refName: String,
        engineLabel: String
    ) {
        result.fold(
            onSuccess = { outFile ->
                val info = withContext(Dispatchers.IO) {
                    recorder.inspectAudioFile(outFile, outFile.name, isRecorded = false)
                }
                val record = GenerationRecord(
                    scriptText = text,
                    languageCode = _selectedLanguage.value,
                    outputFilePath = outFile.absolutePath,
                    outputFormat = settings.outputFormat.extension,
                    referenceName = refName,
                    engineUsed = engineLabel,
                    durationMs = info.durationMs,
                    speed = settings.speed,
                    similarity = settings.similarity,
                    temperature = settings.temperature
                )
                val insertedId = historyRepo.saveRecord(record)
                val saved = record.copy(id = insertedId)
                _latestOutput.value = saved
                _latestOutputWaveform.value = info.waveformPeaks
                _status.value = SynthesisStatus.Ready("Audio ready (${formatDuration(info.durationMs)})")
                player.playFile(outFile.absolutePath)
            },
            onFailure = { err ->
                val msg = err.localizedMessage ?: "Synthesis failed"
                _status.value = SynthesisStatus.Error(msg)
                _snackbarEvents.emit(msg)
            }
        )
    }

    fun loadHistoryRecord(record: GenerationRecord) {
        val file = File(record.outputFilePath)
        if (!file.exists()) {
            viewModelScope.launch {
                _snackbarEvents.emit("Audio file no longer exists on device")
            }
            return
        }
        _scriptText.value = record.scriptText
        _selectedLanguage.value = record.languageCode
        _latestOutput.value = record
        viewModelScope.launch {
            _latestOutputWaveform.value = withContext(Dispatchers.IO) {
                recorder.extractWaveformPeaks(file, 44)
            }
            _status.value = SynthesisStatus.Ready("Loaded from history")
            player.playFile(file.absolutePath)
        }
    }

    fun deleteHistoryRecord(record: GenerationRecord) {
        viewModelScope.launch {
            if (player.playbackState.value.activeFilePath == record.outputFilePath) {
                player.stop()
            }
            if (_latestOutput.value?.id == record.id) {
                _latestOutput.value = null
                _latestOutputWaveform.value = emptyList()
            }
            historyRepo.deleteRecord(record)
            _snackbarEvents.emit("Removed synthesis from history")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            player.stop()
            historyRepo.clearAll(historyFlow.value)
            _latestOutput.value = null
            _latestOutputWaveform.value = emptyList()
            _snackbarEvents.emit("History cleared")
        }
    }

    fun exportAudioToUri(sourceFilePath: String, destinationUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val src = File(sourceFilePath)
                require(src.exists()) { "Source audio file missing" }
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    FileInputStream(src).use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("Unable to open export destination")
            }.fold(
                onSuccess = {
                    _snackbarEvents.emit("Audio saved successfully")
                },
                onFailure = { err ->
                    _snackbarEvents.emit("Export failed: ${err.localizedMessage ?: "Unknown"}")
                }
            )
        }
    }

    fun buildShareIntent(filePath: String, mimeType: String): Intent? {
        val file = File(filePath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun readAssetText(assetName: String): String {
        return runCatching {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        }.getOrElse { "// Could not load $assetName" }
    }

    override fun onCleared() {
        super.onCleared()
        recorder.cancelAndRelease()
        player.release()
        onDeviceTts.shutdown()
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "%02d:%02d".format(minutes, seconds)
        }

        fun formatTimerWithTenths(ms: Long): String {
            val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
            val tenths = ((ms % 1000L) / 100L).coerceIn(0L, 9L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "%02d:%02d.%d".format(minutes, seconds, tenths)
        }
    }
}
