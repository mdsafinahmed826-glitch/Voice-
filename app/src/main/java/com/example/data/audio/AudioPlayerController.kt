package com.example.data.audio

import android.media.MediaPlayer
import com.example.data.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioPlayerController(private val scope: CoroutineScope) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun togglePlay(filePath: String): Result<Unit> {
        val current = _playbackState.value
        if (current.activeFilePath == filePath && mediaPlayer != null) {
            return if (current.isPlaying) {
                pause()
                Result.success(Unit)
            } else {
                resume()
                Result.success(Unit)
            }
        }
        return playFile(filePath)
    }

    fun playFile(filePath: String): Result<Unit> {
        stop()
        return runCatching {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = 0L
                    )
                    progressJob?.cancel()
                }
                start()
            }
            mediaPlayer = player
            _playbackState.value = PlaybackState(
                activeFilePath = filePath,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = player.duration.toLong().coerceAtLeast(1L)
            )
            startProgressTicker()
        }
    }

    private fun pause() {
        runCatching {
            mediaPlayer?.pause()
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
            progressJob?.cancel()
        }
    }

    private fun resume() {
        runCatching {
            mediaPlayer?.start()
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
            startProgressTicker()
        }
    }

    fun seekTo(filePath: String, positionMs: Long) {
        if (_playbackState.value.activeFilePath != filePath) {
            playFile(filePath)
        }
        runCatching {
            mediaPlayer?.seekTo(positionMs.toInt())
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _playbackState.value = PlaybackState()
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive && _playbackState.value.isPlaying) {
                val player = mediaPlayer ?: break
                val pos = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
                _playbackState.value = _playbackState.value.copy(currentPositionMs = pos)
                delay(80L)
            }
        }
    }

    fun release() {
        stop()
    }
}
