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
import core.domain.model.StockBatch
import core.domain.model.StockMovement
import core.domain.model.UnitPriceConfig
import core.domain.receiving.GoodsReceiptPersistenceService
import core.domain.receiving.ReceivingResult
import core.domain.testutil.FakeCoreDatabase
import core.domain.time.LocalDateValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val testTimestamp = 1_789_113_600_000L

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

        productDiscrete = ProductMaster(
            id = "PROD-PARACETAMOL",
            brandName = "Paracetamol",
            genericName = "Paracetamol 500mg",
            productType = "Tablet",
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        db.productMasterDao.insertProduct(productDiscrete)

        unitTablet = ProductUnit(
            id = "UNIT-TAB",
            productId = productDiscrete.id,
            name = "Tablet",
            abbreviation = "tab",
            conversionNumerator = 1L,
            conversionDenominator = 1L,
            isBaseUnit = true,
            isDispensingUnit = true,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        unitBox100 = ProductUnit(
            id = "UNIT-BOX100",
            productId = productDiscrete.id,
            name = "Box of 100",
            abbreviation = "box",
            conversionNumerator = 100L,
            conversionDenominator = 1L,
            isPurchaseUnit = true,
            isDispensingUnit = true,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        db.productMasterDao.insertUnits(
            listOf(unitTablet, unitBox100)
        )

        priceConfigBox = UnitPriceConfig(
            id = "PRICE-BOX100",
            productUnitId = unitBox100.id,
            sellingPrice = Money.ofMinor(50_000L),
            isActive = true,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        db.productMasterDao.insertPriceConfig(priceConfigBox)
    }

    private fun receipt(
        id: String,
        number: String,
        supplierId: String? = null,
        timestamp: Long = testTimestamp
    ): GoodsReceipt {
        return GoodsReceipt(
            id = id,
            receiptNumber = number,
            supplierId = supplierId,
            receivedAt = timestamp,
            status = GoodsReceipt.STATUS_DRAFT,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }

    private fun receiptItem(
        id: String,
        receiptId: String,
        productId: String = productDiscrete.id,
        receivingUnitId: String = unitBox100.id,
        quantity: Long,
        unitCostMinor: Long,
        totalCostMinor: Long,
        batchNumber: String,
        expiryDateInt: Int,
        timestamp: Long = testTimestamp
    ): GoodsReceiptItem {
        return GoodsReceiptItem(
            id = id,
            goodsReceiptId = receiptId,
            lineIndex = 0,
            productId = productId,
            receivingUnitId = receivingUnitId,
            receivedQuantity = Quantity.of(quantity, QuantityScale.SCALE_0),
            unitCost = Money.ofMinor(unitCostMinor),
            totalCost = Money.ofMinor(totalCostMinor),
            batchNumber = batchNumber,
            expiryDateInt = expiryDateInt,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }

    /**
     * Test helper deliberately narrows the persistence result to Success.
     *
     * The production service returns the sealed ReceivingResult type.
     * Tests below need access to Success-only fields such as newBatches,
     * costLayers, and stockMovements, so failures are converted to a
     * test-visible exception at this boundary.
     */
    private fun commitReceipt(
        receipt: GoodsReceipt,
        item: GoodsReceiptItem,
        timestamp: Long = testTimestamp
    ): ReceivingResult.Success {
        val result = receivingService.commitReceipt(
            receipt = receipt,
            items = listOf(item),
            productsById = mapOf(productDiscrete.id to productDiscrete),
            unitsById = mapOf(
                unitBox100.id to unitBox100,
                unitTablet.id to unitTablet
            ),
            commitTimestamp = timestamp
        )

        return when (result) {
            is ReceivingResult.Success -> result

            is ReceivingResult.Failure -> {
                /*
                 * The production error contract exposes a human-readable
                 * message. Do not use toString() here because sealed/data
                 * error representations are implementation details and do
                 * not represent the semantic error message.
                 */
                val message = result.errors.joinToString("; ") {
                    it.message
                }

                throw IllegalStateException(
                    "Expected goods receipt commit to succeed, " +
                        "but receipt '${receipt.id}' failed: $message"
                )
            }
        }
    }

    // ==========================================
    // RECEIVING TESTS
    // ==========================================

    @Test
    fun test1_validReceiptCommitsAtomically() {
        val receipt = receipt(
            id = "REC-001",
            number = "GRN-2026-001",
            supplierId = "SUPP-01"
        )

        val item = receiptItem(
            id = "ITEM-001",
            receiptId = receipt.id,
            quantity = 10L,
            unitCostMinor = 30_000L,
            totalCostMinor = 300_000L,
            batchNumber = "BATCH-A",
            expiryDateInt = 20271231
        )

        val bundle = commitReceipt(receipt, item)

        assertTrue(bundle.committedReceipt.isCommitted)
        assertEquals(1, db.batches.size)
        assertEquals(1, db.costLayers.size)
        assertEquals(1, db.movements.size)
        assertEquals(
            1_000L,
            db.stockMovementDao.getPhysicalStockUnitsForProduct(productDiscrete.id)
        )
    }

    @Test
    fun test2_invalidReceiptCommitsNothing() {
        val receipt = receipt(
            id = "REC-002",
            number = "GRN-2026-002"
        )

        try {
            receiptItem(
                id = "ITEM-002",
                receiptId = receipt.id,
                quantity = -5L,
                unitCostMinor = 20_000L,
                totalCostMinor = 100_000L,
                batchNumber = "BATCH-FAIL",
                expiryDateInt = 20271231
            )

            fail("Expected exception for invalid negative quantity")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }

        assertEquals(0, db.receipts.size)
        assertEquals(0, db.batches.size)
        assertEquals(0, db.costLayers.size)
        assertEquals(0, db.movements.size)
    }

    @Test
    fun test3_duplicateReceiptCannotBeCommittedTwice() {
        val receipt = receipt(
            id = "REC-003",
            number = "GRN-2026-003"
        )

        val item = receiptItem(
            id = "ITEM-003",
            receiptId = receipt.id,
            quantity = 5L,
            unitCostMinor = 30_000L,
            totalCostMinor = 150_000L,
            batchNumber = "BATCH-DUP",
            expiryDateInt = 20271231
        )

        commitReceipt(receipt, item)

        try {
            commitReceipt(receipt, item)
            fail("Expected duplicate receipt commit to fail")
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message!!.contains("already committed") ||
                    e.message!!.contains("Duplicate receiving attempt")
            )
        }

        assertEquals(1, db.movements.size)
        assertEquals(
            500L,
            db.stockMovementDao.getPhysicalStockUnitsForProduct(productDiscrete.id)
        )
    }

    @Test
    fun test4_unitConversionIsExact() {
        val receipt = receipt(
            id = "REC-004",
            number = "GRN-2026-004"
        )

        val item = receiptItem(
            id = "ITEM-004",
            receiptId = receipt.id,
            quantity = 12L,
            unitCostMinor = 30_000L,
            totalCostMinor = 360_000L,
            batchNumber = "BATCH-CONV",
            expiryDateInt = 20271231
        )

        val bundle = commitReceipt(receipt, item)

        assertEquals(
            1_200L,
            bundle.stockMovements.first().quantity.storageUnits
        )

        assertEquals(
            1_200L,
            bundle.costLayers.first().initialQuantity.storageUnits
        )
    }

    @Test
    fun test5_batchResolutionIsDeterministic() {
        val receipt1 = receipt(
            id = "REC-005A",
            number = "GRN-2026-005A"
        )

        val item1 = receiptItem(
            id = "ITEM-005A",
            receiptId = receipt1.id,
            quantity = 2L,
            unitCostMinor = 30_000L,
            totalCostMinor = 60_000L,
            batchNumber = "BATCH-SHARED",
            expiryDateInt = 20280101
        )

        commitReceipt(receipt1, item1)

        assertEquals(1, db.batches.size)

        val originalBatchId = db.batches.values.first().id

        val receipt2 = receipt(
            id = "REC-005B",
            number = "GRN-2026-005B",
            timestamp = testTimestamp + 1_000L
        )

        val item2 = receiptItem(
            id = "ITEM-005B",
            receiptId = receipt2.id,
            quantity = 3L,
            unitCostMinor = 30_000L,
            totalCostMinor = 90_000L,
            batchNumber = "BATCH-SHARED",
            expiryDateInt = 20280101,
            timestamp = testTimestamp + 1_000L
        )

        val bundle2 = commitReceipt(
            receipt2,
            item2,
            testTimestamp + 1_000L
        )

        assertEquals(0, bundle2.newBatches.size)
        assertEquals(1, db.batches.size)
        assertEquals(
            originalBatchId,
            bundle2.costLayers.first().stockBatchId
        )

        assertEquals(
            500L,
            db.stockMovementDao.getPhysicalStockUnitsForBatch(originalBatchId)
        )
    }

    @Test
    fun test6_receiptCreatesExpectedCostLayers() {
        val receipt = receipt(
            id = "REC-006",
            number = "GRN-2026-006",
            supplierId = "SUPP-ALPHA"
        )

        val item = receiptItem(
            id = "ITEM-006",
            receiptId = receipt.id,
            quantity = 5L,
            unitCostMinor = 30_000L,
            totalCostMinor = 150_000L,
            batchNumber = "BATCH-COST",
            expiryDateInt = 20270630
        )

        val bundle = commitReceipt(receipt, item)

        assertEquals(1, bundle.costLayers.size)

        val layer = bundle.costLayers.first()

        assertEquals(
            500L,
            layer.initialQuantity.storageUnits
        )

        assertEquals(
            500L,
            layer.remainingQuantity.storageUnits
        )

        assertEquals(
            300L,
            layer.acquisitionUnitCost.amountMinorUnits
        )

        assertEquals(
            "GRN-2026-006",
            layer.sourceReceiptRef
        )

        assertEquals(
            "SUPP-ALPHA",
            layer.supplierId
        )
    }

    @Test
    fun test7_costLayerMonetaryTotalExactlyEqualsReceiptLineCost() {
        val receipt = receipt(
            id = "REC-007",
            number = "GRN-2026-007"
        )

        val item = receiptItem(
            id = "ITEM-007",
            receiptId = receipt.id,
            receivingUnitId = unitTablet.id,
            quantity = 3L,
            unitCostMinor = 33L,
            totalCostMinor = 100L,
            batchNumber = "BATCH-INDIVISIBLE",
            expiryDateInt = 20270630
        )

        val bundle = commitReceipt(receipt, item)

        assertEquals(2, bundle.costLayers.size)

        /*
         * totalCost is the authoritative acquisition monetary amount.
         *
         * The layer unit costs are intentionally allowed to differ by one
         * minor unit so that the layer quantities conserve the receipt total
         * exactly. Do not reconstruct the receipt total by assuming:
         *
         *     quantity × nominal unitCost
         *
         * is necessarily exact.
         */
        val totalCostSum = bundle.costLayers.sumOf {
            it.initialQuantity.storageUnits *
                it.acquisitionUnitCost.amountMinorUnits
        }

        assertEquals(100L, totalCostSum)
        assertEquals(
            item.totalCost.amountMinorUnits,
            totalCostSum
        )
    }

    @Test
    fun test8_purchaseMovementQuantityEqualsReceivedBaseQuantity() {
        val receipt = receipt(
            id = "REC-008",
            number = "GRN-2026-008"
        )

        val item = receiptItem(
            id = "ITEM-008",
            receiptId = receipt.id,
            quantity = 4L,
            unitCostMinor = 30_000L,
            totalCostMinor = 120_000L,
            batchNumber = "BATCH-MVT",
            expiryDateInt = 20271231
        )

        val bundle = commitReceipt(receipt, item)

        val movement = bundle.stockMovements.first()

        assertEquals(
            StockMovement.TYPE_PURCHASE_RECEIPT,
            movement.movementType
        )

        assertTrue(movement.quantity.isPositive)
        assertEquals(400L, movement.quantity.storageUnits)
    }

    // ==========================================
    // FEFO TESTS
    // ==========================================

    @Test
    fun test9_earliestEligibleExpiryIsSelectedFirst() {
        val batchEarly = StockBatch(
            id = "BATCH-EARLY",
            productId = productDiscrete.id,
            batchNumber = "B-EARLY",
            expiryDateInt = 20261001,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val batchLate = StockBatch(
            id = "BATCH-LATE",
            productId = productDiscrete.id,
            batchNumber = "B-LATE",
            expiryDateInt = 20270501,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
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
        assertEquals(
            batchEarly.id,
            plan.allocations.first().batch.id
        )
        assertEquals(
            30L,
            plan.allocations.first().allocatedQuantity.storageUnits
        )
    }

    @Test
    fun test10_expiredStockIsExcluded() {
        val batchExpired = StockBatch(
            id = "BATCH-EXPIRED",
            productId = productDiscrete.id,
            batchNumber = "B-EXPIRED",
            expiryDateInt = 20260901,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val batchValid = StockBatch(
            id = "BATCH-VALID",
            productId = productDiscrete.id,
            batchNumber = "B-VALID",
            expiryDateInt = 20270101,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val candidates = FefoService.evaluateCandidates(
            batchesWithQuantities = listOf(
                batchExpired to Quantity.of(100L, QuantityScale.SCALE_0),
                batchValid to Quantity.of(50L, QuantityScale.SCALE_0)
            ),
            facilityCalendarDate = testCalendarDate,
            policy = ExpiryPolicy.DEFAULT
        )

        val expiredCandidate =
            candidates.first { it.batch.id == batchExpired.id }

        assertFalse(expiredCandidate.isEligible)

        val plan = FefoService.planAllocation(
            requestedQuantity = Quantity.of(40L, QuantityScale.SCALE_0),
            evaluatedCandidates = candidates
        )

        assertEquals(1, plan.allocations.size)
        assertEquals(
            batchValid.id,
            plan.allocations.first().batch.id
        )
    }

    @Test
    fun test11_sameExpiryBatchesUseDeterministicTieBreaking() {
        val batchA = StockBatch(
            id = "BATCH-TIE-A",
            productId = productDiscrete.id,
            batchNumber = "LOT-100",
            expiryDateInt = 20270101,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val batchB = StockBatch(
            id = "BATCH-TIE-B",
            productId = productDiscrete.id,
            batchNumber = "LOT-200",
            expiryDateInt = 20270101,
            createdAt = testTimestamp + 1_000L,
            updatedAt = testTimestamp + 1_000L
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

        assertEquals(
            batchA.id,
            plan.allocations.first().batch.id
        )
    }

    @Test
    fun test12_multiBatchAllocationWorks() {
        val batch1 = StockBatch(
            id = "BATCH-M1",
            productId = productDiscrete.id,
            batchNumber = "M1",
            expiryDateInt = 20270101,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val batch2 = StockBatch(
            id = "BATCH-M2",
            productId = productDiscrete.id,
            batchNumber = "M2",
            expiryDateInt = 20270601,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val candidates = FefoService.evaluateCandidates(
            batchesWithQuantities = listOf(
                batch1 to Quantity.of(100L, QuantityScale.SCALE_0),
                batch2 to Quantity.of(100L, QuantityScale.SCALE_0)
            ),
            facilityCalendarDate = testCalendarDate
        )

        val plan = FefoService.planAllocation(
            requestedQuantity = Quantity.of(120L, QuantityScale.SCALE_0),
            evaluatedCandidates = candidates
        )

        assertTrue(plan.isFullySatisfied)
        assertEquals(2, plan.allocations.size)

        assertEquals(
            batch1.id,
            plan.allocations[0].batch.id
        )

        assertEquals(
            100L,
            plan.allocations[0].allocatedQuantity.storageUnits
        )

        assertEquals(
            batch2.id,
            plan.allocations[1].batch.id
        )

        assertEquals(
            20L,
            plan.allocations[1].allocatedQuantity.storageUnits
        )
    }

    @Test
    fun test13_insufficientStockIsDetected() {
        val batch = StockBatch(
            id = "BATCH-LOW",
            productId = productDiscrete.id,
            batchNumber = "LOW",
            expiryDateInt = 20270101,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val candidates = FefoService.evaluateCandidates(
            batchesWithQuantities = listOf(
                batch to Quantity.of(25L, QuantityScale.SCALE_0)
            ),
            facilityCalendarDate = testCalendarDate
        )

        val plan = FefoService.planAllocation(
            requestedQuantity = Quantity.of(50L, QuantityScale.SCALE_0),
            evaluatedCandidates = candidates
        )

        assertFalse(plan.isFullySatisfied)
        assertEquals(
            25L,
            plan.allocatedTotal.storageUnits
        )
        assertEquals(
            25L,
            plan.unfulfilledQuantity.storageUnits
        )
    }

    // ==========================================
    // COST ALLOCATION TESTS
    // ==========================================

    @Test
    fun test14_oneCostLayerIsConsumedCorrectly() {
        val batch = StockBatch(
            id = "B1",
            productId = productDiscrete.id,
            batchNumber = "B1",
            expiryDateInt = 20270101,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val layer = InventoryCostLayer(
            id = "L1",
            productId = productDiscrete.id,
            stockBatchId = batch.id,
            initialQuantity = Quantity.of(100L, QuantityScale.SCALE_0),
            remainingQuantity = Quantity.of(100L, QuantityScale.SCALE_0),
            acquisitionUnitCost = Money.ofMinor(1_000L),
            acquiredAt = testTimestamp,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val result = CostLayerAllocationService.allocateCostLayers(
            candidateAllocations = listOf(
                FefoCandidateAllocation(
                    batch,
                    Quantity.of(40L, QuantityScale.SCALE_0)
                )
            ),
            activeLayersByBatch = mapOf(
                batch.id to listOf(layer)
            ),
            consumptionTransactionId = "TX-1",
            consumptionItemId = "ITEM-1",
            productId = productDiscrete.id,
            allocationTimestamp = testTimestamp
        )

        assertEquals(1, result.allocations.size)
        assertEquals(
            40L,
            result.allocations.first().allocatedQuantity.storageUnits
        )
        assertEquals(
            Money.ofMinor(40_000L),
            result.allocations.first().allocatedCost
        )
        assertEquals(
            60L,
            result.updatedCostLayers.first().remainingQuantity.storageUnits
        )
        assertEquals(
            Money.ofMinor(40_000L),
            result.totalCogs
        )
    }

    @Test
    fun test15_multipleCostLayersWithinOneBatchAreConsumedCorrectly() {
        val batchA = StockBatch(
            id = "BATCH-A",
            productId = productDiscrete.id,
            batchNumber = "A",
            expiryDateInt = 20270101,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val layer1 = InventoryCostLayer(
            id = "L1",
            productId = productDiscrete.id,
            stockBatchId = batchA.id,
            initialQuantity = Quantity.of(50L, QuantityScale.SCALE_0),
            remainingQuantity = Quantity.of(50L, QuantityScale.SCALE_0),
            acquisitionUnitCost = Money.ofMinor(1_000L),
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
            acquisitionUnitCost = Money.ofMinor(1_200L),
            acquiredAt = testTimestamp + 1_000L,
            createdAt = testTimestamp + 1_000L,
            updatedAt = testTimestamp + 1_000L
        )

        val result = CostLayerAllocationService.allocateCostLayers(
            candidateAllocations = listOf(
                FefoCandidateAllocation(
                    batchA,
                    Quantity.of(60L, QuantityScale.SCALE_0)
                )
            ),
            activeLayersByBatch = mapOf(
                batchA.id to listOf(layer1, layer2)
            ),
            consumptionTransactionId = "TX-MULTI-LAYER",
            consumptionItemId = "ITEM-MULTI-LAYER",
            productId = productDiscrete.id,
            allocationTimestamp = testTimestamp
        )

        assertEquals(2, result.allocations.size)

        val alloc1 =
            result.allocations.first { it.inventoryCostLayerId == "L1" }

        val alloc2 =
            result.allocations.first { it.inventoryCostLayerId == "L2" }

        assertEquals(50L, alloc1.allocatedQuantity.storageUnits)
        assertEquals(Money.ofMinor(50_000L), alloc1.allocatedCost)

        assertEquals(10L, alloc2.allocatedQuantity.storageUnits)
        assertEquals(Money.ofMinor(12_000L), alloc2.allocatedCost)

        assertEquals(
            Money.ofMinor(62_000L),
            result.totalCogs
        )

        val updatedL1 =
            result.updatedCostLayers.first { it.id == "L1" }

        val updatedL2 =
            result.updatedCostLayers.first { it.id == "L2" }

        assertEquals(
            0L,
            updatedL1.remainingQuantity.storageUnits
        )

        assertEquals(
            20L,
            updatedL2.remainingQuantity.storageUnits
        )
    }

    @Test
    fun test16_multiplePhysicalBatchesAndMultipleCostLayersAreHandledCorrectly() {
        val batch1 = StockBatch(
            id = "B1",
            productId = productDiscrete.id,
            batchNumber = "B1",
            expiryDateInt = 20261201,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val batch2 = StockBatch(
            id = "B2",
            productId = productDiscrete.id,
            batchNumber = "B2",
            expiryDateInt = 20270601,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

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
                FefoCandidateAllocation(
                    batch1,
                    Quantity.of(40L, QuantityScale.SCALE_0)
                ),
                FefoCandidateAllocation(
                    batch2,
                    Quantity.of(20L, QuantityScale.SCALE_0)
                )
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
        assertEquals(
            Money.ofMinor(32_000L),
            result.totalCogs
        )
    }

    @Test
    fun test17_cogsIsExact() {
        val batch = StockBatch(
            id = "B-EXACT",
            productId = productDiscrete.id,
            batchNumber = "EXACT",
            expiryDateInt = 20270101,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val layer = InventoryCostLayer(
            id = "L-EXACT",
            productId = productDiscrete.id,
            stockBatchId = batch.id,
            initialQuantity = Quantity.of(7L, QuantityScale.SCALE_0),
            remainingQuantity = Quantity.of(7L, QuantityScale.SCALE_0),
            acquisitionUnitCost = Money.ofMinor(333L),
            acquiredAt = testTimestamp,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val result = CostLayerAllocationService.allocateCostLayers(
            candidateAllocations = listOf(
                FefoCandidateAllocation(
                    batch,
                    Quantity.of(7L, QuantityScale.SCALE_0)
                )
            ),
            activeLayersByBatch = mapOf(
                batch.id to listOf(layer)
            ),
            consumptionTransactionId = "TX-EXACT",
            consumptionItemId = "ITEM-EXACT",
            productId = productDiscrete.id,
            allocationTimestamp = testTimestamp
        )

        assertEquals(
            Money.ofMinor(2_331L),
            result.totalCogs
        )
    }

    @Test
    fun test18_remainingCostLayerQuantitiesRemainCorrect() {
        val batch = StockBatch(
            id = "B-REM",
            productId = productDiscrete.id,
            batchNumber = "REM",
            expiryDateInt = 20270101,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

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
            candidateAllocations = listOf(
                FefoCandidateAllocation(
                    batch,
                    Quantity.of(37L, QuantityScale.SCALE_0)
                )
            ),
            activeLayersByBatch = mapOf(
                batch.id to listOf(layer)
            ),
            consumptionTransactionId = "TX-REM",
            consumptionItemId = "ITEM-REM",
            productId = productDiscrete.id,
            allocationTimestamp = testTimestamp
        )

        val updated = result.updatedCostLayers.first()

        assertEquals(
            100L,
            updated.initialQuantity.storageUnits
        )

        assertEquals(
            63L,
            updated.remainingQuantity.storageUnits
        )

        assertEquals(
            37L,
            result.allocations.first().allocatedQuantity.storageUnits
        )
    }

    // ==========================================
    // CONSUMPTION INTEGRATION TESTS
    // ==========================================

    private fun seedStock(
        batchNumber: String,
        expiryDateInt: Int,
        boxes: Long,
        totalCostMinor: Long
    ): StockBatch {
        val receipt = receipt(
            id = "REC-SEED-$batchNumber",
            number = "GRN-SEED-$batchNumber"
        )

        val unitCostMinor =
            totalCostMinor / boxes

        val item = receiptItem(
            id = "ITEM-SEED-$batchNumber",
            receiptId = receipt.id,
            quantity = boxes,
            unitCostMinor = unitCostMinor,
            totalCostMinor = totalCostMinor,
            batchNumber = batchNumber,
            expiryDateInt = expiryDateInt
        )

        val bundle = commitReceipt(receipt, item)

        return bundle.newBatches.first()
    }

    @Test
    fun test19_successfulConsumptionCreatesSaleRecord() {
        seedStock(
            "BATCH-SALE1",
            20271231,
            boxes = 5L,
            totalCostMinor = 150_000L
        )

        val result = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-2026-001",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = productDiscrete.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity =
                            Quantity.of(2L, QuantityScale.SCALE_0)
                    )
                ),
                customerRef = "PATIENT-123",
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 10_000L
            )
        )

        assertTrue(result.sale.isCompleted)
        assertEquals(
            "SALE-2026-001",
            result.sale.saleNumber
        )
        assertEquals(
            "PATIENT-123",
            result.sale.customerRef
        )
        assertEquals(
            Money.ofMinor(100_000L),
            result.sale.totalSellingAmount
        )
        assertEquals(
            Money.ofMinor(60_000L),
            result.sale.totalCogs
        )
    }

    @Test
    fun test20_successfulConsumptionCreatesCorrectNegativeStockMovements() {
        seedStock(
            "BATCH-MVT-SALE",
            20271231,
            boxes = 3L,
            totalCostMinor = 90_000L
        )

        val result = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-2026-002",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = productDiscrete.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity =
                            Quantity.of(1L, QuantityScale.SCALE_0)
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 10_000L
            )
        )

        assertEquals(1, result.movements.size)

        val movement = result.movements.first()

        assertEquals(
            StockMovement.TYPE_SALE,
            movement.movementType
        )

        assertTrue(movement.quantity.isNegative)
        assertEquals(-100L, movement.quantity.storageUnits)

        assertEquals(
            200L,
            db.stockMovementDao.getPhysicalStockUnitsForProduct(
                productDiscrete.id
            )
        )
    }

    @Test
    fun test21_successfulConsumptionCreatesMatchingAllocationRecords() {
        val batch = seedStock(
            "BATCH-ALLOC",
            20271231,
            boxes = 4L,
            totalCostMinor = 120_000L
        )

        val request = ConsumptionRequest(
            saleNumber = "SALE-2026-003",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity =
                        Quantity.of(2L, QuantityScale.SCALE_0)
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        val result = consumptionService.consumeStock(request)

        assertEquals(1, result.allocations.size)

        val alloc = result.allocations.first()

        assertEquals(
            result.sale.id,
            alloc.consumptionTransactionId
        )

        assertEquals(
            result.items.first().id,
            alloc.consumptionItemId
        )

        assertEquals(
            productDiscrete.id,
            alloc.productId
        )

        assertEquals(
            batch.id,
            alloc.stockBatchId
        )

        assertEquals(
            200L,
            alloc.allocatedQuantity.storageUnits
        )

        assertEquals(
            Money.ofMinor(300L),
            alloc.acquisitionUnitCost
        )

        assertEquals(
            Money.ofMinor(60_000L),
            alloc.allocatedCost
        )
    }

    @Test
    fun test22_costLayerRemainingQuantitiesDecreaseCorrectly() {
        seedStock(
            "BATCH-DEPLETE",
            20271231,
            boxes = 5L,
            totalCostMinor = 150_000L
        )

        val request = ConsumptionRequest(
            saleNumber = "SALE-2026-004",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity =
                        Quantity.of(3L, QuantityScale.SCALE_0)
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        val result = consumptionService.consumeStock(request)

        val updated = result.updatedCostLayers.first()

        assertEquals(
            200L,
            updated.remainingQuantity.storageUnits
        )

        assertEquals(
            200L,
            db.inventoryCostLayerDao
                .getLayerById(updated.id)!!
                .remainingQuantity
                .storageUnits
        )
    }

    @Test
    fun test23_insufficientStockCommitsNothing() {
        seedStock(
            "BATCH-TINY",
            20271231,
            boxes = 1L,
            totalCostMinor = 30_000L
        )

        val request = ConsumptionRequest(
            saleNumber = "SALE-FAIL-OOS",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity =
                        Quantity.of(2L, QuantityScale.SCALE_0)
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        try {
            consumptionService.consumeStock(request)
            fail("Expected InsufficientStockException")
        } catch (e: InsufficientStockException) {
            // Expected.
        }

        assertEquals(0, db.sales.size)
        assertEquals(0, db.allocations.size)
        assertEquals(1, db.movements.size)

        assertEquals(
            100L,
            db.stockMovementDao.getPhysicalStockUnitsForProduct(
                productDiscrete.id
            )
        )
    }

    @Test
    fun test24_invalidOrExpiredStockCommitsNothing() {
        seedStock(
            "BATCH-PAST",
            20260901,
            boxes = 2L,
            totalCostMinor = 60_000L
        )

        val request = ConsumptionRequest(
            saleNumber = "SALE-FAIL-EXPIRED",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity =
                        Quantity.of(1L, QuantityScale.SCALE_0)
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        try {
            consumptionService.consumeStock(request)
            fail("Expected InsufficientStockException")
        } catch (e: InsufficientStockException) {
            // Expected.
        }

        assertEquals(0, db.sales.size)
        assertEquals(0, db.allocations.size)
    }

    @Test
    fun test25_transactionFailureRollsBackAllChanges() {
        seedStock(
            "BATCH-ROLLBACK",
            20271231,
            boxes = 10L,
            totalCostMinor = 300_000L
        )

        val initialMovementsCount = db.movements.size

        val request = ConsumptionRequest(
            saleNumber = "SALE-ROLLBACK-ALL",
            items = listOf(
                ConsumptionLineRequest(
                    productId = productDiscrete.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity =
                        Quantity.of(1L, QuantityScale.SCALE_0)
                ),
                ConsumptionLineRequest(
                    productId = "NON-EXISTENT-PROD",
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity =
                        Quantity.of(1L, QuantityScale.SCALE_0)
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )

        try {
            consumptionService.consumeStock(request)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }

        assertEquals(0, db.sales.size)
        assertEquals(0, db.allocations.size)
        assertEquals(initialMovementsCount, db.movements.size)

        assertEquals(
            1_000L,
            db.stockMovementDao.getPhysicalStockUnitsForProduct(
                productDiscrete.id
            )
        )
    }

    // ==========================================
    // CONCURRENCY TEST
    // ==========================================

    @Test
    fun test26_twoCompetingConsumptionAttemptsCannotOversellSameStock() {
        seedStock(
            "BATCH-CONCURRENT",
            20271231,
            boxes = 1L,
            totalCostMinor = 30_000L
        )

        val latchStart = CountDownLatch(1)
        val latchDone = CountDownLatch(2)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

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
                                requestedQuantity =
                                    Quantity.of(
                                        1L,
                                        QuantityScale.SCALE_0
                                    )
                            )
                        ),
                        facilityCalendarDate = testCalendarDate,
                        transactionTimestamp =
                            testTimestamp + 20_000L
                    )
                )

                successCount.incrementAndGet()
            } catch (_: Exception) {
                failCount.incrementAndGet()
            } finally {
                latchDone.countDown()
            }
        }

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
                                requestedQuantity =
                                    Quantity.of(
                                        1L,
                                        QuantityScale.SCALE_0
                                    )
                            )
                        ),
                        facilityCalendarDate = testCalendarDate,
                        transactionTimestamp =
                            testTimestamp + 20_000L
                    )
                )

                successCount.incrementAndGet()
            } catch (_: Exception) {
                failCount.incrementAndGet()
            } finally {
                latchDone.countDown()
            }
        }

        threadA.start()
        threadB.start()

        latchStart.countDown()
        latchDone.await()

        assertEquals(1, successCount.get())
        assertEquals(1, failCount.get())

        assertEquals(
            0L,
            db.stockMovementDao.getPhysicalStockUnitsForProduct(
                productDiscrete.id
            )
        )
    }

    // ==========================================
    // HISTORICAL IMMUTABILITY TESTS
    // ==========================================

    @Test
    fun test27_committedReceiptHistoryIsNotRewrittenByLaterProductPriceChanges() {
        seedStock(
            "BATCH-IMMUTABLE-REC",
            20271231,
            boxes = 5L,
            totalCostMinor = 150_000L
        )

        val initialLayerCost =
            db.costLayers.values.first().acquisitionUnitCost

        val updatedPriceConfig =
            priceConfigBox.copy(
                sellingPrice = Money.ofMinor(999_999L)
            )

        db.productMasterDao.insertPriceConfig(updatedPriceConfig)

        assertEquals(
            initialLayerCost,
            db.costLayers.values.first().acquisitionUnitCost
        )
    }

    @Test
    fun test28_committedSaleHistoryRetainsItsHistoricalSellingPriceInformation() {
        seedStock(
            "BATCH-IMMUTABLE-SALE",
            20271231,
            boxes = 5L,
            totalCostMinor = 150_000L
        )

        val saleResult = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-HISTORIC-01",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = productDiscrete.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity =
                            Quantity.of(1L, QuantityScale.SCALE_0)
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 50_000L
            )
        )

        val newPrice = UnitPriceConfig(
            id = "PRICE-NEW",
            productUnitId = unitBox100.id,
            sellingPrice = Money.ofMinor(80_000L),
            isActive = true,
            createdAt = testTimestamp + 100_000L,
            updatedAt = testTimestamp + 100_000L
        )

        db.productMasterDao.insertPriceConfig(newPrice)

        val persistedSaleItem =
            db.saleDao
                .getItemsForSale(saleResult.sale.id)
                .first()

        assertEquals(
            Money.ofMinor(50_000L),
            persistedSaleItem.unitPriceSnapshot
        )

        assertEquals(
            Money.ofMinor(50_000L),
            persistedSaleItem.lineTotal
        )

        assertEquals(
            Money.ofMinor(50_000L),
            db.saleDao
                .getSaleById(saleResult.sale.id)!!
                .totalSellingAmount
        )
    }

    @Test
    fun test29_stockMovementsRemainHistoricalEvidence() {
        seedStock(
            "BATCH-EVIDENCE",
            20271231,
            boxes = 5L,
            totalCostMinor = 150_000L
        )

        val saleResult = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-EVIDENCE-01",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = productDiscrete.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity =
                            Quantity.of(2L, QuantityScale.SCALE_0)
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 50_000L
            )
        )

        val voidedSale = consumptionService.voidSale(
            saleId = saleResult.sale.id,
            voidTimestamp = testTimestamp + 60_000L,
            reason = "Customer canceled dispensing"
        )

        assertTrue(voidedSale.isVoided)

        val allMovements =
            db.stockMovementDao
                .getMovementsForProduct(productDiscrete.id)

        val saleMovements =
            allMovements.filter {
                it.movementType == StockMovement.TYPE_SALE
            }

        assertEquals(1, saleMovements.size)
        assertEquals(
            -200L,
            saleMovements.first().quantity.storageUnits
        )

        val returnMovements =
            allMovements.filter {
                it.movementType == StockMovement.TYPE_RETURN
            }

        assertEquals(1, returnMovements.size)
        assertEquals(
            200L,
            returnMovements.first().quantity.storageUnits
        )

        assertEquals(
            500L,
            db.stockMovementDao.getPhysicalStockUnitsForProduct(
                productDiscrete.id
            )
        )
    }

    // ==========================================
    // HARDENING TESTS
    // ==========================================

    @Test
    fun test30_effectiveCogsNetsToZeroAfterSaleVoidWithoutMutatingOriginalAllocations() {
        seedStock(
            "BATCH-VOID-COGS",
            20271231,
            boxes = 5L,
            totalCostMinor = 150_000L
        )

        val saleResult = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-VOID-COGS-01",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = productDiscrete.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity =
                            Quantity.of(1L, QuantityScale.SCALE_0)
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 10_000L
            )
        )

        assertEquals(
            Money.ofMinor(30_000L),
            consumptionService.getEffectiveCogsForSale(
                saleResult.sale.id
            )
        )

        assertEquals(
            30_000L,
            db.stockAllocationDao.getEffectiveCogsForSale(
                saleResult.sale.id
            )
        )

        val originalAllocations =
            db.stockAllocationDao
                .getAllocationsForSale(saleResult.sale.id)

        assertEquals(1, originalAllocations.size)

        consumptionService.voidSale(
            saleId = saleResult.sale.id,
            voidTimestamp = testTimestamp + 20_000L,
            reason = "Customer canceled dispensing"
        )

        assertEquals(
            Money.ZERO,
            consumptionService.getEffectiveCogsForSale(
                saleResult.sale.id
            )
        )

        assertEquals(
            0L,
            db.stockAllocationDao.getEffectiveCogsForSale(
                saleResult.sale.id
            )
        )

        val allocationsAfterVoid =
            db.stockAllocationDao
                .getAllocationsForSale(saleResult.sale.id)

        assertEquals(1, allocationsAfterVoid.size)

        assertEquals(
            originalAllocations[0].id,
            allocationsAfterVoid[0].id
        )

        assertEquals(
            originalAllocations[0].allocatedQuantity,
            allocationsAfterVoid[0].allocatedQuantity
        )

        assertEquals(
            originalAllocations[0].allocatedCost,
            allocationsAfterVoid[0].allocatedCost
        )

        val layer =
            db.inventoryCostLayerDao
                .getActiveLayersForProduct(productDiscrete.id)
                .first()

        assertEquals(
            500L,
            layer.remainingQuantity.storageUnits
        )
    }

    @Test
    fun test31_doubleVoidingIsRejectedAndSafe() {
        seedStock(
            "BATCH-DBL-VOID",
            20271231,
            boxes = 5L,
            totalCostMinor = 150_000L
        )

        val saleResult = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-DBL-VOID-01",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = productDiscrete.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity =
                            Quantity.of(1L, QuantityScale.SCALE_0)
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 10_000L
            )
        )

        consumptionService.voidSale(
            saleResult.sale.id,
            testTimestamp + 20_000L,
            "First void"
        )

        try {
            consumptionService.voidSale(
                saleResult.sale.id,
                testTimestamp + 30_000L,
                "Second void attempt"
            )

            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("already voided"))
        }

        val returnMovements =
            db.stockMovementDao
                .getMovementsForProduct(productDiscrete.id)
                .filter {
                    it.movementType == StockMovement.TYPE_RETURN
                }

        assertEquals(1, returnMovements.size)

        assertEquals(
            500L,
            db.stockMovementDao.getPhysicalStockUnitsForProduct(
                productDiscrete.id
            )
        )
    }

    @Test
    fun test32_atomicDecrementFailureAbortsEntireConsumptionTransaction() {
        seedStock(
            "BATCH-ATOMIC-FAIL",
            20271231,
            boxes = 1L,
            totalCostMinor = 30_000L
        )

        val layer =
            db.inventoryCostLayerDao
                .getActiveLayersForProduct(productDiscrete.id)
                .first()

        db.inventoryCostLayerDao.decrementRemainingQuantity(
            layer.id,
            80L,
            testTimestamp
        )

        try {
            consumptionService.consumeStock(
                ConsumptionRequest(
                    saleNumber = "SALE-ATOMIC-FAIL-01",
                    items = listOf(
                        ConsumptionLineRequest(
                            productId = productDiscrete.id,
                            dispensingUnitId = unitBox100.id,
                            requestedQuantity =
                                Quantity.of(
                                    1L,
                                    QuantityScale.SCALE_0
                                )
                        )
                    ),
                    facilityCalendarDate = testCalendarDate,
                    transactionTimestamp =
                        testTimestamp + 10_000L
                )
            )

            fail("Expected allocation or atomic decrement failure")
        } catch (_: Exception) {
            // Expected.
        }

        assertEquals(0, db.sales.size)
        assertEquals(0, db.allocations.size)

        val layerAfter =
            db.inventoryCostLayerDao
                .getLayerById(layer.id)!!

        assertEquals(
            20L,
            layerAfter.remainingQuantity.storageUnits
        )
    }

    @Test
    fun test33_productBatchLayerRelationshipMismatchFailsDefensively() {
        val foreignBatch = StockBatch(
            id = "BATCH-MISMATCH",
            productId = "SOME-OTHER-PRODUCT-ID",
            batchNumber = "BM-001",
            expiryDateInt = 20281231,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val candidate = FefoCandidateAllocation(
            batch = foreignBatch,
            allocatedQuantity =
                Quantity.of(50L, QuantityScale.SCALE_0)
        )

        try {
            CostLayerAllocationService.allocateCostLayers(
                candidateAllocations = listOf(candidate),
                activeLayersByBatch = mapOf(
                    foreignBatch.id to emptyList()
                ),
                consumptionTransactionId = "SALE-FAIL-REL",
                consumptionItemId = "ITEM-FAIL-REL",
                productId = productDiscrete.id,
                allocationTimestamp = testTimestamp
            )

            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                e.message!!.contains("relationship violation")
            )
        }
    }
}
