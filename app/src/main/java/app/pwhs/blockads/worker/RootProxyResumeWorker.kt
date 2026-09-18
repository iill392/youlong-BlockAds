package app.pwhs.blockads.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.pwhs.blockads.service.RootProxyService
import timber.log.Timber

class RootProxyResumeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "root_proxy_resume_work"
    }

    override suspend fun doWork(): Result {
        return try {
            val intent = Intent(applicationContext, RootProxyService::class.java).apply {
                action = RootProxyService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to resume Root Proxy")
            Result.retry()
        }
    }
}
