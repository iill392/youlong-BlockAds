package app.pwhs.blockads.utils

import android.content.Context
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import timber.log.Timber
import java.io.File

object CrashReportingManager {

    fun toggleSentry(context: Context, isEnabled: Boolean) {
        if (isEnabled) {
            enableSentry(context)
        } else {
            disableSentry(context)
        }
    }

    private fun enableSentry(context: Context) {
        if (!Sentry.isEnabled()) {
            SentryAndroid.init(context) { options ->
                options.isEnableAutoSessionTracking = true
            }
            Timber.i("Sentry has been ENABLED by user opt-in.")
        }
    }

    private fun disableSentry(context: Context) {
        if (Sentry.isEnabled()) {
            Sentry.close()
            Timber.i("Sentry has been DISABLED by user.")

            // Clean up cached data
            try {
                val sentryCacheDir = File(context.cacheDir, "sentry")
                if (sentryCacheDir.exists()) {
                    sentryCacheDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear Sentry cache")
            }
        }
    }
}
