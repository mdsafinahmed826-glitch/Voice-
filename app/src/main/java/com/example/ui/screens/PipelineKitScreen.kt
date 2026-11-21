package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.StudioViewModel
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun PipelineKitScreen(
    viewModel: StudioViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val serverPy = remember { viewModel.readAssetText("server.py") }
    val requirementsTxt = remember { viewModel.readAssetText("requirements.txt") }
    val webHtml = remember { viewModel.readAssetText("index.html") }
    val mlGuide = remember { viewModel.readAssetText("pipeline_guide.md") }

    fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        onShowMessage("Copied $label to clipboard")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Backend & ML Pipeline Kit",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Ready-to-run Python FastAPI server, single-file HTML/CSS/JS studio, and PyTorch zero-shot cloning architecture.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                leadingIcon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
                label = { Text("FastAPI Server") },
                modifier = Modifier.testTag("kit_tab_fastapi")
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null) },
                label = { Text("Web Studio HTML") },
                modifier = Modifier.testTag("kit_tab_web")
            )
            FilterChip(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                leadingIcon = { Icon(Icons.Rounded.MenuBook, contentDescription = null) },
                label = { Text("ML Pipeline Guide") },
                modifier = Modifier.testTag("kit_tab_guide")
            )
        }

        when (selectedTab) {
            0 -> {
                CodeArtifactCard(
                    title = "Quick Run Commands",
                    subtitle = "Localhost or Cloud GPU (RunPod / Lambda / Modal)",
                    code = "python -m venv .venv && source .venv/bin/activate\npip install -r requirements.txt\nuvicorn server:app --host 0.0.0.0 --port 8000",
                    onCopy = {
                        copyToClipboard(
                            "run commands",
                            "python -m venv .venv && source .venv/bin/activate\npip install -r requirements.txt\nuvicorn server:app --host 0.0.0.0 --port 8000"
                        )
                    }
                )
                CodeArtifactCard(
                    title = "requirements.txt",
                    subtitle = "FastAPI, librosa, noisereduce, silero-vad, PyTorch & Coqui TTS",
                    code = requirementsTxt,
                    onCopy = { copyToClipboard("requirements.txt", requirementsTxt) }
                )
                CodeArtifactCard(
                    title = "server.py",
                    subtitle = "FastAPI /health & /api/v1/clone with XTTS-v2 + CPU fallback",
                    code = serverPy,
                    onCopy = { copyToClipboard("server.py", serverPy) }
                )
            }

            1 -> {
                CodeArtifactCard(
                    title = "index.html (Single-File Web Studio)",
                    subtitle = "Dark-mode HTML/CSS/JS with MediaRecorder, localStorage, and FastAPI fetch",
                    code = webHtml,
                    onCopy = { copyToClipboard("index.html", webHtml) }
                )
            }

            else -> {
                CodeArtifactCard(
                    title = "Zero-Shot Voice Cloning ML Guide",
                    subtitle = "Preprocessing, Speaker Embeddings vs End-to-End, CUDA/CPU Inference & Eval",
                    code = mlGuide,
                    onCopy = { copyToClipboard("ML Pipeline Guide", mlGuide) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CodeArtifactCard(
    title: String,
    subtitle: String,
    code: String,
    onCopy: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(text = title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onCopy,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }
    }
}
