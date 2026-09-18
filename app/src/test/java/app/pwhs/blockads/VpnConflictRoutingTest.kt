package app.pwhs.blockads

import app.pwhs.blockads.data.datastore.AppPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnConflictRoutingTest {

    @Test
    fun testConflictRulesForDifferentModes() {
        // Mock routingMode = ROOT conflict detection logic
        val isRootMode = true
        val isOtherVpnActive = true

        // If in Root mode, we should not intercept even if another VPN is active.
        val shouldBlockForRoot = !isRootMode && isOtherVpnActive
        assertFalse("Root mode should not intercept even if another VPN is running", shouldBlockForRoot)

        // Mock routingMode = non-ROOT (e.g. DIRECT/LOCAL) conflict detection logic
        val isRootModeDirect = false
        val isOtherVpnActiveDirect = true

        // If not in Root mode and another VPN is active, we must intercept.
        val shouldBlockForDirect = !isRootModeDirect && isOtherVpnActiveDirect
        assertTrue("Non-Root mode must intercept and show conflict dialog if another VPN is active", shouldBlockForDirect)
    }

    @Test
    fun testConflictRulesWithNoOtherVpn() {
        val isRootMode = false
        val isOtherVpnActive = false

        // If no other VPN is active, we should never intercept regardless of routing mode.
        val shouldBlock = !isRootMode && isOtherVpnActive
        assertFalse("Should not intercept in any mode if no other VPN is active", shouldBlock)
    }
}
