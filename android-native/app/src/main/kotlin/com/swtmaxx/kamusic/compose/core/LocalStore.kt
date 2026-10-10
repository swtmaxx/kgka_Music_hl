package com.swtmaxx.kamusic.compose.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swtmaxx.kamusic.compose.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * 一条已下载记录。
 *
 * 只存「重建 [Song] 所需的最小字段 + 本地路径」，不存整个 Song ——
 * Song 不是 @Serializable（它要兼容服务端五花八门的字段名，解析靠手写）。
 */
@Serializable
data class DownloadedSong(
    val id: String,
    val hash: String = "",
    val title: String,
    val artist: String,
    val albumId: String? = null,
    val albumAudioId: String? = null,
    val albumName: String? = null,
    val durationMs: Long? = null,
    val coverUrl: String? = null,
    /** 音频文件绝对路径。 */
    val filePath: String,
    /** 封面文件绝对路径；封面下载失败时为 null。 */
    val coverPath: String? = null,
    val sizeBytes: Long = 0L,
    val quality: String = "128",
    val downloadedAt: Long = 0L,
) {
    /** 还原成可播放的 [Song]（带 localPath，播放时不再联网）。 */
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        hash = hash,
        albumId = albumId,
        albumAudioId = albumAudioId,
        albumName = albumName,
        coverUrl = coverUrl,
        durationMs = durationMs,
        localPath = filePath,
        localCoverPath = coverPath,
    )
}

private val Context.downloadsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "ka_music_downloads")

/**
 * 下载记录的持久化。
 *
 * 刻意不引 Room：整个记录是一份小列表（每首 ~200 字节），
 * 用 DataStore 的**单个 JSON 键**存更简单，也不会出现「多键各写一半」的撕裂态。
 *
 * 写操作全部走 [Mutex] 串行，避免并发下载完成时互相覆盖。
 */
class LocalStore(private val context: Context) {

    private object Keys {
        val downloads = stringPreferencesKey("downloads")
    }

    private val mutex = Mutex()

    private val _downloads = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val downloads: StateFlow<List<DownloadedSong>> = _downloads.asStateFlow()

    /** 从 DataStore 恢复。应用启动时调用一次。 */
    suspend fun hydrate() {
        val raw = runCatching {
            context.downloadsDataStore.data.first()[Keys.downloads]
        }.getOrNull()
        _downloads.value = decode(raw)
    }

    suspend fun add(record: DownloadedSong) = mutex.withLock {
        // 同 id 覆盖（重下同一首时不产生重复记录）
        persist(_downloads.value.filterNot { it.id == record.id } + record)
    }

    suspend fun remove(id: String) = mutex.withLock {
        persist(_downloads.value.filterNot { it.id == id })
    }

    suspend fun clearAll() = mutex.withLock { persist(emptyList()) }

    fun findById(id: String): DownloadedSong? = _downloads.value.firstOrNull { it.id == id }

    fun findByHash(hash: String): DownloadedSong? =
        _downloads.value.firstOrNull { it.hash.isNotEmpty() && it.hash == hash }

    fun totalBytes(): Long = _downloads.value.sumOf { it.sizeBytes }

    private suspend fun persist(list: List<DownloadedSong>) {
        _downloads.value = list
        runCatching {
            context.downloadsDataStore.edit { prefs ->
                prefs[Keys.downloads] = KaJson.encodeToString(LIST_SERIALIZER, list)
            }
        }
    }

    private fun decode(raw: String?): List<DownloadedSong> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { KaJson.decodeFromString(LIST_SERIALIZER, raw) }
            .getOrDefault(emptyList())
    }

    private companion object {
        val LIST_SERIALIZER = ListSerializer(DownloadedSong.serializer())
    }
}
