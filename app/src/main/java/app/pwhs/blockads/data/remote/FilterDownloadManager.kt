package app.pwhs.blockads.data.remote

import android.content.Context
import app.pwhs.blockads.data.entities.FilterList
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Hard ceiling for one file fetch. The Ktor socket timeout normally covers
 * this, but a stuck DNS lookup or a half-open connection could otherwise keep a
 * background sync (and the filter load lock it holds) alive for minutes.
 *
 * Sized for the slow path: the compiled rule binaries are 1-2 MB each and
 * raw.githubusercontent.com can serve them at ~24 KB/s, so one file legitimately
 * needs 60-90 s. The previous 30 s cap made every download of the two default
 * rule sets fail, which left the Go engine with no trie/bloom at all and pinned
 * the block rate at 0%.
 */
private const val FILE_DOWNLOAD_TIMEOUT_MS = 180_000L

/**
 * Rewrites GitHub-hosted filter asset URLs to a CDN mirror, falling back to the
 * original URL.
 *
 * raw.githubusercontent.com is frequently throttled from many networks —
 * measured at ~24-39 KB/s versus ~256 KB/s through jsDelivr for the very same
 * 1.4 MB `.trie` file. The engine cannot block anything until those binaries are
 * on disk, so the mirror is tried first while the original URL stays as a
 * fallback: a jsDelivr outage then degrades the download instead of killing
 * filtering outright.
 */
internal object FilterSourceMirror {

    /** `https://raw.githubusercontent.com/{owner}/{repo}[/refs/heads]/{branch}/{path}` */
    private val RAW_HOST = Regex(
        "^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/(?:refs/heads/)?([^/]+)/(.+)$"
    )

    /** `https://github.com/{owner}/{repo}/raw[/refs/heads]/{branch}/{path}` */
    private val GITHUB_HOST = Regex(
        "^https://github\\.com/([^/]+)/([^/]+)/raw/(?:refs/heads/)?([^/]+)/(.+)$"
    )

    private const val JSDELIVR = "https://cdn.jsdelivr.net/gh"

    /**
     * jsDelivr serves the same GitHub content from several independent
     * endpoints, and they fail independently. On a network where
     * raw.githubusercontent.com is blackholed the CDN is the difference between
     * having rules and having none, so every endpoint is tried before giving up
     * on the original URL. The bundled baseline in
     * [app.pwhs.blockads.data.repository.BuiltinRuleSource] covers the case where
     * all of them are unreachable.
     */
    private val JSDELIVR_ENDPOINTS = listOf(
        JSDELIVR,
        "https://fastly.jsdelivr.net/gh",
        "https://gcore.jsdelivr.net/gh",
        "https://testingcf.jsdelivr.net/gh",
    )

    /**
     * @return the URLs to try, in order. Non-GitHub URLs and URLs that cannot be
     *   mapped are returned unchanged, so callers can iterate unconditionally.
     */
    fun candidates(url: String): List<String> {
        val match = RAW_HOST.find(url) ?: GITHUB_HOST.find(url) ?: return listOf(url)
        val (owner, repo, branch, path) = match.destructured
        val suffix = "/$owner/$repo@$branch/$path"
        return (JSDELIVR_ENDPOINTS.map { "$it$suffix" } + url).distinct()
    }
}

data class DownloadedFilterPaths(
    val bloomPath: String?,
    val triePath: String?,
    val cssPath: String?,
    val scriptletPath: String?
)

