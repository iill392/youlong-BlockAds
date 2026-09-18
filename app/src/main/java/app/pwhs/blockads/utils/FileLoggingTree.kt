package app.pwhs.blockads.utils

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue

/**
 * Timber tree that mirrors logs into a rotating file **without ever touching
 * disk on the calling thread**.
 *
 * The previous implementation opened a `FileWriter` and allocated a fresh
 * `SimpleDateFormat` on *every single log call*. Since Timber is called from the
 * main thread all over the UI and from service callbacks, that produced a
 * continuous stream of synchronous disk I/O on the UI thread — a major cause of
 * jank and of slow app / protection startup.
 *
 * Lines are now formatted with a cached per-thread formatter and handed to a
 * single low-priority background writer through a bounded queue. If the queue
 * ever fills up, entries are dropped: logging must never slow the app down.
 */
class FileLoggingTree(context: Context) : Timber.DebugTree() {

    private val logDir = File(context.cacheDir, "logs").apply { mkdirs() }

    private val isDebug: Boolean = app.pwhs.blockads.BuildConfig.DEBUG

    private data class LogRecord(val line: String, val throwable: Throwable?)

    private val queue = ArrayBlockingQueue<LogRecord>(QUEUE_CAPACITY)
    private var dropped = 0

    @Volatile
    private var writer: BufferedWriter? = null
    private var writtenBytes = 0L
    private val pending = StringBuilder(MAX_BATCH_BYTES)

    private val writerThread = Thread({ drainQueue() }, "blockads-log-writer").apply {
        priority = Thread.MIN_PRIORITY
        isDaemon = true
        start()
    }

    private fun drainQueue() {
        while (true) {
            try {
                val first = queue.take()
                appendRecord(first)
                // Coalesce everything already queued into one write syscall.
                while (pending.length < MAX_BATCH_BYTES) {
                    val next = queue.poll() ?: break
                    appendRecord(next)
                }
                flushPending()
            } catch (_: InterruptedException) {
                return
            } catch (_: Throwable) {
                pending.setLength(0)
            }
        }
    }

    private fun appendRecord(record: LogRecord) {
        pending.append(record.line).append('\n')
        record.throwable?.let { pending.append(Log.getStackTraceString(it)).append('\n') }
    }

    private fun flushPending() {
        if (pending.isEmpty()) return
        try {
            val out = currentWriter()
            out.write(pending.toString())
            out.flush()
            writtenBytes += pending.length
        } catch (_: Throwable) {
            // Never let logging take the process down.
        } finally {
            pending.setLength(0)
        }
    }

    private fun currentWriter(): BufferedWriter {
        val existing = writer
        if (existing != null) {
            if (writtenBytes < MAX_FILE_BYTES) return existing
            rotate(existing)
        }
        return openFresh()
    }

    private fun rotate(old: BufferedWriter) {
        try { old.close() } catch (_: Throwable) { }
        writer = null
        try {
            val file = File(logDir, LOG_FILE_NAME)
            val backup = File(logDir, LOG_FILE_NAME_OLD)
            if (backup.exists()) backup.delete()
            file.renameTo(backup)
        } catch (_: Throwable) { }
        writtenBytes = 0L
    }

    private fun openFresh(): BufferedWriter {
        val file = File(logDir, LOG_FILE_NAME)
        writtenBytes = if (file.exists()) file.length() else 0L
        val out = BufferedWriter(FileWriter(file, true), 16 * 1024)
        writer = out
        return out
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (isDebug) {
            super.log(priority, tag, message, t)
        }

        val line = buildString {
            append(formatter.get()!!.format(Date()))
            append(' ').append(priorityLabel(priority))
            append("/[").append(tag ?: "BlockAds").append("] <")
            append(Thread.currentThread().name).append(">: ")
            append(message)
        }

        if (!queue.offer(LogRecord(line, t))) {
            dropped++
            if (dropped % 1000 == 1) {
                Log.w(TAG, "Local log queue full, dropped $dropped entries")
            }
        }
    }

    /** Flush whatever the writer thread has not persisted yet. */
    fun flush() {
        try { writer?.flush() } catch (_: Throwable) { }
    }

    private fun priorityLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "WTF"
        else -> "?"
    }

    companion object {
        private const val TAG = "FileLoggingTree"
        private const val LOG_FILE_NAME = "blockads_logs.txt"
        private const val LOG_FILE_NAME_OLD = "blockads_logs_old.txt"
        private const val MAX_FILE_BYTES = 2L * 1024 * 1024
        private const val QUEUE_CAPACITY = 8192
        private const val MAX_BATCH_BYTES = 64 * 1024

        private val formatter = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        }
    }
}
