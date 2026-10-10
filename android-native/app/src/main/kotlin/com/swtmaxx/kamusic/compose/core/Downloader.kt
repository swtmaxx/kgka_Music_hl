package com.swtmaxx.kamusic.compose.core

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.swtmaxx.kamusic.compose.data.model.Song
import com.swtmaxx.kamusic.compose.data.repo.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** 单首下载的状态。 */
sealed interface DownloadState {
    data object Queued : DownloadState
    data class Running(val bytes: Long, val total: Long, val resuming: Boolean) : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * 续传决策（纯函数，便于单测）。
 *
 * - 已有字节 > 0 且服务器回 **206 Partial Content** → 追加写入
 * - 其余（**200** 表示不支持 Range 或 etag 已变、**416** 表示区间无效）→ 丢弃重下
 */
internal enum class ResumeAction { Append, Restart }

internal fun decideResume(existingBytes: Long, responseCode: Int): ResumeAction =
    if (existingBytes > 0L && responseCode == 206) ResumeAction.Append else ResumeAction.Restart

/** 下载进度里记录的续传元信息，与 `.tmp` 同目录。 */
@Serializable
private data class PartMeta(
    val sourceUrl: String,
    val etag: String? = null,
    val bytes: Long = 0L,
)

/**
 * 下载器。
 *
 * 设计取舍（针对 240×284 / 1GB RAM / API 27 的手表）：
 * - **并发 1**：手表网络与 IO 都弱，多路并发只会互相拖慢，也更容易被系统杀
 * - **失败重试 1 次**：再多就变成耗电
 * - **断点续传带降级**：`Range` + `If-Range` 一次请求即可判定服务器是否支持，
 *   不支持（200）或区间失效（416）就自动整首重下
 * - **原子落盘**：先写 `.tmp`，`fd.sync()` 后 `renameTo`，避免半截文件被当成完整缓存
 * - **UA 与播放一致**：已证实 `Util.getUserAgent(ctx, "KaMusic")` 能从同一 CDN 取流
 */
class Downloader(
    private val context: Context,
    private val http: OkHttpClient,
    private val repo: MusicRepository,
    private val store: LocalStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val queue = ArrayDeque<Pair<Song, String>>()
    private val queuedIds = mutableSetOf<String>()
    private val cancelledIds = mutableSetOf<String>()
    private val workerActive = AtomicBoolean(false)
    private val lock = Mutex()

    /** 一旦发现空间不足就置位，worker 会停掉整个队列（而不是让剩下几百首逐个失败）。 */
    @Volatile
    private var spaceExhausted = false

    /** 下载目录。 */
    val downloadDir: File get() = resolveDownloadDir(context)

    private val coverDir: File get() = File(downloadDir, COVER_DIR_NAME)

    /** 目标目录所在分区的可用字节。 */
    fun freeBytes(): Long = runCatching {
        StatFs(downloadDir.absolutePath).availableBytes
    }.getOrDefault(0L)

    /** 已下载歌曲占用的字节（按记录汇总，不扫描磁盘）。 */
    fun usedBytes(): Long = store.totalBytes()

    fun stateOf(songId: String): DownloadState? = _states.value[songId]

    fun isDownloaded(songId: String): Boolean = store.findById(songId) != null

    /**
     * 入队。已在库中或已在队列里的会被跳过。
     *
     * 返回**实际入队**的首数，供「下载全部」判断是否需要提示。
     */
    suspend fun enqueue(songs: List<Song>, quality: String): Int = lock.withLock {
        var added = 0
        // 新一批任务进来时重新给一次机会（用户可能刚清理了空间）
        spaceExhausted = false
        songs.forEach { song ->
            if (song.id.isEmpty()) return@forEach
            if (store.findById(song.id) != null) return@forEach
            if (song.hash.isEmpty()) return@forEach
            if (queuedIds.contains(song.id)) return@forEach
            queuedIds.add(song.id)
            cancelledIds.remove(song.id)
            queue.addLast(song to quality)
            setState(song.id, DownloadState.Queued)
            added++
        }
        if (added > 0) startWorker()
        added
    }

    fun cancel(songId: String) {
        scope.launch {
            lock.withLock {
                cancelledIds.add(songId)
                queue.removeAll { it.first.id == songId }
                queuedIds.remove(songId)
                _states.update { it - songId }
            }
        }
    }

    /** 清掉已完成/失败的状态（只影响 UI，不动文件）。 */
    fun clearFinished() {
        _states.update { map ->
            map.filterValues { it is DownloadState.Running || it is DownloadState.Queued }
        }
    }

    // ===== 内部 =====

    private fun setState(id: String, state: DownloadState?) {
        _states.update { map -> if (state == null) map - id else map + (id to state) }
    }

    /**
     * 单 worker 串行消费。
     *
     * `AtomicBoolean` 保证同一时刻只有一个 worker；`finally` 里再查一次队列，
     * 兜住「worker 退出瞬间又有新任务入队」的竞态。
     */
    private fun startWorker() {
        if (!workerActive.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    // 空间不足：排空队列，剩余全部标为失败，不再逐首尝试
                    if (spaceExhausted) {
                        val rest = lock.withLock {
                            val pending = queue.toList()
                            queue.clear()
                            queuedIds.clear()
                            pending
                        }
                        rest.forEach { (song, _) ->
                            setState(song.id, DownloadState.Failed("存储空间不足，已停止"))
                        }
                        break
                    }

                    val next = lock.withLock { queue.removeFirstOrNull() } ?: break
                    val (song, quality) = next
                    runCatching { downloadOne(song, quality) }.onFailure { error ->
                        setState(song.id, DownloadState.Failed(error.message ?: "下载失败"))
                    }
                    lock.withLock { queuedIds.remove(song.id) }
                }
            } finally {
                workerActive.set(false)
                val more = lock.withLock { queue.isNotEmpty() }
                if (more && !spaceExhausted) startWorker()
            }
        }
    }

    private suspend fun downloadOne(song: Song, quality: String): Boolean {
        val id = song.id

        if (lock.withLock { cancelledIds.remove(id) }) {
            setState(id, null)
            return false
        }
        if (store.findById(id) != null) {
            setState(id, DownloadState.Done)
            return true
        }

        // ---- 空间检查（用户选择「不限条数，只看剩余空间」）----
        val free = freeBytes()
        if (free < MIN_FREE_BYTES) {
            spaceExhausted = true
            setState(id, DownloadState.Failed("存储空间不足（可用 ${formatBytes(free)}）"))
            return false
        }
        val estimated = estimateBytes(song.durationMs, quality)
        if (free < (estimated * SAFETY_FACTOR).toLong()) {
            spaceExhausted = true
            setState(id, DownloadState.Failed("存储空间不足（需约 ${formatBytes(estimated)}）"))
            return false
        }

        // ---- 解析下载地址 ----
        val url = repo.resolvePlayUrl(song)
        if (url.isNullOrEmpty()) {
            setState(id, DownloadState.Failed("无法获取下载地址"))
            return false
        }

        downloadDir.mkdirs()
        val ext = if (quality == FLAC) "flac" else "mp3"
        val target = resolveTargetFile(song, ext)
        val tmp = File(target.parentFile, target.name + TMP_SUFFIX)
        val metaFile = File(target.parentFile, target.name + META_SUFFIX)

        // ---- 续传：只有 sourceUrl 一致时才复用 .tmp ----
        val savedMeta = readMeta(metaFile)?.takeIf { it.sourceUrl == url }
        var existing = if (savedMeta != null && tmp.exists()) tmp.length() else 0L
        if (existing > 0L && savedMeta?.bytes != existing) existing = 0L

        var attempt = 0
        while (true) {
            val result = runCatching { transfer(url, tmp, metaFile, existing, id, estimated) }
            val error = result.exceptionOrNull()
            if (error == null) break
            if (attempt >= MAX_RETRIES) {
                setState(id, DownloadState.Failed(error.message ?: "下载失败"))
                return false
            }
            attempt++
            existing = if (tmp.exists()) tmp.length() else 0L
        }

        // ---- 原子落盘 ----
        val ok = runCatching {
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("无法重命名临时文件")
        }.isSuccess
        if (!ok) {
            setState(id, DownloadState.Failed("保存失败"))
            return false
        }
        metaFile.delete()

        // ---- 封面（失败不影响音频）----
        val coverPath = downloadCover(song)

        store.add(
            DownloadedSong(
                id = id,
                hash = song.hash,
                title = song.title,
                artist = song.artist,
                albumId = song.albumId,
                albumAudioId = song.albumAudioId,
                albumName = song.albumName,
                durationMs = song.durationMs,
                coverUrl = song.coverUrl,
                filePath = target.absolutePath,
                coverPath = coverPath,
                sizeBytes = target.length(),
                quality = quality,
                downloadedAt = System.currentTimeMillis(),
            ),
        )
        setState(id, DownloadState.Done)
        return true
    }

    /** 一次传输（含续传判定）。成功返回，失败抛异常。 */
    private fun transfer(
        url: String,
        tmp: File,
        metaFile: File,
        existingBytes: Long,
        songId: String,
        estimated: Long,
    ) {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        if (existingBytes > 0L) {
            builder.header("Range", "bytes=$existingBytes-")
            readMeta(metaFile)?.etag?.let { builder.header("If-Range", it) }
        }

        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            val action = decideResume(existingBytes, response.code)
            val append = action == ResumeAction.Append
            val startBytes = if (append) existingBytes else 0L

            val body = response.body ?: throw IOException("响应为空")
            val contentLength = body.contentLength()
            val total = if (contentLength > 0L) startBytes + contentLength else estimated

            val etag = response.header("ETag")
            writeMeta(metaFile, PartMeta(sourceUrl = url, etag = etag, bytes = startBytes))

            FileOutputStream(tmp, append).use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                var written = startBytes
                var lastReport = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            writeMeta(metaFile, PartMeta(url, etag, written))
                            setState(songId, DownloadState.Running(written, total, append))
                        }
                    }
                }
                out.flush()
                // 落盘到物理介质，避免断电/被杀后拿到半截数据
                out.fd.sync()
                writeMeta(metaFile, PartMeta(url, etag, written))
                setState(songId, DownloadState.Running(written, total, append))
            }
        }
    }

    private fun downloadCover(song: Song): String? {
        val url = song.coverUrl?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            coverDir.mkdirs()
            val file = File(coverDir, "${safeName(song.id)}.jpg")
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                body.byteStream().use { input ->
                    FileOutputStream(file).use { out -> input.copyTo(out) }
                }
            }
            file.takeIf { it.length() > 0L }?.absolutePath
        }.getOrNull()
    }

    /**
     * 目标文件名。用最终名（不含 `.tmp`）判重，所以**续传时名字保持稳定**。
     */
    private fun resolveTargetFile(song: Song, ext: String): File {
        val base = safeName("${song.artist} - ${song.title}")
        var candidate = File(downloadDir, "$base.$ext")
        var index = 2
        while (candidate.exists()) {
            candidate = File(downloadDir, "$base ($index).$ext")
            index++
        }
        return candidate
    }

    private fun readMeta(file: File): PartMeta? {
        if (!file.exists()) return null
        return runCatching { KaJson.decodeFromString(PartMeta.serializer(), file.readText()) }
            .getOrNull()
    }

    private fun writeMeta(file: File, meta: PartMeta) {
        runCatching {
            file.writeText(KaJson.encodeToString(PartMeta.serializer(), meta))
        }
    }

    internal companion object {
        const val DIR_NAME = "KA Music"
        const val COVER_DIR_NAME = ".cover"
        const val TMP_SUFFIX = ".tmp"
        const val META_SUFFIX = ".tmp.meta"

        /** 低于这个可用空间就拒绝任何下载，避免把表塞满导致系统异常。 */
        const val MIN_FREE_BYTES = 200L * 1024 * 1024

        /** 预估大小的安全系数（1.2 = 留 20% 余量）。 */
        const val SAFETY_FACTOR = 1.2

        const val MAX_RETRIES = 1
        const val FLAC = "flac"
        const val USER_AGENT = "KaMusic/1.0"
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 200L

        /** 各档音质的估算码率（kbps）。 */
        fun kbpsFor(quality: String): Double = when (quality) {
            FLAC -> 900.0
            "320" -> 320.0
            else -> 128.0
        }

        /** 预估下载大小；`durationMs` 缺失时按 4 分钟估。 */
        fun estimateBytes(durationMs: Long?, quality: String): Long {
            val seconds = (durationMs ?: 240_000L) / 1000.0
            return (seconds * kbpsFor(quality) * 1000.0 / 8.0).toLong()
        }

        /** 把任意字符串变成安全的文件名片段。 */
        fun safeName(raw: String): String {
            val cleaned = raw
                .map { if (it in ILLEGAL_CHARS || it.code < 0x20) '_' else it }
                .joinToString("")
                .trim()
                .trimEnd('.')
            val collapsed = cleaned.replace(Regex("\\s+"), " ").ifEmpty { "未命名" }
            return if (collapsed.length > MAX_NAME_LENGTH) {
                collapsed.take(MAX_NAME_LENGTH)
            } else {
                collapsed
            }
        }

        private const val MAX_NAME_LENGTH = 80
        private val ILLEGAL_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

        /**
         * 解析下载目录。
         *
         * 首选公共目录 `/storage/emulated/0/Download/KA Music/`（用户可见、可用电脑传歌），
         * 但 **API 29+ 的分区存储会拒绝用 File API 直接写公共目录**（我们的 targetSdk 是 37，
         * 所以在新表上会命中）。此时回退到应用外部私有目录：
         * `Android/data/<pkg>/files/KA Music/` —— 不需要任何权限，卸载即删。
         *
         * 目标机 S100 是 API 27，走的是公共目录分支。
         */
        @Suppress("DEPRECATION")
        fun resolveDownloadDir(context: Context): File {
            val fallback = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_NAME)
            if (android.os.Build.VERSION.SDK_INT >= 29) return fallback
            val public = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                DIR_NAME,
            )
            return if (public.exists() || public.mkdirs()) public else fallback
        }

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / 1024.0 / 1024 / 1024)
            bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / 1024.0 / 1024)
            bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
