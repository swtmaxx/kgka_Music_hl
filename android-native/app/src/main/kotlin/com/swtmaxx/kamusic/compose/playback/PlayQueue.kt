package com.swtmaxx.kamusic.compose.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.swtmaxx.kamusic.compose.data.model.Song
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** 播放模式。 */
enum class PlayMode { SEQUENCE, REPEAT_ONE, SHUFFLE }

/**
 * 歌曲登记表。
 *
 * ExoPlayer 的 MediaItem 只带元数据，UI 需要完整的 Song（albumId / albumAudioId
 * 是取播放地址的必需参数），因此用同进程单例保存映射。
 *
 * **双索引**：主键是 `song.id`（在线/本地歌都有），另留一份 `hash` 索引供
 * `resolvePlayUrlByHash` 使用。本地歌没有 hash，若只用 hash 作键会被直接丢掉。
 *
 * 进程被系统回收后服务重启时表会为空，但 MediaItem 里已带 title/artist/artwork，
 * 通知栏仍能正确显示。
 */
object SongRegistry {
    private val byId = ConcurrentHashMap<String, Song>()
    private val byHash = ConcurrentHashMap<String, Song>()

    fun remember(songs: List<Song>) {
        songs.forEach { song ->
            if (song.id.isNotEmpty()) byId[song.id] = song
            if (song.hash.isNotEmpty()) byHash[song.hash] = song
        }
    }

    /** 按 `mediaId`（= `song.id`）取，供 [PlaybackController] 使用。 */
    fun get(mediaId: String?): Song? = mediaId?.let { byId[it] }

    /** 按 hash 取，供播放地址解析使用。 */
    fun getByHash(hash: String?): Song? = hash?.let { byHash[it] }

    fun clear() {
        byId.clear()
        byHash.clear()
    }
}

/** Song ↔ MediaItem 及播放模式的映射。 */
object PlaybackMapping {

    /** 惰性解析用的自定义 scheme；真实地址由 ResolvingDataSource 在播放时换取。 */
    const val SCHEME = "kamusic"

    fun toMediaItem(song: Song): MediaItem {
        val localCover = song.localCoverPath?.takeIf { it.isNotEmpty() }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .apply {
                song.albumName?.takeIf { it.isNotEmpty() }?.let { setAlbumTitle(it) }
                val artwork = localCover?.let { "file://$it" } ?: song.coverUrl
                artwork?.takeIf { it.isNotEmpty() }?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()

        // 已下载的歌直接指向本地文件（ExoPlayer 不再联网）；
        // 其余走自定义 scheme，由 ResolvingDataSource 在真正播放时换取 CDN 直链。
        val uri = song.localPath?.takeIf { it.isNotEmpty() }
            ?.let { Uri.fromFile(File(it)) }
            ?: Uri.parse("$SCHEME://song/${song.hash}")

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
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
