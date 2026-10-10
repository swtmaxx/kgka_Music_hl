package com.swtmaxx.kamusic.compose.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.swtmaxx.kamusic.compose.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 睡眠定时模式。 */
enum class SleepTimerMode {
    /** 未开启。 */
    OFF,

    /** 倒计时（到点直接暂停）。 */
    COUNTDOWN,

    /** 到点后播完当前曲再暂停（不会听到一半被打断）。 */
    END_OF_TRACK,
}

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

    /** 睡眠定时模式（低频，可参与整页重组）。 */
    private val _sleepMode = MutableStateFlow(SleepTimerMode.OFF)
    val sleepMode: StateFlow<SleepTimerMode> = _sleepMode.asStateFlow()

    /**
     * 睡眠定时剩余毫秒（**高频，每秒一次**）。
     *
     * 单独一条流，避免每秒触发播放页整页重组。
     */
    private val _sleepRemainingMs = MutableStateFlow(0L)
    val sleepRemainingMs: StateFlow<Long> = _sleepRemainingMs.asStateFlow()

    /** 倒计时终点（墙钟毫秒）。0 表示未在倒计时。 */
    private var sleepEndAtMs = 0L
    private var sleepTickerJob: Job? = null

    // ===== 播放失败自愈 =====

    /** 连续失败首数。到 [Player.getMediaItemCount] 就停，不再无限跳。 */
    private var failStreak = 0

    /** 上一次失败的 mediaId；同一次失败 ExoPlayer 可能回调多次。 */
    private var lastFailedMediaId: String? = null

    /** 「播完当前曲就停」标志。 */
    private var stopAfterCurrentTrack = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshState()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastFailedMediaId = null
            // 「到点播完当前曲」：ExoPlayer 自动推进到下一首后立刻暂停
            if (stopAfterCurrentTrack &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            ) {
                stopAfterCurrentTrack = false
                _sleepMode.value = SleepTimerMode.OFF
                controller?.pause()
            }
            refreshState()
            refreshQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                // 真的放出来了才算成功，重置失败计数
                failStreak = 0
                lastFailedMediaId = null
            }
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
            val c = controller ?: return
            val failedId = c.currentMediaItem?.mediaId
            // 同一次失败 ExoPlayer 可能回调多次，只处理第一次
            if (failedId != null && failedId == lastFailedMediaId) return
            lastFailedMediaId = failedId

            _state.update { it.copy(errorMessage = friendlyMessage(error)) }

            // 自愈三级：
            //   ① 本地文件优先 —— 已下载的歌在 toMediaItem 里就直接给了 file://，天然成立
            //   ② 音质降级   —— 由 MusicRepository.resolvePlayUrl 的降级链处理
            //   ③ 跳下一首   —— 这里
            failStreak++
            if (failStreak >= c.mediaItemCount.coerceAtLeast(1)) return
            if (c.hasNextMediaItem()) c.seekToNextMediaItem()
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
        sleepTickerJob?.cancel()
        sleepTickerJob = null
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

    // ===== 睡眠定时 =====

    /**
     * 设置倒计时睡眠定时。[minutes] <= 0 表示关闭。
     *
     * 计时用**墙钟**（`System.currentTimeMillis()`）而不是累加 `delay` ——
     * 手表进入 Doze / 系统休眠时 `delay` 会漂移，累加会越走越慢。
     */
    fun setSleepTimer(minutes: Int) {
        sleepTickerJob?.cancel()
        sleepTickerJob = null
        stopAfterCurrentTrack = false

        if (minutes <= 0) {
            sleepEndAtMs = 0L
            _sleepMode.value = SleepTimerMode.OFF
            _sleepRemainingMs.value = 0L
            return
        }

        sleepEndAtMs = System.currentTimeMillis() + minutes * 60_000L
        _sleepMode.value = SleepTimerMode.COUNTDOWN
        _sleepRemainingMs.value = minutes * 60_000L
        sleepTickerJob = scope.launch {
            while (true) {
                val remaining = sleepEndAtMs - System.currentTimeMillis()
                if (remaining <= 0L) {
                    sleepEndAtMs = 0L
                    _sleepRemainingMs.value = 0L
                    _sleepMode.value = SleepTimerMode.OFF
                    controller?.pause()
                    break
                }
                _sleepRemainingMs.value = remaining
                delay(1000L)
            }
        }
    }

    /** 「到点播完当前曲」：不立刻停，等这首自然播完再暂停。 */
    fun setSleepEndOfTrack(enabled: Boolean) {
        sleepTickerJob?.cancel()
        sleepTickerJob = null
        sleepEndAtMs = 0L
        _sleepRemainingMs.value = 0L
        stopAfterCurrentTrack = enabled
        _sleepMode.value = if (enabled) SleepTimerMode.END_OF_TRACK else SleepTimerMode.OFF
    }

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

/**
 * 把 Media3 的内部错误码翻译成用户能看懂的中文。
 *
 * 直接显示 `error.message` 会得到类似
 * `Source error: InvalidResponseCodeException: Response code: 403` 这种串，
 * 在 240px 的屏上又长又无意义。
 */
internal fun friendlyMessage(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    -> "网络连接失败"

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "服务器返回异常"
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "文件不存在"
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "没有读取权限"

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    -> "音频文件损坏"

    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    -> "不支持这种音频格式"

    else -> error.message?.takeIf { it.isNotBlank() } ?: "播放失败"
}
