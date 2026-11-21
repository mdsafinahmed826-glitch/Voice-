package com.example.data.model

sealed class SynthesisStatus {
    data object Idle : SynthesisStatus()
    data object Recording : SynthesisStatus()
    data object Uploading : SynthesisStatus()
    data object Generating : SynthesisStatus()
    data class Ready(val message: String = "Synthesis complete") : SynthesisStatus()
    data class Error(val message: String) : SynthesisStatus()
}

enum class EngineMode(val label: String, val subtitle: String) {
    REMOTE_FASTAPI(
        label = "FastAPI Zero-Shot",
        subtitle = "Sends reference WAV + text to your Python XTTS/F5-TTS server"
    ),
    ON_DEVICE_TTS(
        label = "On-Device TTS",
        subtitle = "Local Android speech synthesizer (no external GPU server needed)"
    )
}

enum class ThemeMode(val label: String) {
    DARK("Dark Studio"),
    LIGHT("Daylight"),
    SYSTEM("System")
}

enum class OutputFormat(val extension: String, val mimeType: String, val label: String) {
    WAV("wav", "audio/wav", "WAV (Lossless)"),
    MP3("mp3", "audio/mpeg", "MP3 (Compact)"),
    FLAC("flac", "audio/flac", "FLAC (Hi-Res)")
}

data class LanguageOption(
    val code: String,
    val name: String,
    val nativeName: String
)

data class PresetScript(
    val id: String,
    val title: String,
    val languageCode: String,
    val text: String
)

data class ReferenceAudioInfo(
    val filePath: String,
    val fileName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val sampleRateHz: Int,
    val isRecorded: Boolean,
    val waveformPeaks: List<Float> = emptyList()
)

data class PlaybackState(
    val activeFilePath: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)

data class ServerHealthInfo(
    val isReachable: Boolean,
    val statusText: String,
    val backendModel: String = "",
    val device: String = "",
    val latencyMs: Long = 0L
)

data class StudioSettings(
    val engineMode: EngineMode = EngineMode.ON_DEVICE_TTS,
    val apiEndpoint: String = "http://10.0.2.2:8000",
    val apiKey: String = "",
    val defaultLanguage: String = "en",
    val similarity: Float = 0.78f,
    val speed: Float = 1.0f,
    val temperature: Float = 0.65f,
    val outputFormat: OutputFormat = OutputFormat.WAV,
    val themeMode: ThemeMode = ThemeMode.DARK
)

object StudioPresets {
    val languages = listOf(
        LanguageOption("en", "English", "English"),
        LanguageOption("bn", "Bengali", "বাংলা"),
        LanguageOption("hi", "Hindi", "हिन्दी"),
        LanguageOption("es", "Spanish", "Español"),
        LanguageOption("fr", "French", "Français"),
        LanguageOption("de", "German", "Deutsch"),
        LanguageOption("ja", "Japanese", "日本語"),
        LanguageOption("ar", "Arabic", "العربية")
    )

    val scripts = listOf(
        PresetScript(
            id = "podcast",
            title = "Podcast Intro",
            languageCode = "en",
            text = "Welcome back to Neural Acoustics. Today we explore how zero-shot voice cloning turns a six-second reference sample into natural, expressive speech."
        ),
        PresetScript(
            id = "bengali",
            title = "Bengali Studio",
            languageCode = "bn",
            text = "নমস্কার, ভক্সক্লোন স্টুডিওতে আপনাকে স্বাগতম। আজ আমরা বাংলা ভাষায় কৃত্রিম বুদ্ধিমত্তা নির্ভর কণ্ঠস্বর সংশ্লেষণ পরীক্ষা করছি।"
        ),
        PresetScript(
            id = "hindi",
            title = "Hindi Narration",
            languageCode = "hi",
            text = "नमस्ते, वॉक्सक्लोन स्टूडियो में आपका स्वागत है। यह आधुनिक ज़ीरो-शॉट वॉयस क्लोनिंग और स्पीच सिंथेसिस का एक सजीव उदाहरण है।"
        ),
        PresetScript(
            id = "story",
            title = "Storytelling",
            languageCode = "en",
            text = "Rain tapped softly against the studio glass as the signal locked in. Every harmonic, every breath, captured with uncanny clarity."
        ),
        PresetScript(
            id = "tech",
            title = "Product Demo",
            languageCode = "en",
            text = "Audio preprocessing resamples input speech to twenty-four kilohertz, removes background noise, and normalizes loudness before inference."
        )
    )
}