class FilterDownloadManager(
    private val context: Context,
    private val client: HttpClient
) {
    private val filterDir = File(context.filesDir, "remote_filters").apply { 
        if (!exists()) mkdirs() 
    }

    /**
     * Downloads the required filter files (.bloom, .trie, and optional .css).
     * @param filter The FilterList to download.
     * @param forceUpdate Forces re-download even if the file exists locally.
     * @param allowDownload When false only files already on disk are resolved and
     *   no network request is issued at all. Used by the service startup path so
     *   that turning the protection on can never be delayed by a slow/unreachable
     *   filter CDN. Missing binaries are fetched later by the background sync.
     * @return Result containing the local paths to the downloaded files.
     */
    suspend fun downloadFilterList(
        filter: FilterList,
        forceUpdate: Boolean = false,
        allowDownload: Boolean = true
    ): Result<DownloadedFilterPaths> = withContext(Dispatchers.IO) {
        try {
            val bloomFile = File(filterDir, "${filter.id}.bloom")
            val trieFile = File(filterDir, "${filter.id}.trie")
            val cssFile = File(filterDir, "${filter.id}.css")
            val scriptletFile = File(filterDir, "${filter.id}.scriptlets")

            val bloomPath = if (filter.bloomUrl.isNotEmpty()) downloadFile(filter.bloomUrl, bloomFile, forceUpdate, allowDownload) else null
            val triePath = if (filter.trieUrl.isNotEmpty()) downloadFile(filter.trieUrl, trieFile, forceUpdate, allowDownload) else null

            var cssPath: String? = null
            if (filter.cssUrl.isNotEmpty()) {
                cssPath = downloadFile(filter.cssUrl, cssFile, forceUpdate, allowDownload)
            }

            var scriptletPath: String? = null
            if (filter.scriptletsUrl.isNotEmpty()) {
                scriptletPath = downloadFile(filter.scriptletsUrl, scriptletFile, forceUpdate, allowDownload)
            }

            if (bloomPath != null && triePath != null) {
                Result.success(DownloadedFilterPaths(bloomPath, triePath, cssPath, scriptletPath))
            } else {
                Result.failure(Exception("Failed to download core filter files (.bloom or .trie) for ${filter.id}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error downloading filter list ${filter.id}")
            Result.failure(e)
        }
    }

    /**
     * Downloads a single file from the given URL and saves it to [destFile].
     * Uses a temporary file during download to prevent partial corruption.
     */
    private suspend fun downloadFile(
        url: String,
        destFile: File,
        forceUpdate: Boolean,
        allowDownload: Boolean = true
    ): String? {
        // Custom filters use "local://" sentinel URLs — files are already on disk
        if (url.startsWith("local://")) {
            return if (destFile.exists() && destFile.length() > 0) destFile.absolutePath else null
        }

        if (!forceUpdate && destFile.exists() && destFile.length() > 0) {
            Timber.d("File already exists: ${destFile.name}")
            return destFile.absolutePath
        }

        // Offline startup: resolve from cache only, never touch the network.
        if (!allowDownload) {
            Timber.d("Offline mode, skipping download of ${destFile.name}")
            return null
        }

        val sources = FilterSourceMirror.candidates(url)
        for ((index, source) in sources.withIndex()) {
            val downloaded = withTimeoutOrNull(FILE_DOWNLOAD_TIMEOUT_MS) {
                fetchTo(source, destFile)
            }
            if (downloaded != null) {
                if (index > 0) {
                    Timber.d("Recovered ${destFile.name} from fallback source $source")
                }
                return downloaded
            }
            Timber.w("Source ${index + 1}/${sources.size} failed for ${destFile.name}: $source")
        }

        Timber.e("Failed to download ${destFile.name} from any of ${sources.size} source(s)")
        return null
    }

    /**
     * Single-source streaming download into [destFile] via a `.tmp` sibling, so a
     * truncated transfer can never be mistaken for a usable rule binary.
     *
     * @return the absolute path on success, null on any failure.
     */
    private suspend fun fetchTo(url: String, destFile: File): String? {
        val tempFile = File(destFile.parent, "${destFile.name}.tmp")
        return try {
            Timber.d("Downloading from $url to ${destFile.name}")
            val channel = client.get(url).bodyAsChannel()

            withContext(Dispatchers.IO) {
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val bytesRead = channel.readAvailable(buffer)
                        if (bytesRead < 0) break
                        if (bytesRead > 0) output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            if (tempFile.renameTo(destFile)) {
                Timber.d("Successfully downloaded to ${destFile.absolutePath}")
                destFile.absolutePath
            } else {
                Timber.e("Failed to rename temp file to ${destFile.name}")
                tempFile.delete()
                null
            }
        } catch (e: CancellationException) {
            // Timed out or cancelled — drop the partial file so the fallback
            // source (or the next attempt) starts from a clean slate.
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to download $url")
            tempFile.delete()
            null
        }
    }

    /**
     * Reads a downloaded CSS file containing raw selectors, appends { display: none !important; }
     * and returns a single valid CSS string ready for injection.
     */
    fun getInjectableCss(file: File): String {
        if (!file.exists() || file.length() == 0L) {
            return ""
        }

        val cssBuilder = StringBuilder()
        try {
            file.forEachLine { line ->
                val selector = line.trim()
                if (selector.isNotEmpty()) {
                    cssBuilder.append(selector).append(" { display: none !important; }\n")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading CSS file ${file.absolutePath}")
            return ""
        }
        return cssBuilder.toString()
    }
}
