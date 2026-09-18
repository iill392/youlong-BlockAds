package app.pwhs.blockads.data.repository

import android.content.Context
import app.pwhs.blockads.data.dao.CustomDnsRuleDao
import app.pwhs.blockads.data.dao.FilterListDao
import app.pwhs.blockads.data.dao.WhitelistDomainDao
import app.pwhs.blockads.data.entities.FilterList
import app.pwhs.blockads.data.remote.FilterDownloadManager
import app.pwhs.blockads.data.remote.FilterSourceMirror
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FilterListRepository(
    private val context: Context,
    private val filterListDao: FilterListDao,
    private val whitelistDomainDao: WhitelistDomainDao,
    private val customDnsRuleDao: CustomDnsRuleDao,
    private val client: HttpClient,
    private val downloadManager: FilterDownloadManager
) {

    companion object {
        private const val CACHE_DIR = "filter_cache"
        const val BLOCK_REASON_CUSTOM_RULE = "CUSTOM_RULE"
        const val BLOCK_REASON_FILTER_LIST = "FILTER_LIST"
        const val BLOCK_REASON_SECURITY = "SECURITY"
        const val BLOCK_REASON_FIREWALL = "FIREWALL"
        const val BLOCK_REASON_UPSTREAM_DNS = "upstream_dns"

        private const val FILTER_LIST_JSON_URL =
            "https://raw.githubusercontent.com/pass-with-high-score/blockads-default-filter/refs/heads/main/output/filter_lists.json"

        /**
         * Hard cap for the remote manifest fetch. The manifest is a small JSON
         * document, so anything beyond a few seconds means the CDN is unreachable.
         * Without this cap the read loop below could stall the caller
         * indefinitely. Sized with headroom because the fallback source is the
         * throttled raw.githubusercontent.com host, not the jsDelivr mirror.
         */
        private const val MANIFEST_FETCH_TIMEOUT_MS = 30_000L

        /**
         * How long the (network-free) startup load may wait for an in-flight
         * background filter load to release [loadMutex]. A background sync can be
         * busy downloading tens of megabytes for minutes; the startup path must
         * never sit behind it, so it falls back to the cached trie/bloom paths.
         */
        private const val STARTUP_LOCK_TIMEOUT_MS = 4_000L

        /**
         * How many filter binaries may be fetched at once.
         *
         * A fresh install has the [RECOMMENDED_LISTS] to fetch, roughly 5 MB of
         * `.trie`/`.bloom` in total, and a user who enables more from the list
         * screen can push that well past 50 MB. Sequentially that is minutes of
         * fetching during which the engine only has the bundled baseline; a small
         * amount of concurrency cuts it to roughly the size of the slowest single
         * file while staying gentle on the CDN and on memory.
         */
        private const val MAX_CONCURRENT_FILTER_DOWNLOADS = 4

        /**
         * Overall ceiling for a background sync. It holds [loadMutex] for its
         * whole duration, so a stalled CDN must never be able to keep it alive
         * indefinitely.
         *
         * The default rule sets need well over two minutes at
         * raw.githubusercontent.com speeds, and every extra enabled list adds to
         * that, so the old 180 s ceiling could not cover a fallback run.
         */
        private const val BACKGROUND_SYNC_TIMEOUT_MS = 900_000L

        /**
         * Mirror of the last successfully resolved engine paths.
         *
         * The compiled `.trie` / `.bloom` binaries live in `filesDir` and
         * outlive the list rows, so restoring these on cold start keeps
         * blocking active even when the list table is empty (the remote
         * manifest is on raw.githubusercontent.com and is unreachable on many
         * networks) — and it removes a DB round trip from the start-up path.
         */
        private const val FAST_PREFS_NAME = "blockads_fast_prefs"
        private const val KEY_AD_TRIE_PATHS = "filter_ad_trie_paths"
        private const val KEY_SEC_TRIE_PATHS = "filter_sec_trie_paths"
        private const val KEY_AD_BLOOM_PATHS = "filter_ad_bloom_paths"
        private const val KEY_SEC_BLOOM_PATHS = "filter_sec_bloom_paths"
        private const val KEY_LAST_BOOTSTRAP_MS = "filter_last_bootstrap_ms"

        /**
         * Ceiling for the last-resort network bootstrap. Blocking nothing at
         * all is a much worse outcome than one slow start, so when the engine
         * has no usable rules we allow one bounded download attempt.
         *
         * Must exceed the time needed to fetch a whole rule set: at 30 s this
         * "recovery" could never finish a single 1.7 MB `.trie` file, so an
         * install that started with no rules stayed broken forever. A fresh
         * install fetches every built-in list (~62 MB) before it is useful, which
         * even with the download concurrency below takes minutes on a slow line.
         */
        private const val NETWORK_BOOTSTRAP_TIMEOUT_MS = 600_000L

        /**
         * Minimum spacing between bootstrap attempts. Without this, a
         * permanently unreachable CDN would add [NETWORK_BOOTSTRAP_TIMEOUT_MS]
         * to *every* start — which is exactly the “sometimes a whole day”
         * behaviour that had to be eliminated.
         */
        private const val NETWORK_BOOTSTRAP_INTERVAL_MS = 15 * 60 * 1000L

        /**
         * Filter lists that are switched on by default, named exactly as the
         * remote manifest publishes them.
         *
         * The manifest marks only two of its twenty-two lists as enabled
         * (StevenBlack Unified + EasyPrivacy), which covers classic desktop-era
         * ad hosts and trackers and misses the mobile ad SDKs that produce most
         * in-app advertising. These are the lists that measurably widen
         * coverage, so they are enabled regardless of the manifest flag:
         *
         *  - AdGuard Mobile Ads  - the in-app ad SDK domains (tiny, high yield)
         *  - Hagezi Pro          - modern ads/tracking/telemetry coverage
         *  - ABPVN, HostsVN      - regional ad hosts the global lists miss
         *
         * Deliberately excluded: the Adult/Gambling/Social lists (they break
         * legitimate content) and the multi-million rule security feeds, which
         * would dominate the download for little ad-blocking gain. The bundled
         * baseline in [BuiltinRuleSource] covers the first-launch case.
         */
        private val RECOMMENDED_LISTS = setOf(
            "StevenBlack Unified",
            "EasyPrivacy",
            "AdGuard Mobile Ads",
            "AdGuard Base Filter",
            "Hagezi Pro",
            "Peter Lowe's Ad and tracking server list",
            "ABPVN",
            "HostsVN",
        )

        /**
         * Set once, after the first rule load of this build, so the recommended
         * lists are switched on exactly once for installs created before they
         * were recommended. A later manual opt-out is respected.
         */
        private const val KEY_RECOMMENDED_MIGRATION = "filter_recommended_migration_done"
    }

    // Paths to pre-compiled binary files for Go Native Engine (CSV strings)
    @Volatile
    private var adTriePaths: String = ""

    @Volatile
    private var securityTriePaths: String = ""

    @Volatile
    private var adBloomPaths: String = ""

    @Volatile
    private var securityBloomPaths: String = ""

    private val loadMutex = Mutex()

    private val fastPrefs = context.applicationContext
        .getSharedPreferences(FAST_PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Bumped whenever the engine's rule paths change, so the VPN service can
     * hot-reload the native tries. The previous watcher keyed off the rule
     * count, which missed syncs that downloaded binaries without changing the
     * total count — leaving the engine with no rules at all.
     */
    private val _filtersRevision = MutableStateFlow(0L)
    val filtersRevision: StateFlow<Long> = _filtersRevision.asStateFlow()

    init {
        File(context.filesDir, "remote_filters").mkdirs()
        restoreCachedPaths()
    }

    /**
     * Restores the engine paths persisted by [persistCachedPaths] in a previous
     * session, but only when every referenced binary still exists on disk.
     */
    private fun restoreCachedPaths() {
        try {
            val adTrie = fastPrefs.getString(KEY_AD_TRIE_PATHS, "").orEmpty()
            val secTrie = fastPrefs.getString(KEY_SEC_TRIE_PATHS, "").orEmpty()
            val adBloom = fastPrefs.getString(KEY_AD_BLOOM_PATHS, "").orEmpty()
            val secBloom = fastPrefs.getString(KEY_SEC_BLOOM_PATHS, "").orEmpty()

            if (adTrie.isEmpty() && adBloom.isEmpty()) {
                // Nothing mirrored yet (first run of this build, or the mirror
                // was cleared) — fall back to whatever is already on disk.
                recoverPathsFromDisk()
                return
            }
            if (!csvFilesExist(adTrie) || !csvFilesExist(adBloom) ||
                !csvFilesExist(secTrie) || !csvFilesExist(secBloom)
            ) {
                Timber.w("Cached filter paths reference files that no longer exist — rescanning disk")
                recoverPathsFromDisk()
                return
            }

            adTriePaths = adTrie
            securityTriePaths = secTrie
            adBloomPaths = adBloom
            securityBloomPaths = secBloom
            Timber.d("Restored ${adTrie.split(',').size} cached filter path set(s) from previous session")
        } catch (e: Exception) {
            Timber.w(e, "Failed to restore cached filter paths")
        }
    }

    /**
     * Rebuilds the engine paths from the compiled binaries still present in the
     * filter cache directory.
     *
     * This is the last line of defence against "protection is on but blocks
     * nothing": the engine is driven purely by the `.trie` / `.bloom` paths it
     * is handed, so if the list table and the mirror are both empty while the
     * binaries survive on disk, the correct action is to reuse them rather than
     * start with no rules at all.
     *
     * Only ids that have **both** a `.trie` and a `.bloom` are used, so a
     * half-finished download can never produce a mismatched pair.
     */
    private fun recoverPathsFromDisk(): Boolean {
        return try {
            val dir = File(context.filesDir, "remote_filters")
            val byId = (dir.listFiles() ?: emptyArray())
                .filter { it.isFile && it.length() > 0 && it.extension in setOf("trie", "bloom") }
                .groupBy { it.nameWithoutExtension }
                .filterValues { group ->
                    group.any { it.extension == "trie" } && group.any { it.extension == "bloom" }
                }

            if (byId.isEmpty()) return false

            val ids = byId.keys.sorted()
            adTriePaths = ids.joinToString(",") { File(dir, "$it.trie").absolutePath }
            adBloomPaths = ids.joinToString(",") { File(dir, "$it.bloom").absolutePath }
            securityTriePaths = ""
            securityBloomPaths = ""
            persistCachedPaths()
            Timber.w("Recovered ${ids.size} filter rule set(s) from the on-disk binary cache")
            true
        } catch (e: Exception) {
            Timber.w(e, "Failed to recover filter paths from disk")
            false
        }
    }

    private fun csvFilesExist(csv: String): Boolean =
        csv.isEmpty() || csv.split(',').all { it.isNotBlank() && File(it).exists() }

    /**
     * Merges comma-separated path lists, preserving order and dropping blanks
     * and duplicates. Used to fold the bundled rule sets into the downloaded
     * ones without ever handing the engine the same path twice.
     */
    private fun joinPaths(vararg groups: String): String {
        val merged = LinkedHashSet<String>()
        for (group in groups) {
            for (path in group.split(',')) {
                val trimmed = path.trim()
                if (trimmed.isNotEmpty()) merged += trimmed
            }
        }
        return merged.joinToString(",")
    }

    private fun persistCachedPaths() {
        try {
            fastPrefs.edit()
                .putString(KEY_AD_TRIE_PATHS, adTriePaths)
                .putString(KEY_SEC_TRIE_PATHS, securityTriePaths)
                .putString(KEY_AD_BLOOM_PATHS, adBloomPaths)
                .putString(KEY_SEC_BLOOM_PATHS, securityBloomPaths)
                .apply()
        } catch (e: Exception) {
            Timber.w(e, "Failed to persist filter paths")
        }
    }

    private val whitelistedDomains = ConcurrentHashMap.newKeySet<String>()
    private val customBlockDomains = ConcurrentHashMap.newKeySet<String>()
    private val customAllowDomains = ConcurrentHashMap.newKeySet<String>()

    private val _domainCountFlow = MutableStateFlow(0)
    val domainCountFlow: StateFlow<Int> = _domainCountFlow.asStateFlow()
    val domainCount: Int get() = _domainCountFlow.value

    fun getAdTriePath(): String = adTriePaths
    fun getSecurityTriePath(): String = securityTriePaths
    fun getAdBloomPath(): String = adBloomPaths
    fun getSecurityBloomPath(): String = securityBloomPaths

    /**
     * True when the native engine has at least one rule source it can enforce.
     * Used to tell “protection is on but has nothing to match against” (the
     * symptom of the filter binaries being missing) apart from a healthy state.
     */
    fun hasUsableRules(): Boolean {
        if (adTriePaths.isNotBlank() || adBloomPaths.isNotBlank()) return true
        if (securityTriePaths.isNotBlank() || securityBloomPaths.isNotBlank()) return true
        // The APK-bundled rule sets may not have been folded into the cached
        // paths yet (they are prepared asynchronously on the load path), so
        // report them directly rather than claiming there is nothing to enforce.
        if (BuiltinRuleSource.triePaths().isNotBlank() || BuiltinRuleSource.bloomPaths().isNotBlank()) return true
        if (customBlockDomains.isNotEmpty()) return true
        return getCosmeticCssPath() != null
    }

    fun getScriptletsPath(): String? {
        val file = File(context.filesDir, "$CACHE_DIR/scriptlets_combined.txt")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    fun getCosmeticCssPath(): String? {
        val file = File(context.filesDir, "$CACHE_DIR/cosmetic_rules.css")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    private inline fun checkDomainAndParents(
        domain: String,
        checker: (String) -> Boolean
    ): Boolean {
        if (checker(domain)) return true
        var d = domain
        while (d.contains('.')) {
            d = d.substringAfter('.')
            if (checker(d)) return true
            if (checker("*.$d")) return true
        }
        return false
    }

    fun isBlocked(domain: String): Boolean {
        if (checkDomainAndParents(domain) { customAllowDomains.contains(it) }) return false
        if (checkDomainAndParents(domain) { customBlockDomains.contains(it) }) return true
        if (checkDomainAndParents(domain) { whitelistedDomains.contains(it) }) return false
        return false
    }

    fun hasCustomRule(domain: String): Long {
        if (checkDomainAndParents(domain) { customAllowDomains.contains(it) }) return 0L
        if (checkDomainAndParents(domain) { whitelistedDomains.contains(it) }) return 0L
        if (checkDomainAndParents(domain) { customBlockDomains.contains(it) }) return 1L
        return -1L
    }

    fun getBlockReason(domain: String): String {
        if (checkDomainAndParents(domain) { customAllowDomains.contains(it) }) return ""
        if (checkDomainAndParents(domain) { customBlockDomains.contains(it) }) return BLOCK_REASON_CUSTOM_RULE
        if (checkDomainAndParents(domain) { whitelistedDomains.contains(it) }) return ""
        return ""
    }

    suspend fun loadCustomRules() {
        val blockDomains = customDnsRuleDao.getBlockDomains()
        val allowDomains = customDnsRuleDao.getAllowDomains()
        customBlockDomains.clear()
        customBlockDomains.addAll(blockDomains.map { it.lowercase() })
        customAllowDomains.clear()
        customAllowDomains.addAll(allowDomains.map { it.lowercase() })
        Timber.d("Loaded ${customBlockDomains.size} block + ${customAllowDomains.size} allow custom rules")
    }

    suspend fun loadWhitelist() {
        val domains = whitelistDomainDao.getAllDomains()
        whitelistedDomains.clear()
        whitelistedDomains.addAll(domains.map { it.lowercase() })
        Timber.d("Loaded ${whitelistedDomains.size} whitelisted domains")
    }

    // ────────────────────────────────────────────────────────────────────
    // Seeding & Remote Sync
    // ────────────────────────────────────────────────────────────────────

    /**
     * Seeds default filter lists by fetching from the remote JSON URL.
     * Updates existing entries so bloomUrl/trieUrl/cssUrl/ruleCount stay current.
     */
    suspend fun seedDefaultsIfNeeded() {
        fetchAndSyncRemoteFilterLists()
    }

    /**
     * Background counterpart of the service-startup path: performs the network
     * work that startup deliberately skips (remote manifest sync + download of
     * any missing filter binaries) and then reloads the engine.
     *
     * Call this only after the tunnel is up and always off the startup path —
     * it may take a long time on a slow or partially reachable network.
     */
    suspend fun backgroundSyncMissingFilters(): Result<Int> = withContext(Dispatchers.IO) {
        val completed = withTimeoutOrNull(BACKGROUND_SYNC_TIMEOUT_MS) {
            try {
                // Pulls the remote manifest and seeds/refreshes built-in lists.
                seedDefaultsIfNeeded()
                // Now binaries can actually be downloaded.
                val reload = loadAllEnabledFilters(offlineOnly = false)
                Timber.d("Background filter sync done: ${reload.getOrDefault(0)} domains")
                reload
            } catch (e: Exception) {
                Timber.e(e, "Background filter sync failed")
                Result.failure<Int>(e)
            }
        }
        completed ?: run {
            Timber.w("Background filter sync timed out after ${BACKGROUND_SYNC_TIMEOUT_MS}ms")
            Result.failure(IllegalStateException("Filter sync timed out"))
        }
    }

    /**
     * Last-resort, self-rate-limited network bootstrap for when the engine has
     * **no** usable rules — the state in which the protection appears to be on
     * but blocks essentially nothing.
     *
     * Deliberately cannot run more than once per
     * [NETWORK_BOOTSTRAP_INTERVAL_MS]: an unreachable CDN must never be able to
     * add its timeout to every single start.
     *
     * @return true when rules became available.
     */
    suspend fun ensureUsableRules(): Boolean {
        if (hasUsableRules()) return true

        val now = System.currentTimeMillis()
        val lastAttempt = fastPrefs.getLong(KEY_LAST_BOOTSTRAP_MS, 0L)
        if (now - lastAttempt < NETWORK_BOOTSTRAP_INTERVAL_MS) {
            Timber.w("No usable filter rules and the last recovery attempt was recent — skipping network bootstrap")
            return false
        }
        fastPrefs.edit().putLong(KEY_LAST_BOOTSTRAP_MS, now).apply()

        Timber.w("No usable filter rules — running a bounded ${NETWORK_BOOTSTRAP_TIMEOUT_MS}ms network recovery")
        withTimeoutOrNull(NETWORK_BOOTSTRAP_TIMEOUT_MS) { backgroundSyncMissingFilters() }

        val ok = hasUsableRules()
        if (ok) {
            Timber.d("Filter recovery succeeded")
        } else {
            Timber.e(
                "Filter recovery failed — protection is currently running WITHOUT any " +
                    "blocklist. Check network access to the filter CDN / use a custom " +
                    "filter list source."
            )
        }
        return ok
    }

    /**
     * Fetches the remote filter_lists.json and syncs pre-compiled URLs to the local DB.
     * Keeps bloomUrl/trieUrl/cssUrl/ruleCount fresh when the server regenerates binaries.
     */
    suspend fun fetchAndSyncRemoteFilterLists() = withContext(Dispatchers.IO) {
        try {
            // Bounded fetch — never let an unreachable CDN stall the caller.
            // The manifest is tiny (~15 KB) but lives on the same throttled GitHub
            // host as the rule binaries, so the CDN mirror is tried first here too.
            var jsonString: String? = null
            for (source in FilterSourceMirror.candidates(FILTER_LIST_JSON_URL)) {
                val fetched = withTimeoutOrNull(MANIFEST_FETCH_TIMEOUT_MS) {
                    try {
                        val channel = client.get(source).bodyAsChannel()
                        val buffer = ByteArray(256 * 1024)
                        val output = java.io.ByteArrayOutputStream()
                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer)
                            if (read > 0) output.write(buffer, 0, read)
                        }
                        output.toString(Charsets.UTF_8.name())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Manifest fetch failed from $source")
                        null
                    }
                }
                if (!fetched.isNullOrBlank()) {
                    jsonString = fetched
                    break
                }
                Timber.w("Manifest source produced no data: $source")
            }

            if (jsonString == null) {
                Timber.w("Remote filter manifest fetch timed out after ${MANIFEST_FETCH_TIMEOUT_MS}ms, keeping local data")
                return@withContext
            }

            val remoteLists = parseRemoteFilterJson(jsonString)
            if (remoteLists.isEmpty()) return@withContext

            val existingLists = filterListDao.getAllSync()
            val existingByName = existingLists.associateBy { it.name }

            for (remote in remoteLists) {
                val existing = existingByName[remote.name]
                val category = if (remote.category == "security") FilterList.CATEGORY_SECURITY else FilterList.CATEGORY_AD
                if (existing != null) {
                    val needsUpdate = existing.url != (remote.originalUrl ?: "") ||
                        existing.description != (remote.description ?: "") ||
                        existing.category != category ||
                        existing.bloomUrl != remote.bloomUrl ||
                        existing.trieUrl != remote.trieUrl ||
                        existing.cssUrl != (remote.cssUrl ?: "") ||
                        existing.scriptletsUrl != (remote.scriptletsUrl ?: "") ||
                        existing.ruleCount != remote.ruleCount ||
                        existing.domainCount != remote.ruleCount ||
                        existing.originalUrl != (remote.originalUrl ?: existing.originalUrl) ||
                        !existing.isBuiltIn

                    if (needsUpdate) {
                        filterListDao.update(
                            existing.copy(
                                url = remote.originalUrl ?: "",
                                description = remote.description ?: "",
                                category = category,
                                bloomUrl = remote.bloomUrl,
                                trieUrl = remote.trieUrl,
                                domainCount = remote.ruleCount,
                                cssUrl = remote.cssUrl ?: "",
                                scriptletsUrl = remote.scriptletsUrl ?: "",
                                ruleCount = remote.ruleCount,
                                originalUrl = remote.originalUrl ?: existing.originalUrl,
                                isBuiltIn = true
                            )
                        )
                        Timber.d("Updated remote filter: ${remote.name}")
                    }
                } else {
                    filterListDao.insert(
                        FilterList(
                            name = remote.name,
                            url = remote.originalUrl ?: "",
                            description = remote.description ?: "",
                            // A list is on by default when the manifest asks for
                            // it, or when it is one of the lists that actually
                            // widen ad coverage (see RECOMMENDED_LISTS). Turning
                            // everything on — the previous behaviour — pulled in
                            // the Adult/Gambling/Social lists and multi-million
                            // rule security feeds for no extra ad blocking.
                            isEnabled = remote.isEnabled || remote.name in RECOMMENDED_LISTS,
                            isBuiltIn = remote.isBuiltIn,
                            category = category,
                            ruleCount = remote.ruleCount,
                            bloomUrl = remote.bloomUrl,
                            trieUrl = remote.trieUrl,
                            cssUrl = remote.cssUrl ?: "",
                            scriptletsUrl = remote.scriptletsUrl ?: "",
                            originalUrl = remote.originalUrl ?: ""
                        )
                    )
                    Timber.d("Inserted new remote filter: ${remote.name}")
                }
            }

            val remoteNames = remoteLists.map { it.name }.toSet()
            val obsolete = existingLists.filter { it.isBuiltIn && it.name !in remoteNames }
            for (o in obsolete) {
                filterListDao.delete(o)
                Timber.d("Removed obsolete built-in filter: ${o.name}")
            }

            enableRecommendedListsOnce()

            Timber.d("Synced ${remoteLists.size} filters from remote JSON")
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch remote filter list JSON")
        }
    }

    /**
     * Turns the [RECOMMENDED_LISTS] on for installs that were set up before they
     * were recommended, then records that the migration ran.
     *
     * Runs at most once per install: the flag is written even when there is
     * nothing to change, so a list the user switches off afterwards stays off.
     * Fresh installs are already correct because
     * [fetchAndSyncRemoteFilterLists] inserts new rows with the same policy, so
     * this is a no-op for them.
     *
     * Called from the rule-load path rather than the manifest sync on purpose:
     * the manifest lives on the same host that is unreachable on exactly the
     * networks where a wider default rule set matters most, and gating the
     * migration on a successful fetch would leave those installs on the old,
     * two-list default forever.
     */
    private suspend fun enableRecommendedListsOnce() {
        if (fastPrefs.getBoolean(KEY_RECOMMENDED_MIGRATION, false)) return

        try {
            val disabled = filterListDao.getAllSync()
                .filter { it.name in RECOMMENDED_LISTS && !it.isEnabled }

            for (list in disabled) {
                filterListDao.setEnabled(list.id, true)
                Timber.i("Enabled recommended filter list: ${list.name}")
            }

            fastPrefs.edit().putBoolean(KEY_RECOMMENDED_MIGRATION, true).apply()
        } catch (e: Exception) {
            Timber.w(e, "Failed to apply the recommended filter list defaults")
        }
    }

    /**
     * Simple JSON parser for the filter_lists.json array.
     */
    private fun parseRemoteFilterJson(json: String): List<app.pwhs.blockads.data.remote.models.FilterList> {
        return try {
            val results = mutableListOf<app.pwhs.blockads.data.remote.models.FilterList>()
            val objects = json.split("},").map {
                it.trim().removePrefix("[").removeSuffix("]").trim() + "}"
            }

            for (obj in objects) {
                val cleaned = obj.trim().removePrefix("{").removeSuffix("}").removeSuffix("},")
                if (cleaned.isBlank()) continue

                fun extractString(key: String): String? {
                    val pattern = "\"$key\"\\s*:\\s*\"(.*?)\"".toRegex()
                    return pattern.find(cleaned)?.groupValues?.get(1)
                        ?.replace("\\u0026", "&")
                }

                fun extractInt(key: String): Int {
                    val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                    return pattern.find(cleaned)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                }

                fun extractBoolean(key: String): Boolean {
                    val pattern = "\"$key\"\\s*:\\s*(true|false)".toRegex()
                    return pattern.find(cleaned)?.groupValues?.get(1) == "true"
                }

                val name = extractString("name") ?: continue
                val bloomUrl = extractString("bloomUrl") ?: continue
                val trieUrl = extractString("trieUrl") ?: continue

                results.add(
                    app.pwhs.blockads.data.remote.models.FilterList(
                        name = name,
                        id = extractString("id") ?: name.lowercase().replace(" ", "_"),
                        description = extractString("description"),
                        isEnabled = extractBoolean("isEnabled"),
                        isBuiltIn = extractBoolean("isBuiltIn"),
                        category = extractString("category"),
                        ruleCount = extractInt("ruleCount"),
                        bloomUrl = bloomUrl,
                        trieUrl = trieUrl,
                        cssUrl = extractString("cssUrl"),
                        scriptletsUrl = extractString("scriptletsUrl"),
                        originalUrl = extractString("originalUrl")
                    )
                )
            }
            results
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse remote filter JSON")
            emptyList()
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Filter Loading & Download
    // ────────────────────────────────────────────────────────────────────

    /**
     * Load all enabled filter lists using FilterDownloadManager.
     * Downloads pre-compiled .bloom/.trie files and collects paths as CSV strings for the Go engine.
     */
    /**
     * Loads the paths of every enabled filter into the native engine.
     *
     * @param offlineOnly when true (service startup) only binaries already present
     *   on disk are resolved and **no network request is made**. Turning the
     *   protection on must stay fast even when the filter CDN is unreachable;
     *   anything missing is fetched afterwards via [backgroundSyncMissingFilters].
     *   When false, missing binaries are downloaded inline (background callers).
     */
    suspend fun loadAllEnabledFilters(offlineOnly: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        // Extract/validate the APK-bundled rule sets first. They are the only
        // rule source that works with no network at all, so they have to be on
        // disk before the engine's first start — otherwise an install that
        // cannot reach the filter CDN blocks nothing. This deliberately sits
        // *outside* the startup timeout below: it copies ~6 MB on a cold install
        // and a timeout that aborts it would leave the engine with no rules.
        BuiltinRuleSource.prepare(context)

        if (offlineOnly) {
            // Startup path — see [STARTUP_LOCK_TIMEOUT_MS]. Waiting behind a
            // background download is what made "turn on protection" take
            // seconds-to-minutes on some devices.
            withTimeoutOrNull(STARTUP_LOCK_TIMEOUT_MS) { loadAllEnabledFiltersLocked(offlineOnly) }
                ?: run {
                    Timber.w("Another filter load is in progress, using cached filter paths")
                    Result.success(_domainCountFlow.value)
                }
        } else {
            loadAllEnabledFiltersLocked(offlineOnly)
        }
    }

    private suspend fun loadAllEnabledFiltersLocked(offlineOnly: Boolean): Result<Int> =
        loadMutex.withLock {
            try {
                // One-time upgrade of the default rule set for installs created
                // before the wider defaults existed. Runs before the enabled
                // rows are read so the newly enabled lists are picked up by this
                // very load and the engine sees a complete rule set.
                enableRecommendedListsOnce()

                val enabledLists = filterListDao.getEnabled()
                if (enabledLists.isEmpty()) {
                    // An empty result is NOT proof that filtering should stop.
                    // The list table is seeded from a manifest hosted on
                    // raw.githubusercontent.com, which is unreachable on many
                    // networks, so "no enabled lists" usually just means
                    // "not seeded yet / migration in flight". Wiping the
                    // engine paths in that case is what silently dropped
                    // blocking to ~0% after the fast-start change. Only a
                    // deliberate configuration (rows exist, none enabled)
                    // clears the rules.
                    val hasConfiguredLists = filterListDao.getAllSync().isNotEmpty()
                    if (hasConfiguredLists) {
                        // A deliberate configuration outranks the built-in
                        // baseline: the user switched every list off, so the
                        // downloaded rule sets go. The bundled ones stay, because
                        // they are not a filter list the user can switch off and
                        // dropping them would leave the engine with nothing.
                        adTriePaths = BuiltinRuleSource.triePaths()
                        securityTriePaths = ""
                        adBloomPaths = BuiltinRuleSource.bloomPaths()
                        securityBloomPaths = ""
                        persistCachedPaths()
                        _filtersRevision.value = System.currentTimeMillis()
                        File(context.filesDir, "$CACHE_DIR/cosmetic_rules.css").delete()
                    } else {
                        // Nothing configured at all — reuse the binaries already
                        // on disk so the engine still has something to match.
                        if (!hasUsableRules()) recoverPathsFromDisk()
                        Timber.w(
                            "No filter lists configured yet — keeping " +
                                "${adTriePaths.split(',').count { it.isNotBlank() }} cached " +
                                "rule set(s) so blocking stays active"
                        )
                    }
                    // The bundled sets are still enforced in this configuration,
                    // so report their rules rather than the misleading "0".
                    _domainCountFlow.value = BuiltinRuleSource.ruleCount()
                    return@withLock Result.success(0)
                }

                val startTime = System.currentTimeMillis()
                val adTrieSb = StringBuilder()
                val secTrieSb = StringBuilder()
                val adBloomSb = StringBuilder()
                val secBloomSb = StringBuilder()
                var totalCount = 0

                // Downloads are independent and purely network-bound, so they run
                // concurrently. Done one-by-one the default set takes minutes while
                // the engine still has no rules at all — which is exactly when the
                // block rate reads 0%. The semaphore keeps concurrency, and with it
                // peak memory and CDN pressure, bounded.
                val downloadable = enabledLists.filter { filter ->
                    val usable = filter.bloomUrl.isNotEmpty() && filter.trieUrl.isNotEmpty()
                    if (!usable) Timber.d("Skipping ${filter.name}: no pre-compiled URLs")
                    usable
                }

                val downloadGate = Semaphore(MAX_CONCURRENT_FILTER_DOWNLOADS)
                val downloadResults = coroutineScope {
                    downloadable.map { filter ->
                        async(Dispatchers.IO) {
                            downloadGate.withPermit {
                                filter to downloadManager.downloadFilterList(
                                    filter = filter,
                                    forceUpdate = false,
                                    allowDownload = !offlineOnly
                                )
                            }
                        }
                    }.awaitAll()
                }

                // Aggregate on this single coroutine so the path builders below are
                // never mutated concurrently.
                for ((filter, result) in downloadResults) {
                    if (result.isSuccess) {
                        val paths = result.getOrNull() ?: continue
                        if (filter.category == FilterList.CATEGORY_SECURITY) {
                            paths.triePath?.let { secTrieSb.append(it).append(",") }
                            paths.bloomPath?.let { secBloomSb.append(it).append(",") }
                        } else {
                            paths.triePath?.let { adTrieSb.append(it).append(",") }
                            paths.bloomPath?.let { adBloomSb.append(it).append(",") }
                        }
                        // Update per-filter domainCount so UI shows the correct value
                        if (filter.domainCount != filter.ruleCount) {
                            filterListDao.updateStats(
                                id = filter.id,
                                count = filter.ruleCount,
                                timestamp = System.currentTimeMillis()
                            )
                        }
                        totalCount += filter.ruleCount
                    } else {
                        Timber.e("Failed to download filter: ${filter.name}")
                    }
                }

                val downloadedAdTriePaths = adTrieSb.toString().trimEnd(',')
                val newSecurityTriePaths = secTrieSb.toString().trimEnd(',')
                val downloadedAdBloomPaths = adBloomSb.toString().trimEnd(',')
                val newSecurityBloomPaths = secBloomSb.toString().trimEnd(',')

                // The bundled baseline is always part of the ad rule set: it is
                // what keeps blocking alive when every remote download failed,
                // and it is what makes the very first launch block anything.
                val newAdTriePaths = joinPaths(downloadedAdTriePaths, BuiltinRuleSource.triePaths())
                val newAdBloomPaths = joinPaths(downloadedAdBloomPaths, BuiltinRuleSource.bloomPaths())

                // Count what the engine will actually enforce, not just what the
                // downloaded filter lists claim: the bundled sets contribute too
                // and leaving them out understates the number shown to the user.
                totalCount += BuiltinRuleSource.ruleCount()

                // Never overwrite a working rule set with nothing. This can
                // happen when every download fails (CDN blocked) but the
                // binaries from a previous session are still on disk and were
                // already restored by restoreCachedPaths().
                val newAdPathsEmpty = downloadedAdTriePaths.isEmpty() && downloadedAdBloomPaths.isEmpty()
                val hadAdPaths = adTriePaths.isNotEmpty() || adBloomPaths.isNotEmpty()
                if (newAdPathsEmpty && hadAdPaths) {
                    Timber.w("Ad filter load produced no paths — keeping the ${adTriePaths.split(',').count { it.isNotBlank() }} previously loaded rule set(s)")
                    // A set restored from a previous session predates the bundled
                    // baseline on the first run of this build, so fold it in.
                    adTriePaths = joinPaths(adTriePaths, BuiltinRuleSource.triePaths())
                    adBloomPaths = joinPaths(adBloomPaths, BuiltinRuleSource.bloomPaths())
                    persistCachedPaths()
                } else {
                    adTriePaths = newAdTriePaths
                    securityTriePaths = newSecurityTriePaths
                    adBloomPaths = newAdBloomPaths
                    securityBloomPaths = newSecurityBloomPaths
                    persistCachedPaths()
                }

                // If we still have nothing to enforce, fall back to the binaries
                // on disk before this load reports "0 rules".
                if (!hasUsableRules()) {
                    recoverPathsFromDisk()
                }

                compileCosmeticAndScriptletRules(enabledLists)

                // Signal the engine to re-read its rule paths even when the
                // rule count happens to be unchanged (e.g. a re-download).
                _filtersRevision.value = System.currentTimeMillis()

                _domainCountFlow.value = totalCount
                val elapsed = System.currentTimeMillis() - startTime
                Timber.d("Filter paths loaded in ${elapsed}ms, totalRules=$totalCount")
                Result.success(totalCount)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load filters")
                Result.failure(e)
            }
        }

    /**
     * Recompiles the combined cosmetic CSS / scriptlet files **only when their
     * inputs actually changed**.
     *
     * Recompiling means re-reading, re-parsing and re-writing every enabled
     * list's `.css` / `.scriptlets` file, which for a normal uBlock/AdGuard set
     * is several megabytes of disk churn. It used to run on *every* startup,
     * inside the [loadMutex], which is one of the reasons "turn on protection"
     * could take seconds to minutes. The signature below is a cheap
     * (size + mtime) fingerprint of all inputs, so an unchanged configuration
     * skips the work entirely.
     */
    private suspend fun compileCosmeticAndScriptletRules(enabledLists: List<FilterList>) {
        val signatureFile = File(context.filesDir, "$CACHE_DIR/compile.signature")
        val signature = buildCompileSignature(enabledLists)

        if (signatureFile.exists() && signatureFile.readText() == signature) {
            Timber.d("Cosmetic/scriptlet rules unchanged, skipping recompile")
            return
        }

        compileCosmeticRules(enabledLists)
        compileScriptletRules(enabledLists)

        try {
            signatureFile.parentFile?.mkdirs()
            signatureFile.writeText(signature)
        } catch (e: Exception) {
            Timber.w(e, "Failed to persist cosmetic compile signature")
        }
    }

    private fun buildCompileSignature(enabledLists: List<FilterList>): String {
        val sb = StringBuilder()
        for (filter in enabledLists) {
            val css = File(context.filesDir, "remote_filters/${filter.id}.css")
            val scriptlets = File(context.filesDir, "remote_filters/${filter.id}.scriptlets")
            sb.append(filter.id).append(':')
                .append(if (css.exists()) css.length() else -1).append(':')
                .append(if (css.exists()) css.lastModified() else 0).append(':')
                .append(if (scriptlets.exists()) scriptlets.length() else -1).append(':')
                .append(if (scriptlets.exists()) scriptlets.lastModified() else 0)
                .append(';')
        }
        return sb.toString()
    }

    private suspend fun compileScriptletRules(enabledLists: List<FilterList>) =
        withContext(Dispatchers.IO) {
            try {
                val validLists = enabledLists.filter { it.category != FilterList.CATEGORY_SECURITY }
                if (validLists.isEmpty()) {
                    File(context.filesDir, "$CACHE_DIR/scriptlets_combined.txt").delete()
                    return@withContext
                }

                val sb = StringBuilder()
                var added = 0
                for (filter in validLists) {
                    if (filter.scriptletsUrl.isEmpty()) continue
                    val f = File(context.filesDir, "remote_filters/${filter.id}.scriptlets")
                    if (f.exists() && f.length() > 0) {
                        sb.append(f.readText())
                        if (!sb.endsWith("\n")) sb.append("\n")
                        added++
                    }
                }

                val outFile = File(context.filesDir, "$CACHE_DIR/scriptlets_combined.txt")
                if (added > 0) {
                    outFile.parentFile?.mkdirs()
                    outFile.writeText(sb.toString())
                    Timber.d("Wrote scriptlets ($added lists, ${sb.length} bytes)")
                } else {
                    outFile.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to compile scriptlet rules")
            }
        }

    private suspend fun compileCosmeticRules(enabledLists: List<FilterList>) =
        withContext(Dispatchers.IO) {
            try {
                val validLists = enabledLists.filter { it.category != FilterList.CATEGORY_SECURITY }
                if (validLists.isEmpty()) {
                    // Drop any previously compiled output so a disabled list can
                    // never keep injecting stale cosmetic rules.
                    File(context.filesDir, "$CACHE_DIR/cosmetic_rules.css").delete()
                    return@withContext
                }

                val cssBuilder = StringBuilder()
                var rulesAdded = 0

                for (filter in validLists) {
                    if (filter.cssUrl.isEmpty()) continue
                    val cssFile = File(context.filesDir, "remote_filters/${filter.id}.css")
                    if (cssFile.exists() && cssFile.length() > 0) {
                        val cssSnippet = downloadManager.getInjectableCss(cssFile)
                        if (cssSnippet.isNotEmpty()) {
                            cssBuilder.append(cssSnippet)
                            rulesAdded++
                        }
                    }
                }

                if (rulesAdded > 0) {
                    val finalCssFile = File(context.filesDir, "$CACHE_DIR/cosmetic_rules.css")
                    finalCssFile.parentFile?.mkdirs()
                    finalCssFile.writeText(cssBuilder.toString())
                    Timber.d("Wrote cosmetic CSS (${cssBuilder.length} bytes)")
                } else {
                    File(context.filesDir, "$CACHE_DIR/cosmetic_rules.css").delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to compile cosmetic rules")
            }
        }

    suspend fun forceUpdateAllEnabledFilters(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // Sync the latest metadata for all built-in filters to the DB
            fetchAndSyncRemoteFilterLists()

            // Fetch the enabled built-in filters (now with updated ruleCounts and URLs)
            val enabledBuiltIn = filterListDao.getEnabled().filter { it.isBuiltIn }

            var totalCount = 0

            for (filter in enabledBuiltIn) {
                // Force download the binary files from the remote server
                val result = downloadManager.downloadFilterList(filter, forceUpdate = true)
                if (result.isSuccess) {
                    filterListDao.updateStats(
                        id = filter.id,
                        count = filter.ruleCount,
                        timestamp = System.currentTimeMillis()
                    )
                    totalCount += filter.ruleCount
                } else {
                    Timber.e("Failed to force update built-in filter: ${filter.name}")
                }
            }

            // Reload into the Go engine
            loadAllEnabledFilters()
            Result.success(totalCount)
        } catch (e: Exception) {
            Timber.e(e, "Failed to force update all enabled filters")
            Result.failure(e)
        }
    }

    suspend fun updateSingleFilter(filter: FilterList): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (filter.isBuiltIn) {
                // Fetch the latest remote configs
                fetchAndSyncRemoteFilterLists()

                // Get the updated entity from DB to have the latest ruleCount & URLs
                val updatedFilter = filterListDao.getById(filter.id) ?: filter

                val result = downloadManager.downloadFilterList(updatedFilter, forceUpdate = true)
                if (result.isSuccess) {
                    filterListDao.updateStats(
                        id = updatedFilter.id,
                        count = updatedFilter.ruleCount,
                        timestamp = System.currentTimeMillis()
                    )
                    loadAllEnabledFilters()
                    Result.success(updatedFilter.ruleCount)
                } else {
                    Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
                }
            } else {
                val result = downloadManager.downloadFilterList(filter, forceUpdate = true)
                if (result.isSuccess) {
                    filterListDao.updateStats(
                        id = filter.id,
                        count = filter.ruleCount,
                        timestamp = System.currentTimeMillis()
                    )
                    loadAllEnabledFilters()
                    Result.success(filter.ruleCount)
                } else {
                    Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
                }
            }
        } catch (e: Exception) {
            Timber.d("Failed to update ${filter.name}: $e")
            Result.failure(e)
        }
    }

    suspend fun findBlockingFilterLists(targetDomain: String): List<String> =
        withContext(Dispatchers.IO) {
            val enabledLists = filterListDao.getEnabled()
            if (enabledLists.isEmpty()) return@withContext emptyList()

            val matchedListNames = mutableListOf<String>()
            for (filter in enabledLists) {
                val trieFile = File(context.filesDir, "remote_filters/${filter.id}.trie")
                if (!trieFile.exists() || trieFile.length() == 0L) continue
                try {
                    if (tunnel.Tunnel.checkDomainInTrieFile(trieFile.absolutePath, targetDomain)) {
                        matchedListNames.add(filter.name)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error scanning trie for ${filter.name}")
                }
            }
            return@withContext matchedListNames
        }

    suspend fun checkDomainInFilter(filterId: Long, domain: String): Boolean =
        withContext(Dispatchers.IO) {
            val trieFile = File(context.filesDir, "remote_filters/$filterId.trie")
            if (!trieFile.exists() || trieFile.length() == 0L) return@withContext false

            return@withContext try {
                tunnel.Tunnel.checkDomainInTrieFile(trieFile.absolutePath, domain)
            } catch (e: Exception) {
                Timber.e(e, "Error scanning trie for $filterId regarding $domain")
                false
            }
        }

}
