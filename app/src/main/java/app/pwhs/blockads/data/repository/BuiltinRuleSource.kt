package app.pwhs.blockads.data.repository

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Ships a fixed set of pre-compiled rule binaries inside the APK.
 *
 * Every rule the native engine enforces has to be fetched from the filter CDN
 * first. On a network where that CDN is unreachable — or merely slow — the app
 * therefore runs with **no rules at all** and blocks nothing, which is exactly
 * the failure that pins the block rate near zero. The binaries below are
 * compiled from the same upstream lists the app would otherwise download, and
 * they are copied straight out of the APK, so core ad/tracker blocking works on
 * the very first launch — offline, before a single byte has been downloaded.
 *
 * They are registered as *additional* rule sources: every remote list the user
 * enables still contributes on top of them.
 *
 * `builtin_cn_domestic` additionally covers mainland-China ad networks, which
 * the remote manifest does not list at all.
 *
 * Provenance and licences are documented in
 * `assets/README-builtin-rules.txt`.
 */
internal object BuiltinRuleSource {

    /** Where the engine expects its binaries — see [FilterListRepository]. */
    private const val FILTER_DIR = "remote_filters"

    /**
     * One bundled rule set: the asset base name plus how many rules it holds.
     *
     * The count is recorded at compile time because the trie/bloom format is
     * intentionally opaque — there is no cheap way to count entries in it — and
     * the number is shown to the user as part of the total rule count.
     */
    private data class BundledRules(val asset: String, val ruleCount: Int)

    /**
     * Each set needs `<asset>.trie` **and** `<asset>.bloom`; a set missing
     * either half is ignored so the engine can never be handed a mismatched
     * pair.
     */
    private val RULE_SETS = listOf(
        BundledRules("builtin_adguard_dns", 178_283),
        BundledRules("builtin_adguard_mobile", 4_442),
        BundledRules("builtin_yoyo_adservers", 3_559),
        // Mainland-China ad networks. The remote manifest carries no CN list at
        // all (its APAC entries are ABPVN/HostsVN, which are Vietnamese), so
        // domestic ad domains used to be structurally unblockable no matter how
        // many global lists were enabled.
        BundledRules("builtin_cn_domestic", 32_864),
    )

    /**
     * Bumped whenever the bundled binaries are replaced. The files are only
     * re-extracted when this differs from what was written last time, so an app
     * update which ships new rules cannot silently keep serving the old ones.
     *
     * Revision 2 added `builtin_cn_domestic` and therefore has to force a
     * re-extract on installs that still carry revision 1.
     */
    private const val REVISION = 2
    private const val STAMP_FILE = ".builtin_rules_rev"

    // Headers written by tunnel/internal/trie and tunnel/internal/bloom. They
    // are validated before use so a binary that no longer matches the native
    // engine (e.g. after an engine upgrade) is ignored instead of being handed
    // to `setTries` — where a failed mmap would silently disable filtering.
    private const val TRIE_MAGIC = 0x54524945 // "TRIE"
    private const val TRIE_VERSION = 2
    private const val BLOOM_MAGIC = 0x424C4F4D // "BLOM"
    private const val BLOOM_VERSION = 1

    private val lock = Any()

    @Volatile
    private var trieCsv: String = ""

    @Volatile
    private var bloomCsv: String = ""

    @Volatile
    private var rules: Int = 0

    /** CSV of the bundled `.trie` paths, or `""` when none could be prepared. */
    fun triePaths(): String = trieCsv

    /** CSV of the bundled `.bloom` paths, or `""` when none could be prepared. */
    fun bloomPaths(): String = bloomCsv

    /** Total rules contributed by the bundled sets that are ready right now. */
    fun ruleCount(): Int = rules

    /**
     * Copies the bundled rule sets into `filesDir/remote_filters` and validates
     * their headers. Cheap after the first successful run; safe to call from any
     * thread; never throws.
     *
     * @return the number of bundled rule sets that are ready to use.
     */
    fun prepare(context: Context): Int = synchronized(lock) {
        val dir = File(context.filesDir, FILTER_DIR)
        val ready = mutableListOf<BundledRules>()
        try {
            if (!dir.exists()) dir.mkdirs()

            val stamp = File(dir, STAMP_FILE)
            val needsReinstall = stamp.takeIf { it.exists() }?.readText()?.trim() != REVISION.toString()

            for (set in RULE_SETS) {
                val name = set.asset
                val trie = File(dir, "$name.trie")
                val bloom = File(dir, "$name.bloom")
                try {
                    if (needsReinstall || !trie.isUsable() || !bloom.isUsable()) {
                        installAsset(context, "$name.trie", trie)
                        installAsset(context, "$name.bloom", bloom)
                    }

                    if (trie.isUsable() && bloom.isUsable() &&
                        hasHeader(trie, TRIE_MAGIC, TRIE_VERSION) &&
                        hasHeader(bloom, BLOOM_MAGIC, BLOOM_VERSION)
                    ) {
                        ready += set
                    } else {
                        Timber.w("Bundled rule set $name is unusable or has an incompatible format")
                        trie.delete()
                        bloom.delete()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to install bundled rule set $name")
                }
            }

            if (ready.isNotEmpty()) {
                stamp.writeText(REVISION.toString())
            }

            trieCsv = ready.joinToString(",") { File(dir, "${it.asset}.trie").absolutePath }
            bloomCsv = ready.joinToString(",") { File(dir, "${it.asset}.bloom").absolutePath }
            rules = ready.sumOf { it.ruleCount }

            if (ready.isNotEmpty()) {
                Timber.i(
                    "Bundled rule sets ready: ${ready.joinToString(", ") { it.asset }} " +
                        "($rules rules)"
                )
            }
        } catch (e: Exception) {
            trieCsv = ""
            bloomCsv = ""
            rules = 0
            Timber.w(e, "Failed to prepare bundled rule sets")
        }
        ready.size
    }

    private fun File.isUsable(): Boolean = exists() && length() > 0L

    /**
     * Streams one asset to [target] through a `.tmp` sibling, so an interrupted
     * copy can never leave a half-written rule binary behind.
     */
    private fun installAsset(context: Context, assetName: String, target: File) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) {
                    throw IllegalStateException("Could not move ${temp.name} into place")
                }
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    /** Compares the big-endian magic + version header written by the Go builders. */
    private fun hasHeader(file: File, magic: Int, version: Int): Boolean = try {
        if (file.length() < 8L) {
            false
        } else {
            file.inputStream().use { input ->
                val header = ByteArray(8)
                var read = 0
                while (read < 8) {
                    val n = input.read(header, read, 8 - read)
                    if (n <= 0) break
                    read += n
                }
                read == 8 && header.toIntBe(0) == magic && header.toIntBe(4) == version
            }
        }
    } catch (e: Exception) {
        false
    }

    private fun ByteArray.toIntBe(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
}
