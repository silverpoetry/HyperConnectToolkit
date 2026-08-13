package com.weichao.hyperconnecttoolkit.diagnostics

/** Final defense-in-depth pass over exported text. Injected diagnostics already hash identifiers. */
object DiagnosticRedactor {
    private val email = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val macAddress = Regex("(?i)(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")
    private val ipv4 = Regex("(?<![0-9.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9.])")
    private val uuid = Regex(
        "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b",
    )
    private val namedIdentifier = Regex(
        "(?i)\\b(deviceId|hostId|remoteDeviceId|accountName|accountId|ssid)" +
            "(\\s*[=:]\\s*)([^\\s,;|]+)",
    )
    private val legacyHostBinding = Regex("(?i)(Host bind (?:completed|failed):)\\s*[^\\s,;|]+")

    fun redact(source: String): String = source
        .replace(email, "<email>")
        .replace(macAddress, "<mac>")
        .replace(ipv4, "<ip>")
        .replace(uuid, "<uuid>")
        .replace(namedIdentifier) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}<redacted>"
        }
        .replace(legacyHostBinding) { match ->
            "${match.groupValues[1]} <redacted>"
        }
}
