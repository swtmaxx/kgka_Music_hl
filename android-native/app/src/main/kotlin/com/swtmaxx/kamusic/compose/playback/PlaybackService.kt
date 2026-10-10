package com.swtmaxx.kamusic.compose.playback

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.swtmaxx.kamusic.compose.KaMusicApp
import com.swtmaxx.kamusic.compose.playback.PlaybackMapping
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * 后台播放服务（前台服务 + MediaSession）。
 *
 * 关键设计：MediaItem 的 URI 分两种：
 * - 在线歌：自定义 scheme `kamusic://song/<hash>`，真实地址由 [ResolvingDataSource]
 *   在**真正开始播放时**才去换取。这样队列里可以有任意多首歌而不必预先请求 N 次
 *   `/song/url`，同时 ExoPlayer 自己的队列、通知栏上一首/下一首、耳机按键都能正常工作。
 * - 已下载的歌：[PlaybackMapping] 直接给 `file://` URI，本层直通不解析。
 *
 * ⚠️ 底层必须用 [DefaultDataSource.Factory] 而不是 [DefaultHttpDataSource.Factory]：
 * `DefaultMediaSourceFactory` 用**同一个** `DataSource.Factory` 处理所有 URI，
 * 而 `DefaultHttpDataSource` **打不开 `file://`** —— 那样本地播放会必然失败。
 * `DefaultDataSource` 会按 scheme 自动分派 file / content / asset / rtmp / http。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val container = (application as KaMusicApp).container

        // http/https 走这份配置（UA 与下载器保持一致，已证实该 UA 能从同一 CDN 取流）
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(this, "KaMusic"))
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)

        // 按 scheme 分派：file/content 交给内置实现，http/https 交给上面的 httpFactory
        val baseFactory = DefaultDataSource.Factory(this, httpFactory)

        // 只对自定义 scheme 做解析；file:// / content:// 原样直通
        val resolvingFactory = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            if (dataSpec.uri.scheme != PlaybackMapping.SCHEME) {
                dataSpec
            } else {
                val hash = dataSpec.uri.lastPathSegment.orEmpty()
                val url = runBlocking { container.musicRepository.resolvePlayUrlByHash(hash) }
                if (url.isNullOrEmpty()) {
                    throw IOException("无法获取播放地址（hash=$hash）")
                }
                dataSpec.withUri(Uri.parse(url))
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, com.swtmaxx.kamusic.compose.MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // 用户划掉任务且没有在播放时，主动停止服务，避免常驻。
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
