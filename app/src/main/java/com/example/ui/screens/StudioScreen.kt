package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.EngineMode
import com.example.data.model.OutputFormat
import com.example.data.model.StudioPresets
import com.example.data.model.SynthesisStatus
import com.example.ui.StudioUiState
import com.example.ui.StudioViewModel
import com.example.ui.components.AudioPlayerCard
import com.example.ui.components.ParameterSlider
import com.example.ui.components.StatusBadge
import com.example.ui.components.WaveformVisualizer

@Composable
fun StudioScreen(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    onExportFile: (filePath: String, suggestedName: String, mimeType: String) -> Unit,
    onShareFile: (filePath: String, mimeType: String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showTuning by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleRecording()
        } else {
            onShowMessage("Microphone permission is required to record a reference voice sample.")
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importReferenceAudio(uri)
        }
    }

    val isBusy = uiState.status is SynthesisStatus.Uploading || uiState.status is SynthesisStatus.Generating

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Banner Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_studio_hero),
                contentDescription = "VoxClone audio studio waveform banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = uiState.settings.engineMode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    StatusBadge(status = uiState.status)
                }
                Column {
                    Text(
                        text = "VoxClone Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Record or upload 5–15s clean speech, then synthesize",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1. Reference Voice Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "1. Reference Voice",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    if (uiState.isRecording) {
                        Text(
                            text = StudioViewModel.formatTimerWithTenths(uiState.recordingElapsedMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("recording_timer")
                        )
                    } else if (uiState.referenceAudio != null) {
                        IconButton(
                            onClick = { viewModel.clearReferenceAudio() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("clear_reference_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear reference audio",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (uiState.isRecording) {
                                viewModel.toggleRecording()
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    viewModel.toggleRecording()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isRecording) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("record_mic_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.isRecording) "Stop Rec" else "Record Mic")
                    }

                    OutlinedButton(
                        onClick = {
                            audioPickerLauncher.launch(arrayOf("audio/*"))
                        },
                        enabled = !uiState.isRecording && !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("upload_sample_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.UploadFile,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Audio")
                    }
                }

                AnimatedVisibility(visible = uiState.isRecording) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        WaveformVisualizer(
                            peaks = uiState.liveMicAmplitudes,
                            progress = 1f,
                            activeColor = MaterialTheme.colorScheme.error,
                            inactiveColor = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = "Recording 24 kHz mono PCM WAV • Speak clearly in a quiet space",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val ref = uiState.referenceAudio
                if (ref != null && !uiState.isRecording) {
                    AudioPlayerCard(
                        title = ref.fileName,
                        subtitle = "${ref.sampleRateHz} Hz • ${ref.sizeBytes / 1024} KB • ${if (ref.isRecorded) "Mic WAV" else "Uploaded"}",
                        filePath = ref.filePath,
                        peaks = ref.waveformPeaks,
                        durationFallbackMs = ref.durationMs,
                        playbackState = uiState.playbackState,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        testTagPrefix = "reference",
                        onTogglePlay = { viewModel.togglePlayFile(ref.filePath) },
                        onSeekMs = { ms -> viewModel.seekAudio(ref.filePath, ms) }
                    )
                }
            }
        }

        // 2. Script & Language Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. Script & Language",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${uiState.scriptText.length} chars • Unlimited",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("script_char_counter")
                    )
                }

                // Preset Script Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioPresets.scripts.forEach { preset ->
                        FilterChip(
                            selected = uiState.scriptText == preset.text,
                            onClick = { viewModel.applyPreset(preset) },
                            label = { Text(preset.title) },
                            modifier = Modifier.testTag("preset_chip_${preset.id}")
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.scriptText,
                    onValueChange = { viewModel.updateScriptText(it) },
                    placeholder = {
                        Text("Type or paste the speech script you want synthesized in the cloned voice...")
                    },
                    minLines = 4,
                    maxLines = 12,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("script_input_field")
                )

                Text(
                    text = "Target Language",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioPresets.languages.forEach { lang ->
                        val selected = uiState.selectedLanguage == lang.code
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectLanguage(lang.code) },
                            label = { Text("${lang.name} (${lang.nativeName})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("lang_chip_${lang.code}")
                        )
                    }
                }
            }
        }

        // 3. Voice Controls (Collapsible)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column {
                            Text(
                                text = "3. Voice Tuning & Format",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Sim ${(uiState.settings.similarity * 100).toInt()}% • ${"%.2f".format(uiState.settings.speed)}x • ${uiState.settings.outputFormat.extension.uppercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { showTuning = !showTuning },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_tuning_button")
                    ) {
                        Icon(
                            imageVector = if (showTuning) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (showTuning) "Collapse tuning controls" else "Expand tuning controls"
                        )
                    }
                }

                AnimatedVisibility(visible = showTuning) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ParameterSlider(
                            label = "Similarity / Stability",
                            valueText = "${(uiState.settings.similarity * 100).toInt()}%",
                            value = uiState.settings.similarity,
                            valueRange = 0.0f..1.0f,
                            onValueChange = { viewModel.updateSimilarity(it) },
                            testTag = "similarity_slider"
                        )
                        ParameterSlider(
                            label = "Speech Speed",
                            valueText = "${"%.2f".format(uiState.settings.speed)}x",
                            value = uiState.settings.speed,
                            valueRange = 0.5f..2.0f,
                            onValueChange = { viewModel.updateSpeed(it) },
                            testTag = "speed_slider"
                        )
                        ParameterSlider(
                            label = "Expressiveness (Temperature)",
                            valueText = "%.2f".format(uiState.settings.temperature),
                            value = uiState.settings.temperature,
                            valueRange = 0.1f..1.0f,
                            onValueChange = { viewModel.updateTemperature(it) },
                            testTag = "temperature_slider"
                        )

                        Text(
                            text = "Output Format",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutputFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = uiState.settings.outputFormat == fmt,
                                    onClick = { viewModel.updateOutputFormat(fmt) },
                                    label = { Text(fmt.extension.uppercase()) },
                                    modifier = Modifier.testTag("format_chip_${fmt.extension}")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Synthesize CTA Button
        Button(
            onClick = { viewModel.synthesizeSpeech() },
            enabled = !uiState.isRecording && !isBusy,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("synthesize_button")
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (uiState.status is SynthesisStatus.Uploading) "Uploading Reference..." else "Synthesizing Voice...",
                    style = MaterialTheme.typography.labelLarge
                )
            } else {
                Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (uiState.settings.engineMode == EngineMode.REMOTE_FASTAPI) {
                        "Clone & Synthesize Speech"
                    } else {
                        "Synthesize Speech (On-Device)"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        if (uiState.settings.engineMode == EngineMode.ON_DEVICE_TTS) {
            Text(
                text = "On-Device TTS active for instant offline playback. Switch to FastAPI Zero-Shot in Settings for reference voice cloning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Error Banner
        if (uiState.status is SynthesisStatus.Error) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = uiState.status.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (uiState.settings.engineMode == EngineMode.REMOTE_FASTAPI) {
                        OutlinedButton(
                            onClick = {
                                viewModel.updateEngineMode(EngineMode.ON_DEVICE_TTS)
                                viewModel.synthesizeSpeech()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "Switch to On-Device TTS & Retry",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Generated Audio Output Card
        val output = uiState.latestOutputRecord
        if (output != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("generated_output_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Generated Output",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AudioPlayerCard(
                        title = output.scriptText,
                        subtitle = "${output.engineUsed} • ${output.languageCode.uppercase()} • ${output.outputFormat.uppercase()}",
                        filePath = output.outputFilePath,
                        peaks = uiState.latestOutputWaveform,
                        durationFallbackMs = output.durationMs,
                        playbackState = uiState.playbackState,
                        accentColor = MaterialTheme.colorScheme.primary,
                        testTagPrefix = "output",
                        onTogglePlay = { viewModel.togglePlayFile(output.outputFilePath) },
                        onSeekMs = { ms -> viewModel.seekAudio(output.outputFilePath, ms) },
                        trailingContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val fmt = OutputFormat.entries.firstOrNull {
                                    it.extension.equals(output.outputFormat, ignoreCase = true)
                                } ?: OutputFormat.WAV

                                Button(
                                    onClick = {
                                        onExportFile(
                                            output.outputFilePath,
                                            "voxclone_${output.id}.${fmt.extension}",
                                            fmt.mimeType
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("download_output_button")
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Audio")
                                }

                                OutlinedButton(
                                    onClick = {
                                        onShareFile(output.outputFilePath, fmt.mimeType)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("share_output_button")
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share")
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
