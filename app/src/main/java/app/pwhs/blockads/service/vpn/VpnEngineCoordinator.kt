package app.pwhs.blockads.service.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.ParcelFileDescriptor
import app.pwhs.blockads.data.dao.FirewallRuleDao
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.entities.DnsProtocol
import app.pwhs.blockads.data.repository.FilterListRepository
import app.pwhs.blockads.service.FirewallManager
import app.pwhs.blockads.service.GoTunnelAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Hard ceiling for the (network-free) part of start-up. If reading the cached
 * filter index takes longer than this the tunnel is brought up with whatever
 * rules are already in memory rather than leaving the UI stuck on
 * "connecting…" — the background sync refreshes them moments later.
 */
private const val STARTUP_FILTER_TIMEOUT_MS = 5_000L

data class StartupConfig(
    val upstreamDns: String,
    val fallbackDns: String,
    val dnsResponseType: String,
    val dnsProtocol: DnsProtocol,
    val dohUrl: String,
    val whitelistedApps: Set<String>,
    val safeSearchEnabled: Boolean,
    val youtubeRestrictedMode: Boolean,
    val firewallEnabled: Boolean,
    val dnsProviderId: String?,
    val firewallManager: FirewallManager?
)

class VpnEngineCoordinator(
    private val context: Context,
    private val appPrefs: AppPreferences,
    private val filterRepo: FilterListRepository,
    private val firewallRuleDao: FirewallRuleDao
) {

    /**
     * @param recoveryScope scope that may run the last-resort rule recovery in
     *   the background. The recovery is network-bound and may take minutes, so
     *   it must never be awaited here — tunnel establishment has to stay fast.
     *   Callers that own a service scope should always pass one; when null the
     *   recovery is skipped and rule download is left to the post-start-up sync.
     */
    suspend fun prepareStartupConfig(recoveryScope: CoroutineScope? = null): StartupConfig {
        filterRepo.loadWhitelist()
        filterRepo.loadCustomRules()
        // The startup path is intentionally network-free so that turning the
        // protection on always completes in well under a second. Previously this
        // called seedDefaultsIfNeeded() + fetchAndSyncRemoteFilterLists() and
        // then loadAllEnabledFilters(), each of which hit the filter CDN — the
        // manifest URL (raw.githubusercontent.com) is unreachable on many
        // networks, so "turn on" could block for minutes or hours.
        // Everything network-bound now runs via backgroundSyncMissingFilters()
        // once the tunnel is up (see AdBlockVpnService).
        val filterResult = withTimeoutOrNull(STARTUP_FILTER_TIMEOUT_MS) {
            filterRepo.loadAllEnabledFilters(offlineOnly = true)
        }
        if (filterResult == null) {
            Timber.w("Filter load exceeded ${STARTUP_FILTER_TIMEOUT_MS}ms — starting with cached rules")
        } else {
            Timber.d("Filters loaded (offline): ${filterResult.getOrDefault(0)} domains")
        }

        // No usable rules means protection would be "on" but block nothing.
        // ensureUsableRules() is self-rate-limited (once per 15 min) and now that
        // the filter CDN is reached through a fast mirror it can genuinely finish
        // a download — which also means it can take minutes. It therefore runs
        // detached: awaiting it here would hold the whole "connecting…" phase
        // hostage to the filter CDN, and the post-start-up sync plus the rule
        // watcher apply the rules as soon as they land anyway.
        if (!filterRepo.hasUsableRules()) {
            if (recoveryScope != null) {
                recoveryScope.launch { filterRepo.ensureUsableRules() }
            } else {
                Timber.w(
                    "No usable filter rules and no recovery scope — rule download " +
                        "deferred to the post-start-up background sync"
                )
            }
        }

        return coroutineScope {
            val d1 = async { appPrefs.upstreamDns.first() }
            val d2 = async { appPrefs.fallbackDns.first() }
            val d3 = async { appPrefs.dnsResponseType.first() }
            val d4 = async { appPrefs.dnsProtocol.first() }
            val d5 = async { appPrefs.dohUrl.first() }
            val d6 = async { appPrefs.getWhitelistedAppsSnapshot() }
            val d7 = async { appPrefs.safeSearchEnabled.first() }
            val d8 = async { appPrefs.youtubeRestrictedMode.first() }
            val d9 = async { appPrefs.firewallEnabled.first() }
            val d10 = async { appPrefs.dnsProviderId.first() }

            val firewallEnabled = d9.await()
            val fwManager = if (firewallEnabled) {
                FirewallManager(context, firewallRuleDao).also {
                    it.loadRules()
                    Timber.d("Firewall enabled, rules loaded")
                }
            } else {
                null
            }

            StartupConfig(
                upstreamDns = d1.await(),
                fallbackDns = d2.await(),
                dnsResponseType = d3.await(),
                dnsProtocol = d4.await(),
                dohUrl = d5.await(),
                whitelistedApps = d6.await(),
                safeSearchEnabled = d7.await(),
                youtubeRestrictedMode = d8.await(),
                firewallEnabled = firewallEnabled,
                dnsProviderId = d10.await(),
                firewallManager = fwManager
            )
        }
    }

    suspend fun configureEngine(goTunnelAdapter: GoTunnelAdapter, config: StartupConfig) {
        var finalUpstreamDns = config.upstreamDns
        var finalDnsProtocol = config.dnsProtocol.name

        if (config.dnsProviderId == "system") {
            val systemDnsList = getSystemDnsServers(context)
            if (systemDnsList.isNotEmpty()) {
                finalUpstreamDns = systemDnsList.first()
                Timber.d("System DNS resolved to: $finalUpstreamDns")
            } else {
                finalUpstreamDns = "8.8.8.8"
                Timber.d("System DNS empty, falling back to 8.8.8.8")
            }
            finalDnsProtocol = "PLAIN"
        }

        goTunnelAdapter.configureDns(
            protocol = finalDnsProtocol,
            primary = finalUpstreamDns,
            fallback = config.fallbackDns,
            dohUrl = config.dohUrl
        )
        goTunnelAdapter.setBlockResponseType(config.dnsResponseType)
        goTunnelAdapter.configureSafeSearch(config.safeSearchEnabled, config.youtubeRestrictedMode)

        val splitDnsZones = appPrefs.splitDnsZones.first()
        goTunnelAdapter.setSplitDNSZones(splitDnsZones)
    }

    suspend fun startTunnel(
        goTunnelAdapter: GoTunnelAdapter,
        vpnInterface: ParcelFileDescriptor,
        resolvedWgConfigJson: String,
        httpsFilteringEnabled: Boolean,
        certDir: String,
        socketProtector: (Int) -> Boolean
    ) {
        val routingMode = appPrefs.getRoutingModeSnapshot()
        val wgConfigJson = if (routingMode == AppPreferences.ROUTING_MODE_WIREGUARD) {
            resolvedWgConfigJson.ifEmpty { appPrefs.getWgConfigJsonSnapshot() ?: "" }
        } else {
            ""
        }

        val selectedBrowsers = appPrefs.getSelectedBrowsersSnapshot()
        val filterHttp3 = appPrefs.getFilterHttp3Snapshot()

        goTunnelAdapter.start(
            vpnInterface = vpnInterface,
            wgConfigJson = wgConfigJson,
            httpsFilteringEnabled = httpsFilteringEnabled,
            selectedBrowsers = selectedBrowsers,
            certDir = certDir,
            filterHttp3 = filterHttp3,
            socketProtector = socketProtector
        )
    }

    suspend fun handleLinkPropertiesChanged(
        goTunnelAdapter: GoTunnelAdapter,
        linkProperties: LinkProperties?
    ) {
        val providerId = appPrefs.dnsProviderId.first()
        if (providerId == "system") {
            val newDns = linkProperties?.dnsServers?.mapNotNull { it.hostAddress }
                ?.filter { it.isNotEmpty() } ?: emptyList()
            val primary = newDns.firstOrNull() ?: "8.8.8.8"
            Timber.d("Network LinkProperties changed, hot-reloading System DNS: $primary")
            val fallback = appPrefs.fallbackDns.first()
            val dohUrl = appPrefs.dohUrl.first()
            goTunnelAdapter.configureDns(
                protocol = "PLAIN",
                primary = primary,
                fallback = fallback,
                dohUrl = dohUrl
            )
        }
    }

    fun startFilterUpdateWatcher(scope: CoroutineScope, goTunnelAdapter: GoTunnelAdapter) {
        scope.launch {
            // Keyed off filtersRevision (not domainCountFlow) because a sync can
            // download the binaries while leaving the rule count unchanged; the
            // count-based watcher missed that case and the engine kept running
            // with the empty paths it was given at boot.
            filterRepo.filtersRevision.drop(1).collectLatest { revision ->
                Timber.d("Filter rules changed (revision=$revision). Dynamically updating Native Go Tries.")
                goTunnelAdapter.updateTries()
                goTunnelAdapter.updateCosmeticRules()
            }
        }
    }

    private fun getSystemDnsServers(context: Context): List<String> {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork ?: return emptyList()
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return emptyList()
        return linkProperties.dnsServers.mapNotNull { it.hostAddress }.filter { it.isNotEmpty() }
    }
}
