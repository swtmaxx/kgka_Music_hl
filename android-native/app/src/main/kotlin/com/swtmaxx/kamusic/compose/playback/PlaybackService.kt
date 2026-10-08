package com.swtmaxx.kamusic.compose.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.swtmaxx.kamusic.compose.KaMusicApp
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * 后台播放服务（前台服务 + MediaSession）。
 *
 * 关键设计：MediaItem 的 URI 使用自定义 scheme `kamusic://song/<hash>`，
 * 真实播放地址由 [ResolvingDataSource] 在**真正开始播放时**才去换取。
 * 这样队列里可以有任意多首歌而不必预先请求 N 次 `/song/url`，
 * 同时 ExoPlayer 自己的队列、通知栏上一首/下一首、耳机按键都能正常工作。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val container = (application as KaMusicApp).container

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(this, "KaMusic"))
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)

        // 播放时才解析真实地址；解析失败抛 IOException，ExoPlayer 会走错误回调。
        val resolvingFactory = ResolvingDataSource.Factory(httpFactory) { dataSpec ->
            val hash = dataSpec.uri.lastPathSegment.orEmpty()
            val url = runBlocking { container.musicRepository.resolvePlayUrlByHash(hash) }
            if (url.isNullOrEmpty()) {
                throw IOException("无法获取播放地址（hash=$hash）")
            }
            dataSpec.withUri(android.net.Uri.parse(url))
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
