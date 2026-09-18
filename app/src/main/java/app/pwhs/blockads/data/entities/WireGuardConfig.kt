package app.pwhs.blockads.data.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Represents a parsed WireGuard configuration file.
 * Strictly separates the [Interface] and [Peer] sections.
 *
 * Serializable to JSON for persistence in DataStore and passing to Go engine.
 */
@Serializable
data class WireGuardConfig(
    val interfaceConfig: WireGuardInterface,
    val peers: List<WireGuardPeer>
) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonStr: String): WireGuardConfig =
            json.decodeFromString<WireGuardConfig>(jsonStr)
    }
}

/**
 * Represents the [Interface] section of a WireGuard .conf file.
 */
@Serializable
data class WireGuardInterface(
    val privateKey: String,
    val address: List<String>,
    val listenPort: Int? = null,
    val dns: List<String> = emptyList()
)

/**
 * Represents a [Peer] section of a WireGuard .conf file.
 * A config may contain multiple peers.
 */
@Serializable
data class WireGuardPeer(
    val publicKey: String,
    val presharedKey: String? = null,
    val endpoint: String? = null,
    @SerialName("allowedIPs")
    val allowedIPs: List<String> = emptyList(),
    val persistentKeepalive: Int? = null
)
