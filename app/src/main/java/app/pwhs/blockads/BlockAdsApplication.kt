package app.pwhs.blockads

import android.app.Application
import app.pwhs.blockads.data.LogRetention
import app.pwhs.blockads.data.dao.DnsErrorDao
import app.pwhs.blockads.data.dao.DnsLogDao
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.di.appModule
import app.pwhs.blockads.worker.DailySummaryScheduler
import app.pwhs.blockads.worker.FilterUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber
import timber.log.Timber.DebugTree
import app.pwhs.blockads.utils.CrashReportingManager
import app.pwhs.blockads.utils.FileLoggingTree


class BlockAdsApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@BlockAdsApplication)
            modules(appModule)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
        
        // Plant File logging tree for all builds to allow log export
        Timber.plant(FileLoggingTree(this))

        // Schedule auto-update for filter lists after Koin is initialized
        val appPreferences: AppPreferences by inject()
        applicationScope.launch {
            // Restore Crash Reporting state dynamically
            val isCrashReportingEnabled = appPreferences.crashReportingEnabled.first()
            CrashReportingManager.toggleSentry(this@BlockAdsApplication, isCrashReportingEnabled)

            // Move v6.3.0 single-config users onto the multi-profile schema.
            appPreferences.migrateLegacyWgConfigIfNeeded()

            // The Root Proxy toggle was removed from Settings, so a leftover
            // "root" routing mode would strand the user with no way back.
            if (appPreferences.getRoutingModeSnapshot() == AppPreferences.ROUTING_MODE_ROOT) {
                appPreferences.setRoutingMode(AppPreferences.ROUTING_MODE_DIRECT)
            }

            FilterUpdateScheduler.scheduleFilterUpdate(this@BlockAdsApplication, appPreferences)

            // Schedule daily summary only if enabled
            if (appPreferences.dailySummaryEnabled.first()) {
                DailySummaryScheduler.scheduleDailySummary(this@BlockAdsApplication)
            }
        }

        // Trusted Wi-Fi networks (#197): auto-pause/resume on SSID change.
        app.pwhs.blockads.service.TrustedNetworkManager(this, appPreferences).start()

        // Keep the DNS log tables bounded. Deferred a little so the Home screen's
        // own queries get the database first — an oversized table used to make
        // those (and the VPN start-up sharing the Room executor) slow.
        val dnsLogDao: DnsLogDao by inject()
        val dnsErrorDao: DnsErrorDao by inject()
        applicationScope.launch {
            delay(LOG_PRUNE_DELAY_MS)
            LogRetention.prune(dnsLogDao, dnsErrorDao)
        }
    }

    private companion object {
        /** Grace period before the background log cleanup runs. */
        const val LOG_PRUNE_DELAY_MS = 3_000L
    }
}
