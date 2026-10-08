package com.swtmaxx.kamusic.native.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.swtmaxx.kamusic.native.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 播放器 UI 状态。**刻意不含 position**，避免高频变化触发整页重组。
 * 进度请单独订阅 [PlaybackController.positionMs]。
 */
data class PlayerUiState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val mode: PlayMode = PlayMode.SEQUENCE,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queueIndex: Int = -1,
    val errorMessage: String? = null,
    val isMuted: Boolean = false,
)

/**
 * UI 侧的播放控制器：连接 [PlaybackService] 的 MediaSession，
 * 并把 MediaController 的状态转成 Compose 可消费的 StateFlow。
 */
class PlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** 高频：当前播放位置（毫秒）。只让进度条订阅它。 */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshState()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshState()
            refreshQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshState()
            refreshQueue()
        }

        override fun onRepeatModeChanged(repeatMode: Int) = refreshState()

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = refreshState()

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            refreshQueue()
            refreshState()
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(errorMessage = error.message ?: "播放失败") }
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val mediaController = runCatching { future.get() }.getOrNull()
                controller = mediaController
                if (mediaController != null) {
                    mediaController.addListener(listener)
                    _connected.value = true
                    refreshQueue()
                    refreshState()
                    startTicker()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun release() {
        tickerJob?.cancel()
        tickerJob = null
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        _connected.value = false
    }

    // ===== 控制 =====

    /** 用给定列表替换播放队列并从 [startIndex] 开始播放。 */
    fun playQueue(songs: List<Song>, startIndex: Int = 0, autoplay: Boolean = true) {
        val controller = controller ?: return
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        SongRegistry.remember(songs)
        controller.setMediaItems(songs.map(PlaybackMapping::toMediaItem), safeIndex, 0L)
        controller.prepare()
        if (autoplay) controller.play()
        refreshQueue()
        refreshState()
    }

    /** 单曲播放（用于搜索/歌单里点击某一首，同时把整个列表作为队列）。 */
    fun playFrom(songs: List<Song>, index: Int) = playQueue(songs, index)

    fun togglePlayPause() {
        val controller = controller ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val controller = controller ?: return
        // 播放超过 3 秒时「上一首」先回到本曲开头，符合常见交互。
        if (controller.currentPosition > 3000) controller.seekTo(0) else controller.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun playAt(index: Int) {
        val controller = controller ?: return
        if (index !in 0 until controller.mediaItemCount) return
        controller.seekTo(index, 0L)
        controller.play()
    }

    fun removeAt(index: Int) {
        val controller = controller ?: return
        if (index !in 0 until controller.mediaItemCount) return
        controller.removeMediaItem(index)
        refreshQueue()
        refreshState()
    }

    fun cycleMode() {
        val controller = controller ?: return
        val current = PlaybackMapping.fromPlayer(controller.repeatMode, controller.shuffleModeEnabled)
        val next = PlaybackMapping.nextMode(current)
        controller.repeatMode = PlaybackMapping.repeatModeFor(next)
        controller.shuffleModeEnabled = PlaybackMapping.shuffleEnabledFor(next)
        refreshState()
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    /** 静音切换（手表没有独立音量键时的便利入口）。 */
    fun toggleMute() {
        val controller = controller ?: return
        val muted = controller.volume > 0f
        controller.volume = if (muted) 0f else 1f
        _state.update { it.copy(isMuted = muted) }
    }

    // ===== 内部 =====

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                val controller = controller
                if (controller != null && controller.isPlaying) {
                    _positionMs.value = controller.currentPosition.coerceAtLeast(0L)
                }
                delay(500L)
            }
        }
    }

    private fun refreshState() {
        val controller = controller
        if (controller == null) {
            _state.value = PlayerUiState()
            _positionMs.value = 0L
            return
        }
        val song = SongRegistry.get(controller.currentMediaItem?.mediaId)
        _state.update {
            it.copy(
                song = song,
                isPlaying = controller.isPlaying,
                durationMs = controller.duration.coerceAtLeast(0L),
                mode = PlaybackMapping.fromPlayer(controller.repeatMode, controller.shuffleModeEnabled),
                hasNext = controller.hasNextMediaItem(),
                hasPrevious = controller.hasPreviousMediaItem(),
                queueIndex = controller.currentMediaItemIndex,
            )
        }
        _positionMs.value = controller.currentPosition.coerceAtLeast(0L)
    }

    private fun refreshQueue() {
        val controller = controller
        if (controller == null) {
            _queue.value = emptyList()
            return
        }
        val items = buildList {
            for (index in 0 until controller.mediaItemCount) {
                SongRegistry.get(controller.getMediaItemAt(index).mediaId)?.let { add(it) }
            }
        }
        _queue.value = items
    }
}
