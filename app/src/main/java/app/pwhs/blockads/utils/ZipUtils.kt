package app.pwhs.blockads.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Utility for downloading and extracting ZIP files safely.
 */
object ZipUtils {

    /**
     * Downloads a ZIP file from [downloadUrl] and extracts its contents into [destDir].
     *
     * - Streams directly from network into ZipInputStream (no intermediate file)
     * - Guards against zip-slip path traversal attacks
     * - On failure, cleans up all partially extracted files
     *
     * @param client Ktor HttpClient for downloading
     * @param downloadUrl URL of the ZIP file
     * @param destDir Directory to extract into (will be created if needed)
     * @return List of extracted [File]s on success
     * @throws ZipExtractionException on any failure
     */
    suspend fun downloadAndExtractZip(
        client: HttpClient,
        downloadUrl: String,
        destDir: File
    ): List<File> = withContext(Dispatchers.IO) {
        val extractedFiles = mutableListOf<File>()

        try {
            destDir.mkdirs()
            val canonicalDest = destDir.canonicalPath

            Timber.d("Downloading ZIP from: $downloadUrl")

            // Download to a temp file first to handle large ZIPs safely
            val tempZipFile = File(destDir, ".download.zip.tmp")
            try {
                val response = client.get(downloadUrl)
                val channel = response.bodyAsChannel()

                FileOutputStream(tempZipFile).use { fos ->
                    val buffer = ByteArray(8 * 1024)
                    while (!channel.isClosedForRead) {
                        val bytesRead = channel.readAvailable(buffer)
                        if (bytesRead > 0) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }

                Timber.d("ZIP downloaded: ${tempZipFile.length()} bytes")

                // Extract from the downloaded temp file
                tempZipFile.inputStream().use { fis ->
                    ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryFile = File(destDir, entry.name)

                            // Zip-slip protection
                            if (!entryFile.canonicalPath.startsWith(canonicalDest)) {
                                throw ZipExtractionException(
                                    "Zip-slip detected: ${entry.name}"
                                )
                            }

                            if (entry.isDirectory) {
                                entryFile.mkdirs()
                            } else {
                                entryFile.parentFile?.mkdirs()
                                BufferedOutputStream(FileOutputStream(entryFile)).use { bos ->
                                    val buf = ByteArray(4 * 1024)
                                    var len: Int
                                    while (zis.read(buf).also { len = it } > 0) {
                                        bos.write(buf, 0, len)
                                    }
                                }
                                extractedFiles.add(entryFile)
                                Timber.d("Extracted: ${entryFile.name} (${entryFile.length()} bytes)")
                            }

                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } finally {
                // Always delete temp zip
                tempZipFile.delete()
            }

            if (extractedFiles.isEmpty()) {
                throw ZipExtractionException("ZIP archive was empty")
            }

            Timber.d("Extraction complete: ${extractedFiles.size} files in ${destDir.name}")
            extractedFiles
        } catch (e: ZipExtractionException) {
            // Clean up on our custom exception
            cleanupDir(destDir)
            throw e
        } catch (e: Exception) {
            // Clean up on any unexpected error
            cleanupDir(destDir)
            Timber.e(e, "Failed to download/extract ZIP")
            throw ZipExtractionException("Extraction failed: ${e.message}", e)
        }
    }

    private fun cleanupDir(dir: File) {
        try {
            if (dir.exists()) {
                dir.deleteRecursively()
                Timber.d("Cleaned up failed extraction directory: ${dir.absolutePath}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to clean up directory: ${dir.absolutePath}")
        }
    }
}

/**
 * Exception thrown when ZIP extraction fails.
 */
class ZipExtractionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
