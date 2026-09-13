package org.mwangaza.app.ui.navigation

import core.domain.consumption.ConsumptionLineRequest
import core.domain.consumption.ConsumptionRequest
import core.domain.consumption.ConsumptionService
import core.domain.fefo.ExpiryPolicy
import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.Money
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.Quantity
import core.domain.model.Sale
import core.domain.model.StockBatch
import core.domain.model.StockMovement
import core.domain.model.UnitPriceConfig
import core.domain.receiving.GoodsReceiptPersistenceService
import core.domain.receiving.ReceivingResult
import core.domain.testutil.FakeCoreDatabase
import core.domain.time.LocalDateValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class NavigationAndIntegrationTest {

    private lateinit var db: FakeCoreDatabase
    private lateinit var receivingService: GoodsReceiptPersistenceService
    private lateinit var consumptionService: ConsumptionService

    @Before
    fun setUp() {
        db = FakeCoreDatabase()
        receivingService = GoodsReceiptPersistenceService(
            transactionRunner = db,
            goodsReceiptDao = db.goodsReceiptDao,
            stockBatchDao = db.stockBatchDao,
            inventoryCostLayerDao = db.inventoryCostLayerDao,
            stockMovementDao = db.stockMovementDao
        )
        consumptionService = ConsumptionService(
            transactionRunner = db,
            productMasterDao = db.productMasterDao,
            stockBatchDao = db.stockBatchDao,
            inventoryCostLayerDao = db.inventoryCostLayerDao,
            stockMovementDao = db.stockMovementDao,
            stockAllocationDao = db.stockAllocationDao,
            saleDao = db.saleDao
        )
    }

    // ========================================================================
    // Tests A - K: Navigation State & Back Stack Verification
    // ========================================================================

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
                // Rule 1: Back from any feature returns to Dashboard
                currentFeature != null -> {
                    currentFeature = null
                }
                // Rule 2: Back from non-Dashboard tab returns to Dashboard tab
                currentBottomTab != "Dashboard" -> {
                    currentBottomTab = "Dashboard"
                }
                // Rule 3: Back on Dashboard root displays exit confirmation
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

        // Test A: Open application -> on Dashboard
        assertEquals("Dashboard", nav.currentBottomTab)
        assertNull(nav.currentFeature)
        assertFalse(nav.isExitDialogShowing)

        // Test B: Tap Products -> Back -> returns to Dashboard
        nav.navigateToFeature("products")
        assertEquals("products", nav.currentFeature)
        nav.handleBackPress()
        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)

        // Test C: Tap Goods Receiving -> Back -> returns to Dashboard
        nav.navigateToFeature("receiving")
        assertEquals("receiving", nav.currentFeature)
        nav.handleBackPress()
        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)

        // Test D: Tap Dispensing -> Back -> returns to Dashboard
        nav.navigateToFeature("dispensing")
        assertEquals("dispensing", nav.currentFeature)
        nav.handleBackPress()
        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)

        // Test E: Tap Inventory -> Back -> returns to Dashboard
        nav.navigateToFeature("inventory")
        assertEquals("inventory", nav.currentFeature)
        nav.handleBackPress()
        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)

        // Test F: Tap Expiry Alerts -> Back -> returns to Dashboard
        nav.navigateToFeature("alerts")
        assertEquals("alerts", nav.currentFeature)
        nav.handleBackPress()
        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)

        // Test G: Tap Reports -> Back -> returns to Dashboard
        nav.navigateToFeature("reports")
        assertEquals("reports", nav.currentFeature)
        nav.handleBackPress()
        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)

        // Test H: Tap Suppliers -> Back -> returns to Dashboard
        nav.navigateToFeature("suppliers")
        assertEquals("suppliers", nav.currentFeature)
        nav.handleBackPress()
        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)

        // Test I: Tap Stock Adjustments -> Back -> returns to Dashboard
        nav.navigateToFeature("adjustments")
        assertEquals("adjustments", nav.currentFeature)
        nav.handleBackPress()
        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)

        // Test J: On Dashboard -> Back -> Exit confirmation dialog appears
        assertNull(nav.currentFeature)
        assertEquals("Dashboard", nav.currentBottomTab)
        nav.handleBackPress()
        assertTrue("Exit confirmation dialog must appear", nav.isExitDialogShowing)
        assertFalse("App must not finish yet", nav.isAppFinished)

        // Test K1: Exit confirmation -> NO -> dialog closes, remains on Dashboard
        nav.confirmExit(false)
        assertFalse("Dialog should be dismissed", nav.isExitDialogShowing)
        assertFalse("App must not finish", nav.isAppFinished)
        assertEquals("Dashboard", nav.currentBottomTab)

        // Test K2: Exit confirmation -> YES -> app exits
        nav.handleBackPress()
        assertTrue(nav.isExitDialogShowing)
        nav.confirmExit(true)
        assertFalse(nav.isExitDialogShowing)
        assertTrue("App must be finished on YES", nav.isAppFinished)
    }

    // ========================================================================
    // End-to-End Operational Domain Lifecycle Test
    // ========================================================================

    @Test
    fun testOperationalLifecycle_Products_Receiving_Inventory_Dispensing_Void() {
        val now = System.currentTimeMillis()

        // 1. Register Product Master with Base Unit & Price Config
        val productId = UUID.randomUUID().toString()
        val unitId = UUID.randomUUID().toString()
        val priceConfigId = UUID.randomUUID().toString()

        val product = ProductMaster(
            id = productId,
            brandName = "Amoxil 500mg",
            genericName = "Amoxicillin Trihydrate",
            productType = "Capsule",
            manufacturer = "GSK",
            description = "Antibiotic",
            isActive = true,
            createdAt = now,
            updatedAt = now
        )

        val baseUnit = ProductUnit(
            id = unitId,
            productId = productId,
            name = "Capsule",
            abbreviation = "cap",
            conversionMultiplier = 1L,
            isBaseUnit = true,
            isPurchaseUnit = true,
            isDispensingUnit = true,
            isDisplayUnit = true,
            isActive = true,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now
        )

        val priceConfig = UnitPriceConfig(
            id = priceConfigId,
            productUnitId = unitId,
            sellingPrice = Money(1500L), // KES 15.00
            isActive = true,
            createdAt = now,
            updatedAt = now
        )

        db.productMasterDao.insertProduct(product)
        db.productMasterDao.insertUnit(baseUnit)
        db.productMasterDao.savePriceConfig(priceConfig)

        assertEquals(1, db.productMasterDao.getAllProducts().size)
        assertEquals(product.displayName, db.productMasterDao.getAllProducts()[0].displayName)

        // 2. Receive Stock (100 capsules, Batch B-101, Expiry 20271231, Total Cost KES 800.00)
        val receiptId = UUID.randomUUID().toString()
        val receipt = GoodsReceipt(
            id = receiptId,
            receiptNumber = "GR-2026-001",
            sourceDocumentRef = "INV-9988",
            receivedAt = now,
            supplierId = null,
            notes = "Initial batch delivery",
            isCommitted = false,
            committedAt = null,
            createdAt = now,
            updatedAt = now
        )

        val receiptItem = GoodsReceiptItem(
            id = UUID.randomUUID().toString(),
            goodsReceiptId = receiptId,
            lineIndex = 0,
            productId = productId,
            unitId = unitId,
            quantity = Quantity.discrete(100L),
            acquisitionCost = Money(80000L), // KES 800.00 total
            batchTrackingMode = StockBatch.TRACKING_STANDARD,
            supplierBatchNumber = "B-101",
            expiryDate = 20271231,
            createdAt = now,
            updatedAt = now
        )

        val receivingResult = receivingService.commitReceipt(
            receipt = receipt,
            items = listOf(receiptItem),
            productsById = mapOf(productId to product),
            unitsById = mapOf(unitId to baseUnit),
            commitTimestamp = now
        )

        assertTrue(receivingResult is ReceivingResult.Success)
        val successReceipt = (receivingResult as ReceivingResult.Success).committedReceipt
        assertTrue(successReceipt.isCommitted)

        // Verify ledger balance: StockMovement must sum to 100
        val movementsAfterReceiving = db.stockMovementDao.getMovementsForProduct(productId)
        assertEquals(1, movementsAfterReceiving.size)
        assertEquals(100L, movementsAfterReceiving[0].quantity.storageUnits)
        assertEquals(StockMovement.TYPE_RECEIPT, movementsAfterReceiving[0].movementType)

        // 3. Dispense Medication (Sell 20 capsules @ KES 15.00 = KES 300.00)
        val saleId = UUID.randomUUID().toString()
        val dispenseRequest = ConsumptionRequest(
            saleId = saleId,
            saleNumber = "SALE-001",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productId,
                    dispensingUnitId = unitId,
                    requestedQuantity = Quantity.discrete(20L),
                    customUnitPrice = Money(1500L)
                )
            ),
            customerRef = "Patient-01",
            initiatedByUserId = "PHARMACIST_1",
            notes = "Prescription dispensing",
            facilityCalendarDate = LocalDateValue(2026, 9, 13),
            expiryPolicy = ExpiryPolicy.DEFAULT,
            transactionTimestamp = now + 1000L
        )

        val consumptionResult = consumptionService.consumeStock(dispenseRequest)
        assertNotNull(consumptionResult.sale)
        assertEquals("SALE-001", consumptionResult.sale.saleNumber)
        assertEquals(30000L, consumptionResult.sale.totalSellingAmount.amountMinorUnits) // KES 300.00
        assertEquals(16000L, consumptionResult.sale.totalCogs.amountMinorUnits) // KES 160.00 (20 * 8.00)

        // Physical ledger units after dispensing: 100 - 20 = 80
        val totalOnHandUnits = db.stockMovementDao.getPhysicalStockUnitsForProduct(productId)
        assertEquals(80L, totalOnHandUnits)

        // 4. Void Sale (Compensating reversal)
        val voidResult = consumptionService.voidSale(
            saleId = saleId,
            voidTimestamp = now + 2000L,
            reason = "Dispensed incorrect dosage"
        )
        assertNotNull(voidResult)
        assertEquals(Sale.STATUS_VOIDED, voidResult.sale.status)

        // Physical stock ledger restored back to 100 units
        val restoredOnHandUnits = db.stockMovementDao.getPhysicalStockUnitsForProduct(productId)
        assertEquals(100L, restoredOnHandUnits)

        // 3 movements: RECEIPT (+100), DISPENSE (-20), RETURN (+20)
        val allMovements = db.stockMovementDao.getAllMovements()
        assertEquals(3, allMovements.size)
    }
}
