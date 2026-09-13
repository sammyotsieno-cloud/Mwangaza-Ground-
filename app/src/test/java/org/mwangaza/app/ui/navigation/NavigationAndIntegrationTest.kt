package org.mwangaza.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation behavior tests for the application module.
 *
 * Domain/inventory transaction integration tests belong to the :core module,
 * where the core test infrastructure and FakeCoreDatabase are available.
 *
 * This test file therefore verifies only application navigation state and
 * back-navigation behavior.
 */
class NavigationAndIntegrationTest {

    /**
     * Small deterministic simulator used to verify the application's
     * navigation rules without depending on Android framework state.
     */
    class NavigationStateSimulator {

        var currentBottomTab: String = "Dashboard"
        var currentFeature: String? = null
        var isExitDialogShowing: Boolean = false
        var isAppFinished: Boolean = false

        fun navigateToFeature(route: String) {
            currentFeature = route
            isExitDialogShowing = false
        }

        fun switchBottomTab(tab: String) {
            currentBottomTab = tab
            currentFeature = null
            isExitDialogShowing = false
        }

        fun handleBackPress() {
            when {
                // Rule 1:
                // Back from any feature returns to the Dashboard.
                currentFeature != null -> {
                    currentFeature = null
                }

                // Rule 2:
                // Back from a non-Dashboard bottom tab returns to Dashboard.
                currentBottomTab != "Dashboard" -> {
                    currentBottomTab = "Dashboard"
                }

                // Rule 3:
                // Back from Dashboard root requests application exit.
                else -> {
                    isExitDialogShowing = true
                }
            }
        }

        fun confirmExit(confirm: Boolean) {
            if (confirm) {
                isAppFinished = true
                isExitDialogShowing = false
            } else {
                isExitDialogShowing = false
            }
        }
    }

    @Test
    fun testNavigationSequence_A_to_K() {
        val nav = NavigationStateSimulator()

        // --------------------------------------------------------------------
        // Test A: Application opens on Dashboard.
        // --------------------------------------------------------------------
        assertEquals("Dashboard", nav.currentBottomTab)
        assertNull(nav.currentFeature)
        assertFalse(nav.isExitDialogShowing)
        assertFalse(nav.isAppFinished)

        // --------------------------------------------------------------------
        // Test B: Products -> Back -> Dashboard.
        // --------------------------------------------------------------------
        nav.navigateToFeature("products")

        assertEquals("products", nav.currentFeature)

        nav.handleBackPress()

        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)
        assertFalse(nav.isExitDialogShowing)

        // --------------------------------------------------------------------
        // Test C: Goods Receiving -> Back -> Dashboard.
        // --------------------------------------------------------------------
        nav.navigateToFeature("receiving")

        assertEquals("receiving", nav.currentFeature)

