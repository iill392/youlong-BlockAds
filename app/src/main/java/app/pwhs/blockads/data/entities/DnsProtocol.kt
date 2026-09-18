package app.pwhs.blockads.data.entities

enum class DnsProtocol {
    PLAIN,  // Traditional UDP DNS on port 53
    DOH,    // DNS-over-HTTPS (RFC 8484)
    DOT,    // DNS-over-TLS (RFC 7858)
    DOQ     // DNS-over-QUIC / DoH3 (RFC 9250, uses HTTP/3 over QUIC transport)
}