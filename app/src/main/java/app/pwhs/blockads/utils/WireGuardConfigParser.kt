package app.pwhs.blockads.utils

import app.pwhs.blockads.data.entities.WireGuardConfig
import app.pwhs.blockads.data.entities.WireGuardInterface
import app.pwhs.blockads.data.entities.WireGuardPeer

/**
 * Robust parser for standard WireGuard .conf files.
 *
 * Handles:
 * - [Interface] and multiple [Peer] sections
 * - Comments (lines starting with #)
 * - Empty / blank lines
 * - Whitespace around keys and values
 */
object WireGuardConfigParser {

    private enum class Section { NONE, INTERFACE, PEER }

    private val sectionRegex = Regex("""^\[(\w+)]$""", RegexOption.IGNORE_CASE)

    /**
     * Parse the raw text content of a WireGuard .conf file.
     *
     * @throws IllegalArgumentException if required fields are missing.
     */
    fun parse(raw: String): WireGuardConfig {
        var currentSection = Section.NONE

        // Interface fields
        var privateKey: String? = null
        val addresses = mutableListOf<String>()
        var listenPort: Int? = null
        val dns = mutableListOf<String>()

        // Peer accumulator
        data class PeerBuilder(
            var publicKey: String? = null,
            var presharedKey: String? = null,
            var endpoint: String? = null,
            val allowedIPs: MutableList<String> = mutableListOf(),
            var persistentKeepalive: Int? = null,
        )

        val peers = mutableListOf<PeerBuilder>()
        var currentPeer: PeerBuilder? = null

        for (line in raw.lines()) {
            val trimmed = line.trim()

            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            // Check for section headers
            val sectionMatch = sectionRegex.find(trimmed)
            if (sectionMatch != null) {
                val sectionName = sectionMatch.groupValues[1]
                when (sectionName.lowercase()) {
                    "interface" -> {
                        currentSection = Section.INTERFACE
                    }
                    "peer" -> {
                        // Finalize previous peer if any
                        currentPeer?.let { peers.add(it) }
                        currentPeer = PeerBuilder()
                        currentSection = Section.PEER
                    }
                    else -> {
                        // Unknown section, skip
                        currentSection = Section.NONE
                    }
                }
                continue
            }

            // Parse key = value
            val eqIndex = trimmed.indexOf('=')
            if (eqIndex == -1) continue

            val key = trimmed.substring(0, eqIndex).trim()
            val value = trimmed.substring(eqIndex + 1).trim()

            when (currentSection) {
                Section.INTERFACE -> {
                    when (key.lowercase()) {
                        "privatekey" -> privateKey = value
                        "address" -> addresses.addAll(
                            value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                        "listenport" -> listenPort = value.toIntOrNull()
                        "dns" -> dns.addAll(
                            value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                    }
                }
                Section.PEER -> {
                    val peer = currentPeer ?: continue
                    when (key.lowercase()) {
                        "publickey" -> peer.publicKey = value
                        "presharedkey" -> peer.presharedKey = value
                        "endpoint" -> peer.endpoint = value
                        "allowedips" -> peer.allowedIPs.addAll(
                            value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                        "persistentkeepalive" -> peer.persistentKeepalive = value.toIntOrNull()
                    }
                }
                Section.NONE -> { /* ignore lines outside any section */ }
            }
        }

        // Finalize last peer
        currentPeer?.let { peers.add(it) }

        // Validate required fields
        requireNotNull(privateKey) { "Missing required field: PrivateKey in [Interface]" }
        require(addresses.isNotEmpty()) { "Missing required field: Address in [Interface]" }

        val parsedPeers = peers.map { pb ->
            requireNotNull(pb.publicKey) { "Missing required field: PublicKey in [Peer]" }
            WireGuardPeer(
                publicKey = pb.publicKey!!,
                presharedKey = pb.presharedKey,
                endpoint = pb.endpoint,
                allowedIPs = pb.allowedIPs.toList(),
                persistentKeepalive = pb.persistentKeepalive
            )
        }

        return WireGuardConfig(
            interfaceConfig = WireGuardInterface(
                privateKey = privateKey,
                address = addresses.toList(),
                listenPort = listenPort,
                dns = dns.toList()
            ),
            peers = parsedPeers
        )
    }
}
