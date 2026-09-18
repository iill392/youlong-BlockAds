package app.pwhs.blockads.data.entities

import kotlinx.serialization.Serializable

/**
 * Serializable data class for JSON import/export of custom DNS rules.
 * This is a simplified representation without Room-specific fields (id, addedTimestamp).
 */
@Serializable
data class CustomDnsRuleExport(
    val rule: String,
    val ruleType: String, // "BLOCK", "ALLOW", "COMMENT"
    val domain: String,
    val isEnabled: Boolean
)

/**
 * Convert a Room entity to an exportable data class.
 */
fun CustomDnsRule.toExport(): CustomDnsRuleExport = CustomDnsRuleExport(
    rule = rule,
    ruleType = ruleType.name,
    domain = domain,
    isEnabled = isEnabled
)

/**
 * Convert an exported data class back to a Room entity (new entry, id=0).
 */
fun CustomDnsRuleExport.toEntity(): CustomDnsRule = CustomDnsRule(
    id = 0,
    rule = rule,
    ruleType = try {
        RuleType.valueOf(ruleType)
    } catch (_: IllegalArgumentException) {
        RuleType.BLOCK
    },
    domain = domain,
    isEnabled = isEnabled
)
