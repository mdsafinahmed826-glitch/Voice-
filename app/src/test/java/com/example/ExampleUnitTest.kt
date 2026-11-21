package com.example

import com.example.data.audio.OnDeviceTtsSynthesizer
import com.example.data.model.OutputFormat
import com.example.data.model.StudioPresets
import com.example.ui.StudioViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun durationFormatting_isAccurate() {
        assertEquals("00:00", StudioViewModel.formatDuration(0L))
        assertEquals("01:05", StudioViewModel.formatDuration(65_000L))
        assertEquals("00:08.4", StudioViewModel.formatTimerWithTenths(8_420L))
    }

    @Test
    fun presetsAndLanguages_includeRequiredOptions() {
        val codes = StudioPresets.languages.map { it.code }
        assertTrue(codes.containsAll(listOf("en", "bn", "hi")))
        assertTrue(StudioPresets.scripts.isNotEmpty())
        assertEquals("audio/wav", OutputFormat.WAV.mimeType)
    }

    @Test
    fun longTextChunking_handlesUnlimitedScripts() {
        val longText = "Hello world, this is a test sentence for unlimited speech synthesis. ".repeat(120)
        val chunks = OnDeviceTtsSynthesizer.splitTextIntoChunks(longText, 1000)
        assertTrue(chunks.size >= 8)
        assertTrue(chunks.all { it.length <= 1000 })
    }
}
