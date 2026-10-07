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
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.ryanheise.audioservice.AudioServiceActivity

class MainActivity : AudioServiceActivity() {
    private var pendingPermissionResult: MethodChannel.Result? = null

    companion object {
        private const val REQUEST_READ_AUDIO = 1001
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
        super.onDestroy()
    }
}
