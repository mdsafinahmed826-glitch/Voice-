package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PlaybackState
import com.example.data.model.SynthesisStatus
import com.example.ui.StudioViewModel

@Composable
fun StatusBadge(
    status: SynthesisStatus,
    modifier: Modifier = Modifier
) {
    val isActive = status is SynthesisStatus.Recording ||
        status is SynthesisStatus.Uploading ||
        status is SynthesisStatus.Generating

    val dotColor by animateColorAsState(
        targetValue = when (status) {
            is SynthesisStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
            is SynthesisStatus.Recording -> MaterialTheme.colorScheme.error
            is SynthesisStatus.Uploading, is SynthesisStatus.Generating -> MaterialTheme.colorScheme.secondary
            is SynthesisStatus.Ready -> MaterialTheme.colorScheme.tertiary
            is SynthesisStatus.Error -> MaterialTheme.colorScheme.error
        },
        label = "status_dot_color"
    )

    val labelText = when (status) {
        is SynthesisStatus.Idle -> "Idle"
        is SynthesisStatus.Recording -> "Recording"
        is SynthesisStatus.Uploading -> "Uploading"
        is SynthesisStatus.Generating -> "Generating"
        is SynthesisStatus.Ready -> "Ready"
        is SynthesisStatus.Error -> "Error"
    }

    val pulseTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = modifier.testTag("status_badge"),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun WaveformVisualizer(
    peaks: List<Float>,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    onSeekFraction: ((Float) -> Unit)? = null
) {
    val bars = if (peaks.isEmpty()) List(36) { 0.14f } else peaks

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(
                if (onSeekFraction != null) {
                    Modifier.pointerInput(bars.size) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeekFraction(frac)
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val count = bars.size.coerceAtLeast(1)
        val slotWidth = size.width / count
        val barWidth = (slotWidth * 0.56f).coerceAtLeast(3.dp.toPx())
        val maxH = size.height
        val minH = 4.dp.toPx()

        bars.forEachIndexed { index, amp ->
            val fraction = (index + 0.5f) / count.toFloat()
            val barColor = if (fraction <= progress) activeColor else inactiveColor
            val barHeight = (minH + (maxH - minH) * amp.coerceIn(0.05f, 1f))
            val top = (maxH - barHeight) / 2f
            val left = index * slotWidth + (slotWidth - barWidth) / 2f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun AudioPlayerCard(
    title: String,
    subtitle: String,
    filePath: String,
    peaks: List<Float>,
    durationFallbackMs: Long,
    playbackState: PlaybackState,
    accentColor: Color,
    testTagPrefix: String,
    onTogglePlay: () -> Unit,
    onSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit = {}
) {
    val isThisActive = playbackState.activeFilePath == filePath
    val isPlaying = isThisActive && playbackState.isPlaying
    val totalMs = if (isThisActive && playbackState.durationMs > 0L) {
        playbackState.durationMs
    } else {
        durationFallbackMs.coerceAtLeast(1L)
    }
    val currentMs = if (isThisActive) playbackState.currentPositionMs else 0L
    val progress = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledIconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${testTagPrefix}_play_button"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = accentColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause audio" else "Play audio"
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${StudioViewModel.formatDuration(currentMs)} / ${StudioViewModel.formatDuration(totalMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        WaveformVisualizer(
            peaks = peaks,
            progress = progress,
            activeColor = accentColor,
            inactiveColor = MaterialTheme.colorScheme.outline,
            onSeekFraction = { frac ->
                onSeekMs((totalMs * frac).toLong())
            }
        )

        trailingContent()
    }
}

@Composable
fun ParameterSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
}
