package app.pwhs.blockads.data

import app.pwhs.blockads.data.dao.DnsErrorDao
import app.pwhs.blockads.data.dao.DnsLogDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Keeps the DNS log tables bounded.
 *
 * The `dns_logs` table previously had no retention at all — it was only ever
 * cleared wholesale by an explicit user action. On a busy device it therefore
 * grew into the hundreds of thousands (or millions) of rows, and every one of
 * the seven aggregate queries the Home screen subscribes to had to scan it.
 * Because those queries share the Room executor with the VPN service, an
 * oversized table also delayed start-up, which is what users reported as
 * "sometimes it turns on instantly, sometimes it takes forever".
 *
 * Pruning is cheap and idempotent, so it is safe to run on every app start.
 */
object LogRetention {

    /** How much history the statistics screens need. */
    val RETENTION_DAYS = 7L

    /** Absolute ceiling so a single noisy day can never bloat the database. */
    const val MAX_DNS_LOG_ROWS = 50_000

    suspend fun prune(
        dnsLogDao: DnsLogDao,
        dnsErrorDao: DnsErrorDao? = null,
        now: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val cutoff = now - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        try {
            val removedByAge = dnsLogDao.deleteOlderThan(cutoff)
            val removedBySize = dnsLogDao.trimToMostRecent(MAX_DNS_LOG_ROWS)
            dnsErrorDao?.deleteOlderThan(cutoff)

            if (removedByAge > 0 || removedBySize > 0) {
                Timber.d(
                    "Log retention: removed $removedByAge expired and " +
                        "$removedBySize over-limit dns_logs rows"
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Log retention failed")
        }
    }
}
