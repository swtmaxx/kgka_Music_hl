package com.swtmaxx.kamusic.compose.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.swtmaxx.kamusic.compose.data.model.Song
import java.util.concurrent.ConcurrentHashMap

/** 播放模式。 */
enum class PlayMode { SEQUENCE, REPEAT_ONE, SHUFFLE }

/**
 * 歌曲登记表：`mediaId(hash)` → [Song]。
 *
 * ExoPlayer 的 MediaItem 只带元数据，UI 需要完整的 Song（albumId / albumAudioId
 * 是取播放地址的必需参数），因此用同进程单例保存映射。
 *
 * 进程被系统回收后服务重启时表会为空，但 MediaItem 里已带 title/artist/artwork，
 * 通知栏仍能正确显示。
 */
object SongRegistry {
    private val map = ConcurrentHashMap<String, Song>()

    fun remember(songs: List<Song>) {
        songs.forEach { if (it.hash.isNotEmpty()) map[it.hash] = it }
    }

    fun get(hash: String?): Song? = hash?.let { map[it] }

    fun clear() = map.clear()
}

/** Song ↔ MediaItem 及播放模式的映射。 */
object PlaybackMapping {

    /** 惰性解析用的自定义 scheme；真实地址由 ResolvingDataSource 在播放时换取。 */
    const val SCHEME = "kamusic"

    fun toMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .apply {
                song.albumName?.takeIf { it.isNotEmpty() }?.let { setAlbumTitle(it) }
                song.coverUrl?.takeIf { it.isNotEmpty() }?.let { setArtworkUri(android.net.Uri.parse(it)) }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(song.hash)
            .setUri("$SCHEME://song/${song.hash}")
            .setMediaMetadata(metadata)
            .build()
    }

    fun repeatModeFor(mode: PlayMode): Int = when (mode) {
        PlayMode.SEQUENCE -> Player.REPEAT_MODE_OFF
        PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        PlayMode.SHUFFLE -> Player.REPEAT_MODE_ALL
    }

    fun shuffleEnabledFor(mode: PlayMode): Boolean = mode == PlayMode.SHUFFLE

    fun fromPlayer(repeatMode: Int, shuffleEnabled: Boolean): PlayMode = when {
        repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.REPEAT_ONE
        shuffleEnabled -> PlayMode.SHUFFLE
        else -> PlayMode.SEQUENCE
    }

    fun nextMode(mode: PlayMode): PlayMode = when (mode) {
        PlayMode.SEQUENCE -> PlayMode.REPEAT_ONE
        PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
        PlayMode.SHUFFLE -> PlayMode.SEQUENCE
    }

    fun modeLabel(mode: PlayMode): String = when (mode) {
        PlayMode.SEQUENCE -> "顺序播放"
        PlayMode.REPEAT_ONE -> "单曲循环"
        PlayMode.SHUFFLE -> "随机播放"
    }
}
