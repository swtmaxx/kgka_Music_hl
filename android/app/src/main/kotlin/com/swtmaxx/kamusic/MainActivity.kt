package com.swtmaxx.kamusic

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.DynamicsProcessing
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.ryanheise.audioservice.AudioServiceActivity
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord

class MainActivity : AudioServiceActivity() {
    private var superLyricChannel: MethodChannel? = null
    private var superLyricRegistered = false
    private var bassBoost: BassBoost? = null
    private var bassBoostSessionId: Int? = null
    private var equalizer: Equalizer? = null
    private var equalizerSessionId: Int? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var dynamicsProcessingSessionId: Int? = null
    private var pendingPermissionResult: MethodChannel.Result? = null

    companion object {
        private const val REQUEST_READ_AUDIO = 1001
        private const val TAG_SUPER_LYRIC = "SuperLyricPublisher"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "kgka_music_hl/screen")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "setKeepScreenOn" -> {
                        val enabled = call.arguments as? Boolean ?: false
                        if (enabled) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "kgka_music_hl/audio_effects")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getEqualizerConfig" -> {
                        val audioSessionId = call.argument<Int>("audioSessionId")
                        runCatching {
                            equalizerConfig(audioSessionId)
                        }.onSuccess { config ->
                            result.success(config)
                        }.onFailure { error ->
                            result.error("equalizer_config_failed", error.message, null)
                        }
                    }
                    "configureEqualizer" -> {
                        val audioSessionId = call.argument<Int>("audioSessionId")
                        val enabled = call.argument<Boolean>("enabled") ?: false
                        val levels = call.argument<List<Int>>("levels") ?: emptyList()

                        runCatching {
                            configureEqualizer(audioSessionId, enabled, levels)
                        }.onSuccess { supported ->
                            result.success(supported)
                        }.onFailure { error ->
                            releaseEqualizer()
                            result.error("equalizer_failed", error.message, null)
                        }
                    }
                    "configureBassBoost" -> {
                        val audioSessionId = call.argument<Int>("audioSessionId")
                        val enabled = call.argument<Boolean>("enabled") ?: false
                        val strength = call.argument<Int>("strength") ?: 0

                        runCatching {
                            configureBassBoost(audioSessionId, enabled, strength)
                        }.onSuccess { supported ->
                            result.success(supported)
                        }.onFailure { error ->
                            releaseBassBoost()
                            result.error("bass_boost_failed", error.message, null)
                        }
                    }
                    "configureVolumeNormalization" -> {
                        val audioSessionId = call.argument<Int>("audioSessionId")
                        val enabled = call.argument<Boolean>("enabled") ?: false

                        runCatching {
                            configureVolumeNormalization(audioSessionId, enabled)
                        }.onSuccess { supported ->
                            result.success(supported)
                        }.onFailure { error ->
                            releaseDynamicsProcessing()
                            result.error("volume_normalization_failed", error.message, null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "kgka_music_hl/local_music")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "hasPermission" -> {
                        result.success(hasReadAudioPermission())
                    }
                    "requestPermission" -> {
                        if (hasReadAudioPermission()) {
                            result.success(true)
                        } else {
                            pendingPermissionResult = result
                            requestAudioPermission()
                        }
                    }
                    "getLocalSongs" -> {
                        if (!hasReadAudioPermission()) {
                            result.error("no_permission", "READ_MEDIA_AUDIO permission not granted", null)
                            return@setMethodCallHandler
                        }
                        runCatching {
                            queryLocalSongs()
                        }.onSuccess { songs ->
                            result.success(songs)
                        }.onFailure { error ->
                            result.error("query_failed", error.message, null)
                        }
                    }
                    "getAlbumArt" -> {
                        val albumId = call.argument<Number>("albumId")?.toLong()
                        if (albumId == null || albumId <= 0) {
                            result.success(null)
                            return@setMethodCallHandler
                        }
                        runCatching {
                            getAlbumArtBytes(albumId)
                        }.onSuccess { bytes ->
                            result.success(bytes)
                        }.onFailure { error ->
                            result.error("album_art_failed", error.message, null)
                        }
                    }
                    "getEmbeddedLyrics" -> {
                        val filePath = call.argument<String>("filePath")
                        if (filePath.isNullOrEmpty()) {
                            result.success(null)
                            return@setMethodCallHandler
                        }
                        runCatching {
                            getEmbeddedLyrics(filePath)
                        }.onSuccess { lyrics ->
                            result.success(lyrics)
                        }.onFailure { error ->
                            result.error("lyrics_failed", error.message, null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }


        // SuperLyricApi 歌词发布到系统服务
        superLyricChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "kgka_music_hl/super_lyric"
        )
        superLyricChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "isAvailable" -> {
                    runCatching {
                        SuperLyricHelper.isAvailable()
                    }.onSuccess { available ->
                        result.success(available)
                    }.onFailure { error ->
                        Log.w(TAG_SUPER_LYRIC, "isAvailable failed: ${error.message}")
                        result.success(false)
                    }
                }
                "registerPublisher" -> {
                    runCatching {
                        if (!superLyricRegistered) {
                            SuperLyricHelper.registerPublisher()
                            superLyricRegistered = true
                        }
                        // 以系统服务里的真实注册状态为准（本地标志位可能过期）
                        val registered = runCatching {
                            SuperLyricHelper.isPublisherRegistered()
                        }.getOrNull() ?: superLyricRegistered
                        Log.d(TAG_SUPER_LYRIC, "registerPublisher: registered=$registered")
                        result.success(registered)
                    }.onFailure { error ->
                        Log.w(TAG_SUPER_LYRIC, "registerPublisher failed: ${error.message}")
                        result.success(false)
                    }
                }
                "unregisterPublisher" -> {
                    runCatching {
                        if (superLyricRegistered) {
                            SuperLyricHelper.unregisterPublisher()
                            superLyricRegistered = false
                        }
                        result.success(null)
                    }.onFailure {
                        result.success(null)
                    }
                }
                "sendLyric" -> {
                    runCatching {
                        // 自愈：启动时注册可能因系统服务未就绪而失败，
                        // 这里在首次发送前补注册一次（幂等，服务端按 UID 去重）
                        if (!superLyricRegistered) {
                            SuperLyricHelper.registerPublisher()
                            superLyricRegistered = true
                        }
                        val title = call.argument<String>("title") ?: ""
                        val artist = call.argument<String>("artist") ?: ""
                        val album = call.argument<String>("album") ?: ""
                        val lyricText = call.argument<String>("lyricText") ?: ""
                        // ⚠️ Dart int 经 MethodChannel 传输后，能放进 32 位时是 Integer，
                        // 直接 cast Long 会抛 ClassCastException，必须经 Number.toLong() 转换
                        val lyricStartTime =
                            (call.argument<Number>("lyricStartTime"))?.toLong() ?: 0L
                        val lyricEndTime =
                            (call.argument<Number>("lyricEndTime"))?.toLong() ?: 0L
                        val secondaryText = call.argument<String>("secondaryText")
                        val translationText = call.argument<String>("translationText")
                        val words = call.argument<List<Map<String, Any>>>("words")

                        val lyricData = SuperLyricData()
                            .setTitle(title)
                            .setArtist(artist)
                            .setAlbum(album)

                        // 主歌词行（含逐字）
                        val lyricWords: Array<SuperLyricWord>? = words?.mapNotNull { w ->
                            val wordText = w["word"] as? String
                            val wordStart = (w["startTime"] as? Number)?.toLong()
                            val wordEnd = (w["endTime"] as? Number)?.toLong()
                            if (wordText != null && wordStart != null && wordEnd != null) {
                                SuperLyricWord(wordText, wordStart, wordEnd)
                            } else null
                        }?.takeIf { it.isNotEmpty() }?.toTypedArray()

                        val mainLyric = if (lyricWords != null) {
                            SuperLyricLine(lyricText, lyricWords, lyricStartTime, lyricEndTime)
                        } else {
                            SuperLyricLine(lyricText, lyricStartTime, lyricEndTime)
                        }
                        lyricData.setLyric(mainLyric)

                        // 副歌词行（音译/罗马音）
                        if (!secondaryText.isNullOrBlank()) {
                            lyricData.setSecondary(
                                SuperLyricLine(secondaryText, lyricStartTime, lyricEndTime)
                            )
                        }
                        // 翻译行
                        if (!translationText.isNullOrBlank()) {
                            lyricData.setTranslation(
                                SuperLyricLine(translationText, lyricStartTime, lyricEndTime)
                            )
                        }
                        SuperLyricHelper.sendLyric(lyricData)
                        Log.d(
                            TAG_SUPER_LYRIC,
                            "sendLyric ok: \"$lyricText\" ($lyricStartTime-$lyricEndTime ms), " +
                                "words=${lyricWords?.size ?: 0}, " +
                                "secondary=${!secondaryText.isNullOrBlank()}, " +
                                "translation=${!translationText.isNullOrBlank()}"
                        )
                        result.success(true)
                    }.onFailure { error ->
                        Log.w(TAG_SUPER_LYRIC, "sendLyric failed: ${error.message}")
                        result.error("send_lyric_failed", error.message, null)
                    }
                }
                "sendStop" -> {
                    runCatching {
                        if (!superLyricRegistered) {
                            SuperLyricHelper.registerPublisher()
                            superLyricRegistered = true
                        }
                        SuperLyricHelper.sendStop(SuperLyricData())
                        Log.d(TAG_SUPER_LYRIC, "sendStop ok")
                        result.success(true)
                    }.onFailure { error ->
                        Log.w(TAG_SUPER_LYRIC, "sendStop failed: ${error.message}")
                        result.error("send_stop_failed", error.message, null)
                    }
                }
                "debugState" -> {
                    result.success(
                        mapOf(
                            "serviceAvailable" to
                                (runCatching { SuperLyricHelper.isAvailable() }.getOrNull() ?: false),
                            "localRegisteredFlag" to superLyricRegistered,
                            "publisherRegistered" to
                                (runCatching { SuperLyricHelper.isPublisherRegistered() }
                                    .getOrNull() ?: false),
                        )
                    )
                }
                else -> result.notImplemented()
            }
        }

    }

    private fun readAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun hasReadAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, readAudioPermission()) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(readAudioPermission()),
            REQUEST_READ_AUDIO
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_AUDIO) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            pendingPermissionResult?.success(granted)
            pendingPermissionResult = null
        }
    }

    private fun queryLocalSongs(): List<Map<String, Any?>> {
        val songs = mutableListOf<Map<String, Any?>>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.IS_MUSIC,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.let {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "未知歌曲"
                    val artist = it.getString(artistColumn) ?: "未知艺人"
                    val album = it.getString(albumColumn) ?: ""
                    val duration = it.getLong(durationColumn)
                    val filePath = it.getString(dataColumn) ?: ""
                    val albumId = it.getLong(albumIdColumn)

                    // 构建专辑封面 URI
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )

                    if (filePath.isNotEmpty()) {
                        songs.add(
                            mapOf(
                                "id" to filePath,
                                "title" to title,
                                "artist" to artist,
                                "album" to album,
                                "duration" to duration,
                                "filePath" to filePath,
                                "albumArtUri" to albumArtUri.toString(),
                            )
                        )
                    }
                }
            }
        } finally {
            cursor?.close()
        }

        return songs
    }

    private fun equalizerConfig(audioSessionId: Int?): Map<String, Any>? {
        if (audioSessionId == null || audioSessionId <= 0) {
            return null
        }
        val effect = ensureEqualizer(audioSessionId)
        val range = effect.bandLevelRange
        val bands = (0 until effect.numberOfBands).map { index ->
            val band = index.toShort()
            mapOf(
                "centerHz" to effect.getCenterFreq(band) / 1000,
                "level" to effect.getBandLevel(band).toInt()
            )
        }
        return mapOf(
            "range" to listOf(range[0].toInt(), range[1].toInt()),
            "bands" to bands
        )
    }

    private fun configureEqualizer(
        audioSessionId: Int?,
        enabled: Boolean,
        levels: List<Int>
    ): Boolean {
        if (!enabled) {
            releaseEqualizer()
            return true
        }
        if (audioSessionId == null || audioSessionId <= 0) {
            return false
        }

        val effect = ensureEqualizer(audioSessionId)
        val range = effect.bandLevelRange
        val bandCount = minOf(effect.numberOfBands.toInt(), levels.size)
        for (index in 0 until bandCount) {
            val level = levels[index].coerceIn(range[0].toInt(), range[1].toInt())
            effect.setBandLevel(index.toShort(), level.toShort())
        }
        effect.enabled = true
        return true
    }

    private fun ensureEqualizer(audioSessionId: Int): Equalizer {
        if (equalizerSessionId == audioSessionId && equalizer != null) {
            return equalizer!!
        }
        releaseEqualizer()
        return Equalizer(0, audioSessionId).also {
            equalizer = it
            equalizerSessionId = audioSessionId
        }
    }

    private fun releaseEqualizer() {
        equalizer?.runCatching {
            enabled = false
            release()
        }
        equalizer = null
        equalizerSessionId = null
    }

    private fun configureBassBoost(
        audioSessionId: Int?,
        enabled: Boolean,
        strength: Int
    ): Boolean {
        if (!enabled) {
            releaseBassBoost()
            return true
        }
        if (audioSessionId == null || audioSessionId <= 0) {
            return false
        }

        val effect = if (bassBoostSessionId == audioSessionId && bassBoost != null) {
            bassBoost!!
        } else {
            releaseBassBoost()
            BassBoost(0, audioSessionId).also {
                bassBoost = it
                bassBoostSessionId = audioSessionId
            }
        }

        val clampedStrength = strength.coerceIn(0, 1000).toShort()
        if (effect.strengthSupported) {
            effect.setStrength(clampedStrength)
        } else {
            effect.setStrength(if (clampedStrength > 0) 1000 else 0)
        }
        effect.enabled = true
        return true
    }

    private fun releaseBassBoost() {
        bassBoost?.runCatching {
            enabled = false
            release()
        }
        bassBoost = null
        bassBoostSessionId = null
    }

    private fun configureVolumeNormalization(audioSessionId: Int?, enabled: Boolean): Boolean {
        if (!enabled) {
            releaseDynamicsProcessing()
            return true
        }
        if (audioSessionId == null || audioSessionId <= 0) {
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false
        }

        try {
            val effect = if (dynamicsProcessingSessionId == audioSessionId && dynamicsProcessing != null) {
                dynamicsProcessing!!
            } else {
                releaseDynamicsProcessing()

                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, // channels
                    false, // preEq
                    0,
                    true, // mbc
                    1, // 1 band compressor
                    false, // postEq
                    0,
                    true // limiter
                ).build()

                DynamicsProcessing(0, audioSessionId, config).also {
                    dynamicsProcessing = it
                    dynamicsProcessingSessionId = audioSessionId
                }
            }

            val mbcBand = DynamicsProcessing.MbcBand(
                true, // enabled
                20000.0f, // cutoffFrequency
                50.0f, // attackTime
                300.0f, // releaseTime
                2.0f, // ratio: 4->2，压缩温和化，保留动态
                -18.0f, // threshold: -15 -> -18，略放宽，让更多内容不触发压缩
                6.0f, // kneeWidth: 2 -> 6，软拐点，过渡更平滑
                -60.0f, // noiseGateThreshold
                1.0f, // expanderRatio
                2.0f, // preGain
                4.0f // postGain: 0 -> 4，makeup gain 增益补偿，整体提亮（老歌明显受益）
            )

            val limiter = DynamicsProcessing.Limiter(
                true, // inUse
                true, // enabled
                0, // linkGroup
                1.0f, // attackTime
                100.0f, // releaseTime
                10.0f, // ratio
                -1.0f, // threshold
                0.0f // postGain
            )

            for (c in 0 until 2) {
                effect.setMbcBandByChannelIndex(c, 0, mbcBand)
                effect.setLimiterByChannelIndex(c, limiter)
            }

            effect.enabled = true
            return true
        } catch (e: Exception) {
            releaseDynamicsProcessing()
            return false
        }
    }

    private fun releaseDynamicsProcessing() {
        dynamicsProcessing?.runCatching {
            enabled = false
            release()
        }
        dynamicsProcessing = null
        dynamicsProcessingSessionId = null
    }

    private fun getAlbumArtBytes(albumId: Long): ByteArray? {
        val uri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
        return try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    private fun getEmbeddedLyrics(filePath: String): String? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            // METADATA_KEY_LYRICS = 26，API 29+ 才有常量名，直接用数字 key 兼容旧版本
            val lyrics = retriever.extractMetadata(26)
            retriever.release()
            lyrics
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        releaseEqualizer()
        releaseBassBoost()
        if (superLyricRegistered) {
            runCatching { SuperLyricHelper.unregisterPublisher() }
            superLyricRegistered = false
        }
        super.onDestroy()
    }
}