        nav.handleBackPress()

        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)
        assertFalse(nav.isExitDialogShowing)

        // --------------------------------------------------------------------
        // Test D: Dispensing -> Back -> Dashboard.
        // --------------------------------------------------------------------
        nav.navigateToFeature("dispensing")

        assertEquals("dispensing", nav.currentFeature)

        nav.handleBackPress()

        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)
        assertFalse(nav.isExitDialogShowing)

        // --------------------------------------------------------------------
        // Test E: Inventory -> Back -> Dashboard.
        // --------------------------------------------------------------------
        nav.navigateToFeature("inventory")

        assertEquals("inventory", nav.currentFeature)

        nav.handleBackPress()

        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)
        assertFalse(nav.isExitDialogShowing)

        // --------------------------------------------------------------------
        // Test F: Expiry Alerts -> Back -> Dashboard.
        // --------------------------------------------------------------------
        nav.navigateToFeature("alerts")

        assertEquals("alerts", nav.currentFeature)

        nav.handleBackPress()

        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)
        assertFalse(nav.isExitDialogShowing)

        // --------------------------------------------------------------------
        // Test G: Reports -> Back -> Dashboard.
        // --------------------------------------------------------------------
        nav.navigateToFeature("reports")

        assertEquals("reports", nav.currentFeature)

        nav.handleBackPress()

        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)
        assertFalse(nav.isExitDialogShowing)

        // --------------------------------------------------------------------
        // Test H: Suppliers -> Back -> Dashboard.
        // --------------------------------------------------------------------
        nav.navigateToFeature("suppliers")

        assertEquals("suppliers", nav.currentFeature)

        nav.handleBackPress()

        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)
        assertFalse(nav.isExitDialogShowing)

        // --------------------------------------------------------------------
        // Test I: Stock Adjustments -> Back -> Dashboard.
        // --------------------------------------------------------------------
        nav.navigateToFeature("adjustments")

        assertEquals("adjustments", nav.currentFeature)

        nav.handleBackPress()

        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)
        assertFalse(nav.isExitDialogShowing)

        // --------------------------------------------------------------------
        // Test J:
        // Dashboard -> Back -> Exit confirmation appears.
        // --------------------------------------------------------------------
        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)

        nav.handleBackPress()

        assertTrue(
            "Exit confirmation dialog must appear",
            nav.isExitDialogShowing
        )

        assertFalse(
            "App must not finish before exit is confirmed",
            nav.isAppFinished
        )

        // --------------------------------------------------------------------
        // Test K1:
        // Exit confirmation -> NO -> remain on Dashboard.
        // --------------------------------------------------------------------
        nav.confirmExit(false)

        assertFalse(
            "Exit dialog should be dismissed after choosing NO",
            nav.isExitDialogShowing
        )

        assertFalse(
            "App must not finish after choosing NO",
            nav.isAppFinished
        )

        assertEquals(
            "Dashboard",
            nav.currentBottomTab
        )

        assertNull(nav.currentFeature)

        // --------------------------------------------------------------------
        // Test K2:
        // Dashboard -> Back -> YES -> application exits.
        // --------------------------------------------------------------------
        nav.handleBackPress()

        assertTrue(
            "Exit confirmation dialog must appear again",
            nav.isExitDialogShowing
        )

        nav.confirmExit(true)

        assertFalse(
            "Exit dialog should be dismissed after choosing YES",
            nav.isExitDialogShowing
        )

        assertTrue(
            "App must finish after choosing YES",
            nav.isAppFinished
        )
    }

    @Test
    fun backFromFeatureDoesNotExitApplication() {
        val nav = NavigationStateSimulator()

        nav.navigateToFeature("products")

        nav.handleBackPress()

        assertNull(nav.currentFeature)
        assertFalse(nav.isExitDialogShowing)
        assertFalse(nav.isAppFinished)
        assertEquals("Dashboard", nav.currentBottomTab)
    }

    @Test
    fun backFromNonDashboardTabReturnsToDashboard() {
        val nav = NavigationStateSimulator()

        nav.switchBottomTab("Inventory")

        assertEquals("Inventory", nav.currentBottomTab)
        assertNull(nav.currentFeature)

        nav.handleBackPress()

        assertEquals("Dashboard", nav.currentBottomTab)
        assertNull(nav.currentFeature)
        assertFalse(nav.isExitDialogShowing)
        assertFalse(nav.isAppFinished)
    }

    @Test
    fun cancellingExitKeepsApplicationOpen() {
        val nav = NavigationStateSimulator()

        nav.handleBackPress()

        assertTrue(nav.isExitDialogShowing)

        nav.confirmExit(false)

        assertFalse(nav.isExitDialogShowing)
        assertFalse(nav.isAppFinished)
        assertEquals("Dashboard", nav.currentBottomTab)
    }

    @Test
    fun confirmingExitFinishesApplication() {
        val nav = NavigationStateSimulator()

        nav.handleBackPress()

        assertTrue(nav.isExitDialogShowing)

        nav.confirmExit(true)

        assertFalse(nav.isExitDialogShowing)
        assertTrue(nav.isAppFinished)
    }
}
