package core.domain

import core.domain.allocation.CostLayerAllocationService
import core.domain.consumption.ConsumptionLineRequest
import core.domain.consumption.ConsumptionRequest
import core.domain.consumption.ConsumptionService
import core.domain.consumption.InsufficientStockException
import core.domain.fefo.ExpiryPolicy
import core.domain.fefo.FefoCandidateAllocation
import core.domain.fefo.FefoService
import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.InventoryCostLayer
import core.domain.model.Money
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.Quantity
import core.domain.model.QuantityScale
import core.domain.model.Sale
import core.domain.model.StockBatch
import core.domain.model.StockMovement
import core.domain.model.UnitPriceConfig
import core.domain.receiving.GoodsReceiptPersistenceService
import core.domain.testutil.FakeCoreDatabase
import core.domain.time.LocalDateValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class TransactionChainTest {

    private lateinit var db: FakeCoreDatabase
    private lateinit var receivingService: GoodsReceiptPersistenceService
    private lateinit var consumptionService: ConsumptionService

    private val testCalendarDate = LocalDateValue(2026, 9, 11)
    private val testTimestamp = 1789113600000L

    private lateinit var productDiscrete: ProductMaster
    private lateinit var unitTablet: ProductUnit
    private lateinit var unitBox100: ProductUnit
    private lateinit var priceConfigBox: UnitPriceConfig

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
            saleDao = db.saleDao,
            productMasterDao = db.productMasterDao,
            stockBatchDao = db.stockBatchDao,
            inventoryCostLayerDao = db.inventoryCostLayerDao,
            stockMovementDao = db.stockMovementDao,
            stockAllocationDao = db.stockAllocationDao
        )

        // Seed product
        productDiscrete = ProductMaster(
            id = "PROD-PARACETAMOL",
            canonicalName = "Paracetamol 500mg",
            category = "Analgesics",
            quantityScale = QuantityScale.SCALE_0,
            hasExpiry = true,
            requiresBatchTracking = true,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        db.productMasterDao.insertProduct(productDiscrete)

        unitTablet = ProductUnit(
            id = "UNIT-TAB",
            productId = productDiscrete.id,
            name = "Tablet",
            conversionMultiplier = 1L,
            isBaseUnit = true,
            isDispensingUnit = true,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        unitBox100 = ProductUnit(
            id = "UNIT-BOX100",
            productId = productDiscrete.id,
            name = "Box of 100",
            conversionMultiplier = 100L,
            isPurchaseUnit = true,
            isDispensingUnit = true,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        db.productMasterDao.insertUnits(listOf(unitTablet, unitBox100))

        priceConfigBox = UnitPriceConfig(
            id = "PRICE-BOX100",
            productUnitId = unitBox100.id,
            sellingPrice = Money.ofMinor(50_000L), // KSh 500.00
            isActive = true,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        db.productMasterDao.insertPriceConfig(priceConfigBox)
    }

    // ==========================================
    // RECEIVING TESTS (1-8)
    // ==========================================

    @Test
    fun test1_validReceiptCommitsAtomically() {
        val receipt = GoodsReceipt(
            id = "REC-001",
            receiptNumber = "GRN-2026-001",
            supplierId = "SUPP-01",
            receivedAt = testTimestamp,
            status = GoodsReceipt.STATUS_DRAFT,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        val item = GoodsReceiptItem(
            id = "ITEM-001",
            goodsReceiptId = receipt.id,
            lineIndex = 0,
            productId = productDiscrete.id,
            receivedUnitId = unitBox100.id,
            receivedQuantity = Quantity.of(10L, QuantityScale.SCALE_0), // 10 boxes = 1000 tablets
            lineTotalCost = Money.ofMinor(300_000L), // KSh 3,000.00
            batchNumber = "BATCH-A",
            expiryDateInt = 20271231,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val bundle = receivingService.commitReceipt(
            receipt = receipt,
            items = listOf(item),
            productsById = mapOf(productDiscrete.id to productDiscrete),
            unitsById = mapOf(unitBox100.id to unitBox100, unitTablet.id to unitTablet),
            commitTimestamp = testTimestamp
        )

        assertTrue(bundle.committedReceipt.isCommitted)
        assertEquals(1, db.batches.size)
        assertEquals(1, db.costLayers.size)
        assertEquals(1, db.movements.size)
        assertEquals(1000L, db.stockMovementDao.getPhysicalStockUnitsForProduct(productDiscrete.id))
    }

    @Test
    fun test2_invalidReceiptCommitsNothing() {
        val receipt = GoodsReceipt(
            id = "REC-002",
            receiptNumber = "GRN-2026-002",
            supplierId = "SUPP-01",
            receivedAt = testTimestamp,
            status = GoodsReceipt.STATUS_DRAFT,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        // Invalid item: negative quantity will fail validation
        try {
            val item = GoodsReceiptItem(
                id = "ITEM-002",
                goodsReceiptId = receipt.id,
                lineIndex = 0,
                productId = productDiscrete.id,
                receivedUnitId = unitBox100.id,
                receivedQuantity = Quantity.of(-5L, QuantityScale.SCALE_0),
                lineTotalCost = Money.ofMinor(100_000L),
                batchNumber = "BATCH-FAIL",
                expiryDateInt = 20271231,
                createdAt = testTimestamp,
                updatedAt = testTimestamp
            )
            receivingService.commitReceipt(
                receipt = receipt,
                items = listOf(item),
                productsById = mapOf(productDiscrete.id to productDiscrete),
                unitsById = mapOf(unitBox100.id to unitBox100),
                commitTimestamp = testTimestamp
            )
            fail("Expected exception for invalid negative quantity")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Verify zero commits in database
        assertEquals(0, db.receipts.size)
        assertEquals(0, db.batches.size)
        assertEquals(0, db.costLayers.size)
        assertEquals(0, db.movements.size)
    }

    @Test
    fun test3_duplicateReceiptCannotBeCommittedTwice() {
        val receipt = GoodsReceipt(
            id = "REC-003",
            receiptNumber = "GRN-2026-003",
            supplierId = "SUPP-01",
            receivedAt = testTimestamp,
            status = GoodsReceipt.STATUS_DRAFT,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        val item = GoodsReceiptItem(
            id = "ITEM-003",
            goodsReceiptId = receipt.id,
            lineIndex = 0,
            productId = productDiscrete.id,
            receivedUnitId = unitBox100.id,
            receivedQuantity = Quantity.of(5L, QuantityScale.SCALE_0),
            lineTotalCost = Money.ofMinor(150_000L),
            batchNumber = "BATCH-DUP",
            expiryDateInt = 20271231,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        receivingService.commitReceipt(
            receipt = receipt,
            items = listOf(item),
            productsById = mapOf(productDiscrete.id to productDiscrete),
            unitsById = mapOf(unitBox100.id to unitBox100, unitTablet.id to unitTablet),
            commitTimestamp = testTimestamp
        )

        // Attempting to commit the same receipt again must fail loudly
        try {
            receivingService.commitReceipt(
                receipt = receipt,
                items = listOf(item),
                productsById = mapOf(productDiscrete.id to productDiscrete),
                unitsById = mapOf(unitBox100.id to unitBox100, unitTablet.id to unitTablet),
                commitTimestamp = testTimestamp
            )
            fail("Expected IllegalStateException on duplicate receipt commit")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("already committed") || e.message!!.contains("Duplicate receiving attempt"))
        }

        // Ledger must have exactly 1 movement, not duplicated
        assertEquals(1, db.movements.size)
        assertEquals(500L, db.stockMovementDao.getPhysicalStockUnitsForProduct(productDiscrete.id))
    }

    @Test
    fun test4_unitConversionIsExact() {
        val receipt = GoodsReceipt(
            id = "REC-004",
            receiptNumber = "GRN-2026-004",
            receivedAt = testTimestamp,
            status = GoodsReceipt.STATUS_DRAFT,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        val item = GoodsReceiptItem(
            id = "ITEM-004",
            goodsReceiptId = receipt.id,
            lineIndex = 0,
            productId = productDiscrete.id,
            receivedUnitId = unitBox100.id,
            receivedQuantity = Quantity.of(12L, QuantityScale.SCALE_0), // 12 boxes * 100 = 1200 tablets
            lineTotalCost = Money.ofMinor(360_000L),
            batchNumber = "BATCH-CONV",
            expiryDateInt = 20271231,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val bundle = receivingService.commitReceipt(
            receipt = receipt,
            items = listOf(item),
            productsById = mapOf(productDiscrete.id to productDiscrete),
            unitsById = mapOf(unitBox100.id to unitBox100, unitTablet.id to unitTablet),
            commitTimestamp = testTimestamp
        )

        assertEquals(1200L, bundle.newMovements.first().quantity.storageUnits)
        assertEquals(1200L, bundle.newCostLayers.first().initialQuantity.storageUnits)
    }

    @Test
    fun test5_batchResolutionIsDeterministic() {
        val receipt1 = GoodsReceipt(
            id = "REC-005A",
            receiptNumber = "GRN-2026-005A",
            receivedAt = testTimestamp,
            status = GoodsReceipt.STATUS_DRAFT,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        val item1 = GoodsReceiptItem(
            id = "ITEM-005A",
            goodsReceiptId = receipt1.id,
            lineIndex = 0,
            productId = productDiscrete.id,
            receivedUnitId = unitBox100.id,
            receivedQuantity = Quantity.of(2L, QuantityScale.SCALE_0),
            lineTotalCost = Money.ofMinor(60_000L),
            batchNumber = "BATCH-SHARED",
            expiryDateInt = 20280101,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        receivingService.commitReceipt(
            receipt = receipt1,
            items = listOf(item1),
            productsById = mapOf(productDiscrete.id to productDiscrete),
            unitsById = mapOf(unitBox100.id to unitBox100, unitTablet.id to unitTablet),
            commitTimestamp = testTimestamp
        )
        assertEquals(1, db.batches.size)
        val originalBatchId = db.batches.values.first().id

        // Second receipt receives the same batch number and expiry date for the same product
        val receipt2 = GoodsReceipt(
            id = "REC-005B",
            receiptNumber = "GRN-2026-005B",
            receivedAt = testTimestamp + 1000L,
            status = GoodsReceipt.STATUS_DRAFT,
            createdAt = testTimestamp + 1000L,
            updatedAt = testTimestamp + 1000L
        )
        val item2 = GoodsReceiptItem(
            id = "ITEM-005B",
            goodsReceiptId = receipt2.id,
            lineIndex = 0,
            productId = productDiscrete.id,
            receivedUnitId = unitBox100.id,
            receivedQuantity = Quantity.of(3L, QuantityScale.SCALE_0),
            lineTotalCost = Money.ofMinor(90_000L),
            batchNumber = "BATCH-SHARED",
            expiryDateInt = 20280101,
            createdAt = testTimestamp + 1000L,
            updatedAt = testTimestamp + 1000L
        )
        val bundle2 = receivingService.commitReceipt(
            receipt = receipt2,
            items = listOf(item2),
            productsById = mapOf(productDiscrete.id to productDiscrete),
            unitsById = mapOf(unitBox100.id to unitBox100, unitTablet.id to unitTablet),
            commitTimestamp = testTimestamp + 1000L
        )

        // No new batch should be created; existing batch must be reused
        assertEquals(0, bundle2.newBatches.size)
        assertEquals(1, db.batches.size)
        assertEquals(originalBatchId, bundle2.newCostLayers.first().stockBatchId)
        assertEquals(500L, db.stockMovementDao.getPhysicalStockUnitsForBatch(originalBatchId))
    }

    @Test
    fun test6_receiptCreatesExpectedCostLayers() {
        val receipt = GoodsReceipt(
            id = "REC-006",
            receiptNumber = "GRN-2026-006",
            supplierId = "SUPP-ALPHA",
            receivedAt = testTimestamp,
            status = GoodsReceipt.STATUS_DRAFT,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        val item = GoodsReceiptItem(
            id = "ITEM-006",
            goodsReceiptId = receipt.id,
            lineIndex = 0,
            productId = productDiscrete.id,
            receivedUnitId = unitBox100.id,
            receivedQuantity = Quantity.of(5L, QuantityScale.SCALE_0), // 500 tablets
            lineTotalCost = Money.ofMinor(150_000L), // 150,000 cents / 500 = 300 cents per tablet
            batchNumber = "BATCH-COST",
            expiryDateInt = 20270630,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val bundle = receivingService.commitReceipt(
            receipt = receipt,
            items = listOf(item),
            productsById = mapOf(productDiscrete.id to productDiscrete),
            unitsById = mapOf(unitBox100.id to unitBox100, unitTablet.id to unitTablet),
            commitTimestamp = testTimestamp
        )

        assertEquals(1, bundle.newCostLayers.size)
        val layer = bundle.newCostLayers.first()
        assertEquals(500L, layer.initialQuantity.storageUnits)
        assertEquals(500L, layer.remainingQuantity.storageUnits)
        assertEquals(300L, layer.acquisitionUnitCost.amountMinorUnits)
        assertEquals("GRN-2026-006", layer.sourceReceiptRef)
        assertEquals("SUPP-ALPHA", layer.supplierId)
    }

    @Test
    fun test7_costLayerMonetaryTotalExactlyEqualsReceiptLineCost() {
        // Test an indivisible amount: 100 minor units across 3 base items
        // 100 / 3 = 33 with remainder 1 -> Tranche 1: 2 items @ 33 = 66, Tranche 2: 1 item @ 34 = 34. Total = 100 exact!
        val receipt = GoodsReceipt(
            id = "REC-007",
            receiptNumber = "GRN-2026-007",
            receivedAt = testTimestamp,
            status = GoodsReceipt.STATUS_DRAFT,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        val item = GoodsReceiptItem(
            id = "ITEM-007",
            goodsReceiptId = receipt.id,
            lineIndex = 0,
            productId = productDiscrete.id,
            receivedUnitId = unitTablet.id,
            receivedQuantity = Quantity.of(3L, QuantityScale.SCALE_0),
            lineTotalCost = Money.ofMinor(100L),
            batchNumber = "BATCH-INDIVISIBLE",
            expiryDateInt = 20270630,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val bundle = receivingService.commitReceipt(
            receipt = receipt,
            items = listOf(item),
            productsById = mapOf(productDiscrete.id to productDiscrete),
            unitsById = mapOf(unitTablet.id to unitTablet),
            commitTimestamp = testTimestamp
        )

        assertEquals(2, bundle.newCostLayers.size)
        val totalCostSum = bundle.newCostLayers.sumOf {
            it.initialQuantity.storageUnits * it.acquisitionUnitCost.amountMinorUnits
        }
        assertEquals(100L, totalCostSum)
    }

    @Test
    fun test8_purchaseMovementQuantityEqualsReceivedBaseQuantity() {
        val receipt = GoodsReceipt(
            id = "REC-008",
            receiptNumber = "GRN-2026-008",
            receivedAt = testTimestamp,
            status = GoodsReceipt.STATUS_DRAFT,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        val item = GoodsReceiptItem(
            id = "ITEM-008",
            goodsReceiptId = receipt.id,
            lineIndex = 0,
            productId = productDiscrete.id,
            receivedUnitId = unitBox100.id,
            receivedQuantity = Quantity.of(4L, QuantityScale.SCALE_0), // 400 base units
            lineTotalCost = Money.ofMinor(120_000L),
            batchNumber = "BATCH-MVT",
            expiryDateInt = 20271231,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val bundle = receivingService.commitReceipt(
            receipt = receipt,
            items = listOf(item),
            productsById = mapOf(productDiscrete.id to productDiscrete),
            unitsById = mapOf(unitBox100.id to unitBox100, unitTablet.id to unitTablet),
            commitTimestamp = testTimestamp
        )

        val movement = bundle.newMovements.first()
        assertEquals(StockMovement.TYPE_PURCHASE_RECEIPT, movement.movementType)
        assertTrue(movement.quantity.isPositive)
        assertEquals(400L, movement.quantity.storageUnits)
    }

    // ==========================================
    // FEFO TESTS (9-13)
    // ==========================================

    @Test
    fun test9_earliestEligibleExpiryIsSelectedFirst() {
        val batchEarly = StockBatch(
            id = "BATCH-EARLY",
            productId = productDiscrete.id,
            batchNumber = "B-EARLY",
            expiryDateInt = 20261001, // Earlier
            createdAt = testTimestamp
        )
        val batchLate = StockBatch(
            id = "BATCH-LATE",
            productId = productDiscrete.id,
            batchNumber = "B-LATE",
            expiryDateInt = 20270501, // Later
            createdAt = testTimestamp
        )

        val candidates = FefoService.evaluateCandidates(
            batchesWithQuantities = listOf(
                batchLate to Quantity.of(50L, QuantityScale.SCALE_0),
                batchEarly to Quantity.of(50L, QuantityScale.SCALE_0)
            ),
            facilityCalendarDate = testCalendarDate
        )

        val plan = FefoService.planAllocation(
            requestedQuantity = Quantity.of(30L, QuantityScale.SCALE_0),
            evaluatedCandidates = candidates
        )

        assertEquals(1, plan.allocations.size)
        assertEquals(batchEarly.id, plan.allocations.first().batch.id)
        assertEquals(30L, plan.allocations.first().allocatedQuantity.storageUnits)
    }

    @Test
    fun test10_expiredStockIsExcluded() {
        val batchExpired = StockBatch(
            id = "BATCH-EXPIRED",
            productId = productDiscrete.id,
            batchNumber = "B-EXPIRED",
            expiryDateInt = 20260901, // Expired relative to 2026-09-11
            createdAt = testTimestamp
        )
        val batchValid = StockBatch(
            id = "BATCH-VALID",
            productId = productDiscrete.id,
            batchNumber = "B-VALID",
            expiryDateInt = 20270101,
            createdAt = testTimestamp
        )

        val candidates = FefoService.evaluateCandidates(
            batchesWithQuantities = listOf(
                batchExpired to Quantity.of(100L, QuantityScale.SCALE_0),
                batchValid to Quantity.of(50L, QuantityScale.SCALE_0)
            ),
            facilityCalendarDate = testCalendarDate,
            policy = ExpiryPolicy.DEFAULT
        )

        val expiredCandidate = candidates.first { it.batch.id == batchExpired.id }
        assertFalse(expiredCandidate.isEligible)

        val plan = FefoService.planAllocation(
            requestedQuantity = Quantity.of(40L, QuantityScale.SCALE_0),
            evaluatedCandidates = candidates
        )

        assertEquals(1, plan.allocations.size)
        assertEquals(batchValid.id, plan.allocations.first().batch.id)
    }

    @Test
    fun test11_sameExpiryBatchesUseDeterministicTieBreaking() {
        // Batches with exact same expiry date: tie-break by createdAt ASC, then batchNumber ASC
        val batchA = StockBatch(
            id = "BATCH-TIE-A",
            productId = productDiscrete.id,
            batchNumber = "LOT-100",
            expiryDateInt = 20270101,
            createdAt = testTimestamp
        )
        val batchB = StockBatch(
            id = "BATCH-TIE-B",
            productId = productDiscrete.id,
            batchNumber = "LOT-200",
            expiryDateInt = 20270101,
            createdAt = testTimestamp + 1000L // Registered later
        )

        val candidates = FefoService.evaluateCandidates(
            batchesWithQuantities = listOf(
                batchB to Quantity.of(50L, QuantityScale.SCALE_0),
                batchA to Quantity.of(50L, QuantityScale.SCALE_0)
            ),
            facilityCalendarDate = testCalendarDate
        )

        val plan = FefoService.planAllocation(
            requestedQuantity = Quantity.of(30L, QuantityScale.SCALE_0),
            evaluatedCandidates = candidates
        )

        // batchA registered earlier, so must be chosen first
        assertEquals(batchA.id, plan.allocations.first().batch.id)
    }

    @Test
    fun test12_multiBatchAllocationWorks() {
        val batch1 = StockBatch(
            id = "BATCH-M1",
            productId = productDiscrete.id,
            batchNumber = "M1",
            expiryDateInt = 20270101,
            createdAt = testTimestamp
        )
        val batch2 = StockBatch(
            id = "BATCH-M2",
            productId = productDiscrete.id,
            batchNumber = "M2",
            expiryDateInt = 20270601,
            createdAt = testTimestamp
        )

        val candidates = FefoService.evaluateCandidates(
            batchesWithQuantities = listOf(
                batch1 to Quantity.of(100L, QuantityScale.SCALE_0),
                batch2 to Quantity.of(100L, QuantityScale.SCALE_0)
            ),
            facilityCalendarDate = testCalendarDate
        )

        // Request 120 units -> 100 from batch1, 20 from batch2
        val plan = FefoService.planAllocation(
            requestedQuantity = Quantity.of(120L, QuantityScale.SCALE_0),
            evaluatedCandidates = candidates
        )

        assertTrue(plan.isFullySatisfied)
        assertEquals(2, plan.allocations.size)
        assertEquals(batch1.id, plan.allocations[0].batch.id)
        assertEquals(100L, plan.allocations[0].allocatedQuantity.storageUnits)
        assertEquals(batch2.id, plan.allocations[1].batch.id)
        assertEquals(20L, plan.allocations[1].allocatedQuantity.storageUnits)
    }

    @Test
    fun test13_insufficientStockIsDetected() {
        val batch = StockBatch(
            id = "BATCH-LOW",
            productId = productDiscrete.id,
            batchNumber = "LOW",
            expiryDateInt = 20270101,
            createdAt = testTimestamp
        )

        val candidates = FefoService.evaluateCandidates(
            batchesWithQuantities = listOf(batch to Quantity.of(25L, QuantityScale.SCALE_0)),
            facilityCalendarDate = testCalendarDate
        )

        val plan = FefoService.planAllocation(
            requestedQuantity = Quantity.of(50L, QuantityScale.SCALE_0),
            evaluatedCandidates = candidates
        )

        assertFalse(plan.isFullySatisfied)
        assertEquals(25L, plan.allocatedTotal.storageUnits)
        assertEquals(25L, plan.unfulfilledQuantity.storageUnits)
    }

    // ==========================================
    // COST ALLOCATION TESTS (14-18)
    // ==========================================

    @Test
    fun test14_oneCostLayerIsConsumedCorrectly() {
        val batch = StockBatch("B1", productDiscrete.id, "B1", 20270101, testTimestamp)
        val layer = InventoryCostLayer(
            id = "L1",
            productId = productDiscrete.id,
            stockBatchId = batch.id,
            initialQuantity = Quantity.of(100L, QuantityScale.SCALE_0),
            remainingQuantity = Quantity.of(100L, QuantityScale.SCALE_0),
            acquisitionUnitCost = Money.ofMinor(1000L), // 10 KSh
            acquiredAt = testTimestamp,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val result = CostLayerAllocationService.allocateCostLayers(
            candidateAllocations = listOf(FefoCandidateAllocation(batch, Quantity.of(40L, QuantityScale.SCALE_0))),
            activeLayersByBatch = mapOf(batch.id to listOf(layer)),
            consumptionTransactionId = "TX-1",
            consumptionItemId = "ITEM-1",
            productId = productDiscrete.id,
            allocationTimestamp = testTimestamp
        )

        assertEquals(1, result.allocations.size)
        assertEquals(40L, result.allocations.first().allocatedQuantity.storageUnits)
        assertEquals(Money.ofMinor(40_000L), result.allocations.first().allocatedCost) // 40 * 10 KSh = 400 KSh
        assertEquals(60L, result.updatedCostLayers.first().remainingQuantity.storageUnits)
        assertEquals(Money.ofMinor(40_000L), result.totalCogs)
    }

    @Test
    fun test15_multipleCostLayersWithinOneBatchAreConsumedCorrectly() {
        // Spec Example:
        // Batch A:
        // Cost Layer 1: 50 units @ KSh 10 (1000 cents)
        // Cost Layer 2: 30 units @ KSh 12 (1200 cents)
        // Consume 60 units from Batch A.
        // Result: Layer 1 consumed 50, Layer 2 consumed 10.
        // COGS: 50 * 10 + 10 * 12 = KSh 620 (62,000 cents).
        // Remaining: Layer 1 = 0, Layer 2 = 20.
        val batchA = StockBatch("BATCH-A", productDiscrete.id, "A", 20270101, testTimestamp)
        val layer1 = InventoryCostLayer(
            id = "L1",
            productId = productDiscrete.id,
            stockBatchId = batchA.id,
            initialQuantity = Quantity.of(50L, QuantityScale.SCALE_0),
            remainingQuantity = Quantity.of(50L, QuantityScale.SCALE_0),
            acquisitionUnitCost = Money.ofMinor(1000L), // KSh 10
            acquiredAt = testTimestamp,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        val layer2 = InventoryCostLayer(
            id = "L2",
            productId = productDiscrete.id,
            stockBatchId = batchA.id,
            initialQuantity = Quantity.of(30L, QuantityScale.SCALE_0),
            remainingQuantity = Quantity.of(30L, QuantityScale.SCALE_0),
            acquisitionUnitCost = Money.ofMinor(1200L), // KSh 12
            acquiredAt = testTimestamp + 1000L,
            createdAt = testTimestamp + 1000L,
            updatedAt = testTimestamp + 1000L
        )

        val result = CostLayerAllocationService.allocateCostLayers(
            candidateAllocations = listOf(FefoCandidateAllocation(batchA, Quantity.of(60L, QuantityScale.SCALE_0))),
            activeLayersByBatch = mapOf(batchA.id to listOf(layer1, layer2)),
            consumptionTransactionId = "TX-MULTI-LAYER",
            consumptionItemId = "ITEM-MULTI-LAYER",
            productId = productDiscrete.id,
            allocationTimestamp = testTimestamp
        )

        assertEquals(2, result.allocations.size)
        val alloc1 = result.allocations.first { it.inventoryCostLayerId == "L1" }
        val alloc2 = result.allocations.first { it.inventoryCostLayerId == "L2" }

        assertEquals(50L, alloc1.allocatedQuantity.storageUnits)
        assertEquals(Money.ofMinor(50_000L), alloc1.allocatedCost)

        assertEquals(10L, alloc2.allocatedQuantity.storageUnits)
        assertEquals(Money.ofMinor(12_000L), alloc2.allocatedCost)

        // Exact COGS: 50,000 + 12,000 = 62,000 cents (KSh 620.00)
        assertEquals(Money.ofMinor(62_000L), result.totalCogs)

        val updatedL1 = result.updatedCostLayers.first { it.id == "L1" }
        val updatedL2 = result.updatedCostLayers.first { it.id == "L2" }
        assertEquals(0L, updatedL1.remainingQuantity.storageUnits)
        assertEquals(20L, updatedL2.remainingQuantity.storageUnits)
    }

    @Test
    fun test16_multiplePhysicalBatchesAndMultipleCostLayersAreHandledCorrectly() {
        val batch1 = StockBatch("B1", productDiscrete.id, "B1", 20261201, testTimestamp)
        val batch2 = StockBatch("B2", productDiscrete.id, "B2", 20270601, testTimestamp)

        val b1Layer = InventoryCostLayer(
            id = "L-B1",
            productId = productDiscrete.id,
            stockBatchId = batch1.id,
            initialQuantity = Quantity.of(40L, QuantityScale.SCALE_0),
            remainingQuantity = Quantity.of(40L, QuantityScale.SCALE_0),
            acquisitionUnitCost = Money.ofMinor(500L),
            acquiredAt = testTimestamp,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val b2Layer1 = InventoryCostLayer(
            id = "L-B2-1",
            productId = productDiscrete.id,
            stockBatchId = batch2.id,
            initialQuantity = Quantity.of(30L, QuantityScale.SCALE_0),
            remainingQuantity = Quantity.of(30L, QuantityScale.SCALE_0),
            acquisitionUnitCost = Money.ofMinor(600L),
            acquiredAt = testTimestamp,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val result = CostLayerAllocationService.allocateCostLayers(
            candidateAllocations = listOf(
                FefoCandidateAllocation(batch1, Quantity.of(40L, QuantityScale.SCALE_0)),
                FefoCandidateAllocation(batch2, Quantity.of(20L, QuantityScale.SCALE_0))
            ),
            activeLayersByBatch = mapOf(
                batch1.id to listOf(b1Layer),
                batch2.id to listOf(b2Layer1)
            ),
            consumptionTransactionId = "TX-COMPLEX",
            consumptionItemId = "ITEM-COMPLEX",
            productId = productDiscrete.id,
            allocationTimestamp = testTimestamp
        )

        assertEquals(2, result.allocations.size)
        // B1: 40 * 500 = 20,000 cents
        // B2: 20 * 600 = 12,000 cents
        // Total COGS: 32,000 cents
        assertEquals(Money.ofMinor(32_000L), result.totalCogs)
    }

    @Test
    fun test17_cogsIsExact() {
        val batch = StockBatch("B-EXACT", productDiscrete.id, "EXACT", 20270101, testTimestamp)
        val layer = InventoryCostLayer(
            id = "L-EXACT",
            productId = productDiscrete.id,
            stockBatchId = batch.id,
            initialQuantity = Quantity.of(7L, QuantityScale.SCALE_0),
            remainingQuantity = Quantity.of(7L, QuantityScale.SCALE_0),
            acquisitionUnitCost = Money.ofMinor(333L), // 333 minor units each
            acquiredAt = testTimestamp,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val result = CostLayerAllocationService.allocateCostLayers(
            candidateAllocations = listOf(FefoCandidateAllocation(batch, Quantity.of(7L, QuantityScale.SCALE_0))),
            activeLayersByBatch = mapOf(batch.id to listOf(layer)),
            consumptionTransactionId = "TX-EXACT",
            consumptionItemId = "ITEM-EXACT",
            productId = productDiscrete.id,
            allocationTimestamp = testTimestamp
        )

        // 7 * 333 = 2331 minor units exact
        assertEquals(Money.ofMinor(2331L), result.totalCogs)
    }

    @Test
    fun test18_remainingCostLayerQuantitiesRemainCorrect() {
        val batch = StockBatch("B-REM", productDiscrete.id, "REM", 20270101, testTimestamp)
        val layer = InventoryCostLayer(
            id = "L-REM",
            productId = productDiscrete.id,
            stockBatchId = batch.id,
            initialQuantity = Quantity.of(100L, QuantityScale.SCALE_0),
            remainingQuantity = Quantity.of(100L, QuantityScale.SCALE_0),
            acquisitionUnitCost = Money.ofMinor(100L),
            acquiredAt = testTimestamp,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val result = CostLayerAllocationService.allocateCostLayers(
            candidateAllocations = listOf(FefoCandidateAllocation(batch, Quantity.of(37L, QuantityScale.SCALE_0))),
            activeLayersByBatch = mapOf(batch.id to listOf(layer)),
            consumptionTransactionId = "TX-REM",
            consumptionItemId = "ITEM-REM",
            productId = productDiscrete.id,
            allocationTimestamp = testTimestamp
        )

        val updated = result.updatedCostLayers.first()
        assertEquals(100L, updated.initialQuantity.storageUnits)
        assertEquals(63L, updated.remainingQuantity.storageUnits)
        assertEquals(37L, result.allocations.first().allocatedQuantity.storageUnits)
    }

    // ==========================================
    // CONSUMPTION INTEGRATION TESTS (19-25)
    // ==========================================

    private fun seedStock(batchNumber: String, expiryDateInt: Int, boxes: Long, totalCostMinor: Long): StockBatch {
        val receipt = GoodsReceipt(
            id = "REC-SEED-$batchNumber",
            receiptNumber = "GRN-SEED-$batchNumber",
            receivedAt = testTimestamp,
            status = GoodsReceipt.STATUS_DRAFT,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        val item = GoodsReceiptItem(
            id = "ITEM-SEED-$batchNumber",
            goodsReceiptId = receipt.id,
            lineIndex = 0,
            productId = productDiscrete.id,
            receivedUnitId = unitBox100.id,
            receivedQuantity = Quantity.of(boxes, QuantityScale.SCALE_0),
            lineTotalCost = Money.ofMinor(totalCostMinor),
            batchNumber = batchNumber,
            expiryDateInt = expiryDateInt,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        val bundle = receivingService.commitReceipt(
            receipt = receipt,
            items = listOf(item),
            productsById = mapOf(productDiscrete.id to productDiscrete),
            unitsById = mapOf(unitBox100.id to unitBox100, unitTablet.id to unitTablet),
            commitTimestamp = testTimestamp
        )
        return bundle.newBatches.first()
    }

    @Test
    fun test19_successfulConsumptionCreatesSaleRecord() {
        seedStock("BATCH-SALE1", 20271231, boxes = 5L, totalCostMinor = 150_000L) // 500 tablets

        val request = ConsumptionRequest(
            saleNumber = "SALE-2026-001",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity = Quantity.of(2L, QuantityScale.SCALE_0) // 2 boxes = 200 tablets
                )
            ),
            customerRef = "PATIENT-123",
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        val result = consumptionService.consumeStock(request)

        assertTrue(result.sale.isCompleted)
        assertEquals("SALE-2026-001", result.sale.saleNumber)
        assertEquals("PATIENT-123", result.sale.customerRef)
        // 2 boxes @ 50,000 minor units = 100,000 minor units (KSh 1,000.00)
        assertEquals(Money.ofMinor(100_000L), result.sale.totalSellingAmount)
        // Cost: 200 tablets * 300 cents = 60,000 cents (KSh 600.00)
        assertEquals(Money.ofMinor(60_000L), result.sale.totalCogs)
    }

    @Test
    fun test20_successfulConsumptionCreatesCorrectNegativeStockMovements() {
        seedStock("BATCH-MVT-SALE", 20271231, boxes = 3L, totalCostMinor = 90_000L) // 300 tablets

        val request = ConsumptionRequest(
            saleNumber = "SALE-2026-002",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity = Quantity.of(1L, QuantityScale.SCALE_0) // 1 box = 100 tablets
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        val result = consumptionService.consumeStock(request)

        assertEquals(1, result.movements.size)
        val movement = result.movements.first()
        assertEquals(StockMovement.TYPE_SALE, movement.movementType)
        assertTrue(movement.quantity.isNegative)
        assertEquals(-100L, movement.quantity.storageUnits)

        // Authoritative balance in ledger must be: 300 - 100 = 200 tablets
        assertEquals(200L, db.stockMovementDao.getPhysicalStockUnitsForProduct(productDiscrete.id))
    }

    @Test
    fun test21_successfulConsumptionCreatesMatchingAllocationRecords() {
        val batch = seedStock("BATCH-ALLOC", 20271231, boxes = 4L, totalCostMinor = 120_000L) // 400 tablets

        val request = ConsumptionRequest(
            saleNumber = "SALE-2026-003",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity = Quantity.of(2L, QuantityScale.SCALE_0) // 200 tablets
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        val result = consumptionService.consumeStock(request)

        assertEquals(1, result.allocations.size)
        val alloc = result.allocations.first()
        assertEquals(result.sale.id, alloc.consumptionTransactionId)
        assertEquals(result.items.first().id, alloc.consumptionItemId)
        assertEquals(productDiscrete.id, alloc.productId)
        assertEquals(batch.id, alloc.stockBatchId)
        assertEquals(200L, alloc.allocatedQuantity.storageUnits)
        assertEquals(Money.ofMinor(300L), alloc.acquisitionUnitCost)
        assertEquals(Money.ofMinor(60_000L), alloc.allocatedCost)
    }

    @Test
    fun test22_costLayerRemainingQuantitiesDecreaseCorrectly() {
        seedStock("BATCH-DEPLETE", 20271231, boxes = 5L, totalCostMinor = 150_000L) // 500 tablets

        val request = ConsumptionRequest(
            saleNumber = "SALE-2026-004",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity = Quantity.of(3L, QuantityScale.SCALE_0) // 300 tablets consumed
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        val result = consumptionService.consumeStock(request)

        val updated = result.updatedCostLayers.first()
        assertEquals(200L, updated.remainingQuantity.storageUnits)
        assertEquals(200L, db.inventoryCostLayerDao.getLayerById(updated.id)!!.remainingQuantity.storageUnits)
    }

    @Test
    fun test23_insufficientStockCommitsNothing() {
        seedStock("BATCH-TINY", 20271231, boxes = 1L, totalCostMinor = 30_000L) // 100 tablets

        val request = ConsumptionRequest(
            saleNumber = "SALE-FAIL-OOS",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity = Quantity.of(2L, QuantityScale.SCALE_0) // Need 200 tablets, only 100 exist
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        try {
            consumptionService.consumeStock(request)
            fail("Expected InsufficientStockException")
        } catch (e: InsufficientStockException) {
            // Expected
        }

        // Must commit nothing: zero sales, zero movements added, zero allocations
        assertEquals(0, db.sales.size)
        assertEquals(0, db.allocations.size)
        assertEquals(1, db.movements.size) // Only initial receipt
        assertEquals(100L, db.stockMovementDao.getPhysicalStockUnitsForProduct(productDiscrete.id))
    }

    @Test
    fun test24_invalidOrExpiredStockCommitsNothing() {
        seedStock("BATCH-PAST", 20260901, boxes = 2L, totalCostMinor = 60_000L) // Expired relative to 2026-09-11

        val request = ConsumptionRequest(
            saleNumber = "SALE-FAIL-EXPIRED",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity = Quantity.of(1L, QuantityScale.SCALE_0)
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        try {
            consumptionService.consumeStock(request)
            fail("Expected InsufficientStockException due to expired stock exclusion")
        } catch (e: InsufficientStockException) {
            // Expected
        }

        assertEquals(0, db.sales.size)
        assertEquals(0, db.allocations.size)
    }

    @Test
    fun test25_transactionFailureRollsBackAllChanges() {
        seedStock("BATCH-ROLLBACK", 20271231, boxes = 10L, totalCostMinor = 300_000L) // 1000 tablets
        val initialMovementsCount = db.movements.size

        // We create a multi-item request where item 1 succeeds but item 2 fails (non-existent product)
        val request = ConsumptionRequest(
            saleNumber = "SALE-ROLLBACK-ALL",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity = Quantity.of(1L, QuantityScale.SCALE_0)
                ),
                ConsumptionLineRequest(
                    productId = "NON-EXISTENT-PROD",
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity = Quantity.of(1L, QuantityScale.SCALE_0)
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        try {
            consumptionService.consumeStock(request)
            fail("Expected IllegalArgumentException for non-existent product")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Entire transaction must be completely rolled back
        assertEquals(0, db.sales.size)
        assertEquals(0, db.allocations.size)
        assertEquals(initialMovementsCount, db.movements.size)
        assertEquals(1000L, db.stockMovementDao.getPhysicalStockUnitsForProduct(productDiscrete.id))
    }

    // ==========================================
    // CONCURRENCY TEST (26)
    // ==========================================

    @Test
    fun test26_twoCompetingConsumptionAttemptsCannotOversellSameStock() {
        // Only 1 box (100 tablets) available in inventory
        seedStock("BATCH-CONCURRENT", 20271231, boxes = 1L, totalCostMinor = 30_000L)

        val latchStart = CountDownLatch(1)
        val latchDone = CountDownLatch(2)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // Thread A requests 1 box
        val threadA = Thread {
            latchStart.await()
            try {
                consumptionService.consumeStock(
                    ConsumptionRequest(
                        saleNumber = "SALE-CONCURRENT-A",
                        items = listOf(
                            ConsumptionLineRequest(
                                productId = productDiscrete.id,
                                dispensingUnitId = unitBox100.id,
                                requestedQuantity = Quantity.of(1L, QuantityScale.SCALE_0)
                            )
                        ),
                        facilityCalendarDate = testCalendarDate,
                        transactionTimestamp = testTimestamp + 20_000L
                    )
                )
                successCount.incrementAndGet()
            } catch (e: Exception) {
                failCount.incrementAndGet()
            } finally {
                latchDone.countDown()
            }
        }

        // Thread B requests 1 box simultaneously
        val threadB = Thread {
            latchStart.await()
            try {
                consumptionService.consumeStock(
                    ConsumptionRequest(
                        saleNumber = "SALE-CONCURRENT-B",
                        items = listOf(
                            ConsumptionLineRequest(
                                productId = productDiscrete.id,
                                dispensingUnitId = unitBox100.id,
                                requestedQuantity = Quantity.of(1L, QuantityScale.SCALE_0)
                            )
                        ),
                        facilityCalendarDate = testCalendarDate,
                        transactionTimestamp = testTimestamp + 20_000L
                    )
                )
                successCount.incrementAndGet()
            } catch (e: Exception) {
                failCount.incrementAndGet()
            } finally {
                latchDone.countDown()
            }
        }

        threadA.start()
        threadB.start()

        latchStart.countDown() // Release threads at the exact same instant
        latchDone.await()

        // Exactly one must succeed, and the other must fail due to stock depletion
        assertEquals(1, successCount.get())
        assertEquals(1, failCount.get())

        // Balance in ledger must be exactly 0, never negative!
        assertEquals(0L, db.stockMovementDao.getPhysicalStockUnitsForProduct(productDiscrete.id))
    }

    // ==========================================
    // HISTORICAL IMMUTABILITY TESTS (27-29)
    // ==========================================

    @Test
    fun test27_committedReceiptHistoryIsNotRewrittenByLaterProductPriceChanges() {
        seedStock("BATCH-IMMUTABLE-REC", 20271231, boxes = 5L, totalCostMinor = 150_000L)
        val initialLayerCost = db.costLayers.values.first().acquisitionUnitCost

        // Later operational price change: selling price altered in UnitPriceConfig
        val updatedPriceConfig = priceConfigBox.copy(sellingPrice = Money.ofMinor(999_999L))
        db.productMasterDao.insertPriceConfig(updatedPriceConfig)

        // Historical cost layer acquisition unit cost must remain unchanged
        assertEquals(initialLayerCost, db.costLayers.values.first().acquisitionUnitCost)
    }

    @Test
    fun test28_committedSaleHistoryRetainsItsHistoricalSellingPriceInformation() {
        seedStock("BATCH-IMMUTABLE-SALE", 20271231, boxes = 5L, totalCostMinor = 150_000L)

        // Sell 1 box at configured price of KSh 500.00 (50,000 minor units)
        val saleResult = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-HISTORIC-01",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = productDiscrete.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity = Quantity.of(1L, QuantityScale.SCALE_0)
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 50_000L
            )
        )

        // Now, business changes the price of Box 100 to KSh 800.00 (80,000 minor units)
        val newPrice = UnitPriceConfig(
            id = "PRICE-NEW",
            productUnitId = unitBox100.id,
            sellingPrice = Money.ofMinor(80_000L),
            isActive = true,
            createdAt = testTimestamp + 100_000L
        )
        db.productMasterDao.insertPriceConfig(newPrice)

        // Verify the persisted historical sale item still has the original snapshot
        val persistedSaleItem = db.saleDao.getItemsForSale(saleResult.sale.id).first()
        assertEquals(Money.ofMinor(50_000L), persistedSaleItem.unitPriceSnapshot)
        assertEquals(Money.ofMinor(50_000L), persistedSaleItem.lineTotal)
        assertEquals(Money.ofMinor(50_000L), db.saleDao.getSaleById(saleResult.sale.id)!!.totalSellingAmount)
    }

    @Test
    fun test29_stockMovementsRemainHistoricalEvidence() {
        seedStock("BATCH-EVIDENCE", 20271231, boxes = 5L, totalCostMinor = 150_000L) // 500 tablets

        val saleResult = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-EVIDENCE-01",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = productDiscrete.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity = Quantity.of(2L, QuantityScale.SCALE_0) // 200 tablets
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 50_000L
            )
        )

        // Operational void / reversal of the sale
        val voidedSale = consumptionService.voidSale(
            saleId = saleResult.sale.id,
            voidTimestamp = testTimestamp + 60_000L,
            reason = "Customer canceled dispensing"
        )

        assertTrue(voidedSale.isVoided)

        // Original negative sale movement must STILL exist as historical audit evidence
        val allMovements = db.stockMovementDao.getMovementsForProduct(productDiscrete.id)
        val saleMovements = allMovements.filter { it.movementType == StockMovement.TYPE_SALE }
        assertEquals(1, saleMovements.size)
        assertEquals(-200L, saleMovements.first().quantity.storageUnits)

        // Compensating positive return movement must exist
        val returnMovements = allMovements.filter { it.movementType == StockMovement.TYPE_RETURN }
        assertEquals(1, returnMovements.size)
        assertEquals(200L, returnMovements.first().quantity.storageUnits)

        // Final physical stock restored back to 500 tablets
        assertEquals(500L, db.stockMovementDao.getPhysicalStockUnitsForProduct(productDiscrete.id))
    }
}
