package core.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import core.domain.allocation.CostLayerAllocationService
import core.domain.consumption.ConsumptionLineRequest
import core.domain.consumption.ConsumptionRequest
import core.domain.consumption.ConsumptionService
import core.domain.consumption.InsufficientStockException
import core.domain.fefo.FefoCandidateAllocation
import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.InventoryCostLayer
import core.domain.model.Money
import core.domain.model.ProductCategory
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.Quantity
import core.domain.model.QuantityScale
import core.domain.model.Sale
import core.domain.model.SaleItem
import core.domain.model.StockAllocation
import core.domain.model.StockBatch
import core.domain.model.StockMovement
import core.domain.model.Supplier
import core.domain.model.UnitPriceConfig
import core.domain.persistence.CoreDatabase
import core.domain.persistence.GoodsReceiptDao
import core.domain.persistence.InventoryCostLayerDao
import core.domain.persistence.ProductMasterDao
import core.domain.persistence.RoomTransactionRunner
import core.domain.persistence.SaleDao
import core.domain.persistence.StockAllocationDao
import core.domain.persistence.StockBatchDao
import core.domain.persistence.StockMovementDao
import core.domain.receiving.GoodsReceiptPersistenceService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real Room / SQLite Integration Test Suite (Tests A through L).
 *
 * Runs against an in-memory Android SQLite Room database to verify:
 * - Real SQLite foreign key constraints (ON DELETE RESTRICT)
 * - Real SQLite unique indices (unique receipt number, unique sale number, unique batch composite index)
 * - SQL-level atomic decrement of InventoryCostLayer (UPDATE ... WHERE remaining_quantity >= :quantity)
 * - Real SQLite transaction rollbacks
 * - Immutable StockAllocation audit records and compensating reversal mechanism
 */
@RunWith(AndroidJUnit4::class)
class RoomTransactionChainIntegrationTest {

    private lateinit var db: CoreDatabase
    private lateinit var goodsReceiptDao: GoodsReceiptDao
    private lateinit var stockBatchDao: StockBatchDao
    private lateinit var inventoryCostLayerDao: InventoryCostLayerDao
    private lateinit var stockMovementDao: StockMovementDao
    private lateinit var stockAllocationDao: StockAllocationDao
    private lateinit var saleDao: SaleDao
    private lateinit var productMasterDao: ProductMasterDao

    private lateinit var transactionRunner: RoomTransactionRunner
    private lateinit var receivingPersistenceService: GoodsReceiptPersistenceService
    private lateinit var consumptionService: ConsumptionService

    private val testTimestamp = 1_700_000_000_000L
    private val testCalendarDate = 20260301

    private val category = ProductCategory(
        id = "CAT-PHARMA",
        code = "PHARMA",
        name = "Pharmaceuticals",
        displayOrder = 1,
        isActive = true,
        createdAt = testTimestamp,
        updatedAt = testTimestamp
    )

    private val product = ProductMaster(
        id = "PROD-AMOX-500",
        sku = "SKU-AMOX-500",
        canonicalName = "Amoxicillin 500mg Capsules",
        categoryId = category.id,
        quantityScale = QuantityScale.SCALE_0,
        isActive = true,
        isArchived = false,
        createdAt = testTimestamp,
        updatedAt = testTimestamp
    )

    private val unitCapsule = ProductUnit(
        id = "UNIT-CAPSULE",
        productId = product.id,
        name = "Capsule",
        abbreviation = "cap",
        conversionMultiplier = 1L,
        isBaseUnit = true,
        allowDecimals = false,
        createdAt = testTimestamp,
        updatedAt = testTimestamp
    )

    private val unitBox100 = ProductUnit(
        id = "UNIT-BOX-100",
        productId = product.id,
        name = "Box of 100",
        abbreviation = "bx100",
        conversionMultiplier = 100L,
        isBaseUnit = false,
        allowDecimals = false,
        createdAt = testTimestamp,
        updatedAt = testTimestamp
    )

    private val priceConfigBox = UnitPriceConfig(
        id = "PRICE-BOX-100",
        productUnitId = unitBox100.id,
        sellingPrice = Money.ofMinor(50_000L), // KSh 500.00
        isActive = true,
        createdAt = testTimestamp
    )

    private val supplier = Supplier(
        id = "SUPP-MEDS-LTD",
        name = "Kenya Medical Supplies Ltd",
        contactPerson = "Inventory Manager",
        createdAt = testTimestamp,
        updatedAt = testTimestamp
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        goodsReceiptDao = db.goodsReceiptDao()
        stockBatchDao = db.stockBatchDao()
        inventoryCostLayerDao = db.inventoryCostLayerDao()
        stockMovementDao = db.stockMovementDao()
        stockAllocationDao = db.stockAllocationDao()
        saleDao = db.saleDao()
        productMasterDao = db.productMasterDao()

        transactionRunner = RoomTransactionRunner(db)

        receivingPersistenceService = GoodsReceiptPersistenceService(
            transactionRunner = transactionRunner,
            goodsReceiptDao = goodsReceiptDao,
            stockBatchDao = stockBatchDao,
            inventoryCostLayerDao = inventoryCostLayerDao,
            stockMovementDao = stockMovementDao
        )

        consumptionService = ConsumptionService(
            transactionRunner = transactionRunner,
            productMasterDao = productMasterDao,
            stockBatchDao = stockBatchDao,
            stockMovementDao = stockMovementDao,
            inventoryCostLayerDao = inventoryCostLayerDao,
            stockAllocationDao = stockAllocationDao,
            saleDao = saleDao
        )

        // Seed master data
        productMasterDao.insertProduct(product)
        productMasterDao.insertUnits(listOf(unitCapsule, unitBox100))
        productMasterDao.insertPriceConfig(priceConfigBox)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedStock(
        batchNumber: String,
        expiryDateInt: Int,
        boxes: Long,
        totalCostMinor: Long,
        receiptNumber: String = "REC-$batchNumber"
    ) {
        val receipt = GoodsReceipt(
            id = "ID-$receiptNumber",
            receiptNumber = receiptNumber,
            supplierId = supplier.id,
            status = GoodsReceipt.STATUS_DRAFT,
            receivedAt = testTimestamp,
            receivedByUserId = "USER-STORE-01",
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val item = GoodsReceiptItem(
            id = "ITEM-$receiptNumber",
            goodsReceiptId = receipt.id,
            lineIndex = 0,
            productId = product.id,
            receivingUnitId = unitBox100.id,
            quantity = Quantity.of(boxes, QuantityScale.SCALE_0),
            unitCost = Money.ofMinor(totalCostMinor / boxes),
            lineTotal = Money.ofMinor(totalCostMinor),
            batchNumber = batchNumber,
            expiryDateInt = expiryDateInt,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        receivingPersistenceService.commitReceipt(
            receipt = receipt,
            items = listOf(item),
            productsById = mapOf(product.id to product),
            unitsById = mapOf(unitBox100.id to unitBox100, unitCapsule.id to unitCapsule),
            commitTimestamp = testTimestamp
        )
    }

    // ==========================================
    // TEST A: Atomic Receiving Creation
    // ==========================================
    @Test
    fun testA_receivingCreatesExactBatchesCostLayersAndMovementsAtomically() {
        seedStock("BATCH-A-01", 20271231, boxes = 10L, totalCostMinor = 300_000L, receiptNumber = "REC-A-01")

        // 1. Verify receipt status committed
        val receipt = goodsReceiptDao.getReceiptByNumber("REC-A-01")
        assertNotNull(receipt)
        assertTrue(receipt!!.isCommitted)

        // 2. Verify stock batch created (10 boxes * 100 multiplier = 1000 base capsules)
        val batches = stockBatchDao.getBatchesForProduct(product.id)
        assertEquals(1, batches.size)
        assertEquals("BATCH-A-01", batches[0].batchNumber)

        // 3. Verify cost layer created with exact acquisition unit cost (300,000 / 1000 = 300 minor units / cap)
        val layers = inventoryCostLayerDao.getActiveLayersForProduct(product.id)
        assertEquals(1, layers.size)
        assertEquals(1000L, layers[0].initialQuantity.storageUnits)
        assertEquals(1000L, layers[0].remainingQuantity.storageUnits)
        assertEquals(Money.ofMinor(300L), layers[0].acquisitionUnitCost)

        // 4. Verify stock movement created in physical ledger
        val movements = stockMovementDao.getMovementsForProduct(product.id)
        assertEquals(1, movements.size)
        assertEquals(StockMovement.TYPE_PURCHASE_RECEIPT, movements[0].movementType)
        assertEquals(1000L, movements[0].quantity.storageUnits)
        assertEquals(1000L, stockMovementDao.getPhysicalStockUnitsForProduct(product.id))
    }

    // ==========================================
    // TEST B: Transaction Rollback on Receiving Failure
    // ==========================================
    @Test
    fun testB_rollbackLeavesZeroRecordsOnReceivingFailure() {
        val receipt = GoodsReceipt(
            id = "REC-FAIL-01",
            receiptNumber = "REC-FAIL-01",
            supplierId = supplier.id,
            status = GoodsReceipt.STATUS_DRAFT,
            receivedAt = testTimestamp,
            receivedByUserId = "USER-01",
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        // Line 0 is valid, Line 1 has an invalid product that fails domain validation
        val itemValid = GoodsReceiptItem(
            id = "ITEM-FAIL-0",
            goodsReceiptId = receipt.id,
            lineIndex = 0,
            productId = product.id,
            receivingUnitId = unitBox100.id,
            quantity = Quantity.of(5L, QuantityScale.SCALE_0),
            unitCost = Money.ofMinor(30_000L),
            lineTotal = Money.ofMinor(150_000L),
            batchNumber = "BATCH-ROLLBACK-01",
            expiryDateInt = 20271231,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        val itemInvalid = GoodsReceiptItem(
            id = "ITEM-FAIL-1",
            goodsReceiptId = receipt.id,
            lineIndex = 1,
            productId = "NON-EXISTENT-PROD",
            receivingUnitId = unitBox100.id,
            quantity = Quantity.of(5L, QuantityScale.SCALE_0),
            unitCost = Money.ofMinor(30_000L),
            lineTotal = Money.ofMinor(150_000L),
            batchNumber = "BATCH-ROLLBACK-02",
            expiryDateInt = 20271231,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )

        try {
            receivingPersistenceService.commitReceipt(
                receipt = receipt,
                items = listOf(itemValid, itemInvalid),
                productsById = mapOf(product.id to product),
                unitsById = mapOf(unitBox100.id to unitBox100, unitCapsule.id to unitCapsule),
                commitTimestamp = testTimestamp
            )
            fail("Expected commit to fail due to missing product")
        } catch (e: Exception) {
            // Expected
        }

        // Entire SQLite transaction must be rolled back: 0 receipts, 0 batches, 0 layers, 0 movements
        assertEquals(0, goodsReceiptDao.getItemsForReceipt(receipt.id).size)
        assertEquals(0, stockBatchDao.getBatchesForProduct(product.id).size)
        assertEquals(0, inventoryCostLayerDao.getActiveLayersForProduct(product.id).size)
        assertEquals(0L, stockMovementDao.getPhysicalStockUnitsForProduct(product.id))
    }

    // ==========================================
    // TEST C: Consumption FEFO, FIFO, Allocations & Ledger
    // ==========================================
    @Test
    fun testC_consumptionSelectsBatchesViaFEFOAllocatesCostLayersViaFIFOAndWritesLedger() {
        // Batch 1 expires later: 2028-12-31
        seedStock("BATCH-C-LATER", 20281231, boxes = 5L, totalCostMinor = 150_000L, receiptNumber = "REC-C-01")
        // Batch 2 expires earlier: 2027-06-30
        seedStock("BATCH-C-EARLIER", 20270630, boxes = 5L, totalCostMinor = 200_000L, receiptNumber = "REC-C-02")

        val result = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-C-01",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = product.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity = Quantity.of(2L, QuantityScale.SCALE_0) // 200 capsules
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 10_000L
            )
        )

        // FEFO policy must pick BATCH-C-EARLIER first
        val allocation = result.allocations.first()
        val earlierBatch = stockBatchDao.findMatchingBatch(product.id, "BATCH-C-EARLIER", 20270630)
        assertNotNull(earlierBatch)
        assertEquals(earlierBatch!!.id, allocation.stockBatchId)
        assertEquals(200L, allocation.allocatedQuantity.storageUnits)

        // Verify remaining layer quantity decremented at SQL level (500 - 200 = 300)
        val layer = inventoryCostLayerDao.getLayerById(allocation.inventoryCostLayerId)!!
        assertEquals(300L, layer.remainingQuantity.storageUnits)

        // Verify negative movement written to ledger
        val movements = stockMovementDao.getMovementsBySourceRef("SALE-C-01")
        assertEquals(1, movements.size)
        assertEquals(-200L, movements[0].quantity.storageUnits)
        assertEquals(StockMovement.TYPE_SALE, movements[0].movementType)

        // Total physical balance is now 800 (1000 - 200)
        assertEquals(800L, stockMovementDao.getPhysicalStockUnitsForProduct(product.id))
    }

    // ==========================================
    // TEST D: Insufficient Physical Stock Fails and Commits Nothing
    // ==========================================
    @Test
    fun testD_consumptionFailsAndCommitsNothingWhenPhysicalStockIsInsufficient() {
        seedStock("BATCH-D-01", 20271231, boxes = 1L, totalCostMinor = 30_000L) // 100 capsules

        try {
            consumptionService.consumeStock(
                ConsumptionRequest(
                    saleNumber = "SALE-D-FAIL",
                    items = listOf(
                        ConsumptionLineRequest(
                            productId = product.id,
                            dispensingUnitId = unitBox100.id,
                            requestedQuantity = Quantity.of(5L, QuantityScale.SCALE_0) // 500 requested, only 100 in stock
                        )
                    ),
                    facilityCalendarDate = testCalendarDate,
                    transactionTimestamp = testTimestamp + 10_000L
                )
            )
            fail("Expected InsufficientStockException")
        } catch (e: InsufficientStockException) {
            // Expected
        }

        // No sale, no allocation, no movement committed
        assertEquals(0, stockAllocationDao.getAllocationsForSale("SALE-D-FAIL").size)
        assertEquals(100L, stockMovementDao.getPhysicalStockUnitsForProduct(product.id))
        val layer = inventoryCostLayerDao.getActiveLayersForProduct(product.id).first()
        assertEquals(100L, layer.remainingQuantity.storageUnits)
    }

    // ==========================================
    // TEST E: Insufficient Cost Layer Balance Fails and Commits Nothing
    // ==========================================
    @Test
    fun testE_consumptionFailsAndCommitsNothingWhenCostLayerBalanceIsInsufficient() {
        seedStock("BATCH-E-01", 20271231, boxes = 2L, totalCostMinor = 60_000L) // 200 capsules

        // Artificially deplete cost layer balance directly while leaving physical ledger intact
        val layer = inventoryCostLayerDao.getActiveLayersForProduct(product.id).first()
        inventoryCostLayerDao.decrementRemainingQuantity(layer.id, 150L, testTimestamp) // leaves 50 units

        try {
            consumptionService.consumeStock(
                ConsumptionRequest(
                    saleNumber = "SALE-E-FAIL",
                    items = listOf(
                        ConsumptionLineRequest(
                            productId = product.id,
                            dispensingUnitId = unitBox100.id,
                            requestedQuantity = Quantity.of(1L, QuantityScale.SCALE_0) // requests 100 base units, only 50 in layer
                        )
                    ),
                    facilityCalendarDate = testCalendarDate,
                    transactionTimestamp = testTimestamp + 10_000L
                )
            )
            fail("Expected allocation failure due to insufficient cost layer balance")
        } catch (e: Exception) {
            // Expected
        }

        // No sale or allocation committed
        assertEquals(0, stockAllocationDao.getAllocationsForSale("SALE-E-FAIL").size)
        // Layer balance remained untouched at 50
        val layerAfter = inventoryCostLayerDao.getLayerById(layer.id)!!
        assertEquals(50L, layerAfter.remainingQuantity.storageUnits)
    }

    // ==========================================
    // TEST F: Consumption Rejects Expired Stock under FEFO
    // ==========================================
    @Test
    fun testF_consumptionRejectsExpiredStockUnderFEFO() {
        // Stock expired yesterday: 2026-02-28 (facility date is 2026-03-01)
        seedStock("BATCH-EXPIRED", 20260228, boxes = 5L, totalCostMinor = 150_000L)

        try {
            consumptionService.consumeStock(
                ConsumptionRequest(
                    saleNumber = "SALE-EXPIRED",
                    items = listOf(
                        ConsumptionLineRequest(
                            productId = product.id,
                            dispensingUnitId = unitBox100.id,
                            requestedQuantity = Quantity.of(1L, QuantityScale.SCALE_0)
                        )
                    ),
                    facilityCalendarDate = testCalendarDate, // 2026-03-01
                    transactionTimestamp = testTimestamp + 10_000L
                )
            )
            fail("Expected InsufficientStockException because batch is expired")
        } catch (e: InsufficientStockException) {
            // Expected
        }

        assertEquals(0, stockAllocationDao.getAllocationsForSale("SALE-EXPIRED").size)
    }

    // ==========================================
    // TEST G: Multi-Layer Consumption & Exact COGS Calculation
    // ==========================================
    @Test
    fun testG_multiLayerConsumptionSplitsAcrossLayersCorrectlyAndCalculatesExactCOGS() {
        // Layer 1: 1 box (100 cap) @ KSh 2.00 / cap (200 minor units) = 20,000 minor
        seedStock("BATCH-G-01", 20271231, boxes = 1L, totalCostMinor = 20_000L, receiptNumber = "REC-G-01")
        // Layer 2: 2 boxes (200 cap) @ KSh 3.50 / cap (350 minor units) = 70,000 minor
        seedStock("BATCH-G-02", 20281231, boxes = 2L, totalCostMinor = 70_000L, receiptNumber = "REC-G-02")

        // Consume 150 capsules (1.5 boxes -> 100 from Layer 1, 50 from Layer 2)
        val result = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-G-01",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = product.id,
                        dispensingUnitId = unitCapsule.id,
                        requestedQuantity = Quantity.of(150L, QuantityScale.SCALE_0)
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 20_000L
            )
        )

        assertEquals(2, result.allocations.size)

        // Allocation 1: 100 cap @ 200 = 20,000 minor
        val alloc1 = result.allocations[0]
        assertEquals(100L, alloc1.allocatedQuantity.storageUnits)
        assertEquals(Money.ofMinor(20_000L), alloc1.allocatedCost)

        // Allocation 2: 50 cap @ 350 = 17,500 minor
        val alloc2 = result.allocations[1]
        assertEquals(50L, alloc2.allocatedQuantity.storageUnits)
        assertEquals(Money.ofMinor(17_500L), alloc2.allocatedCost)

        // Exact COGS: 20,000 + 17,500 = 37,500 minor units
        val expectedTotalCogs = Money.ofMinor(37_500L)
        assertEquals(expectedTotalCogs, result.sale.totalCogs)
        assertEquals(expectedTotalCogs.amountMinorUnits, stockAllocationDao.getEffectiveCogsForSale(result.sale.id))
    }

    // ==========================================
    // TEST H: Re-Submitting Same Receipt or Sale Number Fails Safely
    // ==========================================
    @Test
    fun testH_resubmittingSameReceiptOrSaleNumberFailsSafelyWithoutDoubleCounting() {
        seedStock("BATCH-H-01", 20271231, boxes = 5L, totalCostMinor = 150_000L, receiptNumber = "REC-H-01")

        // 1. Attempt duplicate receiving commit with exact same receipt number
        val duplicateReceipt = GoodsReceipt(
            id = "ID-REC-H-01-DUP",
            receiptNumber = "REC-H-01",
            supplierId = supplier.id,
            status = GoodsReceipt.STATUS_DRAFT,
            receivedAt = testTimestamp,
            receivedByUserId = "USER-01",
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        try {
            receivingPersistenceService.commitReceipt(
                receipt = duplicateReceipt,
                items = listOf(),
                productsById = mapOf(product.id to product),
                unitsById = mapOf(unitBox100.id to unitBox100),
                commitTimestamp = testTimestamp
            )
            fail("Expected failure on duplicate receipt number submission")
        } catch (e: Exception) {
            // Expected
        }

        // Ledger must still have only 500 units
        assertEquals(500L, stockMovementDao.getPhysicalStockUnitsForProduct(product.id))

        // 2. Perform a valid sale
        val saleReq = ConsumptionRequest(
            saleId = "SALE-ID-H-01",
            saleNumber = "SALE-H-NUM-01",
            items = listOf(
                ConsumptionLineRequest(
                    productId = product.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity = Quantity.of(1L, QuantityScale.SCALE_0)
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 10_000L
        )
        consumptionService.consumeStock(saleReq)

        // 3. Attempt re-submitting identical sale request
        try {
            consumptionService.consumeStock(saleReq)
            fail("Expected failure on duplicate sale number submission")
        } catch (e: IllegalStateException) {
            // Expected idempotency rejection
        }

        // Inventory must reflect only ONE deduction (500 - 100 = 400)
        assertEquals(400L, stockMovementDao.getPhysicalStockUnitsForProduct(product.id))
    }

    // ==========================================
    // TEST I: Concurrent Consumption Atomic Decrement
    // ==========================================
    @Test
    fun testI_concurrentConsumptionAttemptsCannotOverAllocateCostLayers() {
        // Seed exactly 1 box (100 capsules)
        seedStock("BATCH-CONCURRENT", 20271231, boxes = 1L, totalCostMinor = 30_000L)

        val latchStart = CountDownLatch(1)
        val latchDone = CountDownLatch(2)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        val threadA = Thread {
            latchStart.await()
            try {
                consumptionService.consumeStock(
                    ConsumptionRequest(
                        saleNumber = "SALE-CONCUR-A",
                        items = listOf(
                            ConsumptionLineRequest(
                                productId = product.id,
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

        val threadB = Thread {
            latchStart.await()
            try {
                consumptionService.consumeStock(
                    ConsumptionRequest(
                        saleNumber = "SALE-CONCUR-B",
                        items = listOf(
                            ConsumptionLineRequest(
                                productId = product.id,
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

        latchStart.countDown()
        latchDone.await()

        // Exactly one must win, and one must fail due to atomic decrement constraint
        assertEquals(1, successCount.get())
        assertEquals(1, failCount.get())

        // Physical ledger and cost layer must be exactly 0, NEVER negative
        assertEquals(0L, stockMovementDao.getPhysicalStockUnitsForProduct(product.id))
        val layer = inventoryCostLayerDao.getActiveLayersForProduct(product.id)
        assertEquals(0, layer.size)
    }

    // ==========================================
    // TEST J: Sale Void Restores Stock & Layers Without Mutating Allocations
    // ==========================================
    @Test
    fun testJ_saleVoidRestoresPhysicalStockAndCostLayersWithoutMutatingOriginalAllocations() {
        seedStock("BATCH-J-01", 20271231, boxes = 5L, totalCostMinor = 150_000L) // 500 capsules

        val saleResult = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-J-01",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = product.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity = Quantity.of(2L, QuantityScale.SCALE_0) // 200 capsules
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 30_000L
            )
        )

        val originalAllocations = stockAllocationDao.getAllocationsForSale(saleResult.sale.id)
        assertEquals(1, originalAllocations.size)
        val origAlloc = originalAllocations[0]

        // Void the sale
        val voidedSale = consumptionService.voidSale(
            saleId = saleResult.sale.id,
            voidTimestamp = testTimestamp + 60_000L,
            reason = "Customer cancelled order"
        )
        assertTrue(voidedSale.isVoided)

        // 1. Original StockAllocation records remain completely untouched / immutable
        val allocationsAfterVoid = stockAllocationDao.getAllocationsForSale(saleResult.sale.id)
        assertEquals(1, allocationsAfterVoid.size)
        assertEquals(origAlloc.id, allocationsAfterVoid[0].id)
        assertEquals(origAlloc.allocatedQuantity, allocationsAfterVoid[0].allocatedQuantity)
        assertEquals(origAlloc.allocatedCost, allocationsAfterVoid[0].allocatedCost)

        // 2. Physical stock restored via compensating TYPE_RETURN movement
        val movements = stockMovementDao.getMovementsForProduct(product.id)
        val returnMovements = movements.filter { it.movementType == StockMovement.TYPE_RETURN }
        assertEquals(1, returnMovements.size)
        assertEquals(200L, returnMovements[0].quantity.storageUnits)
        assertEquals(500L, stockMovementDao.getPhysicalStockUnitsForProduct(product.id))

        // 3. Cost layer restored to original balance (500 capsules)
        val restoredLayer = inventoryCostLayerDao.getLayerById(origAlloc.inventoryCostLayerId)!!
        assertEquals(500L, restoredLayer.remainingQuantity.storageUnits)

        // 4. Effective COGS nets to zero
        assertEquals(Money.ZERO, consumptionService.getEffectiveCogsForSale(saleResult.sale.id))
        assertEquals(0L, stockAllocationDao.getEffectiveCogsForSale(saleResult.sale.id))

        // 5. Double-voiding must fail
        try {
            consumptionService.voidSale(saleResult.sale.id, testTimestamp + 70_000L, "Double void")
            fail("Expected failure on double-voiding attempt")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    // ==========================================
    // TEST K: Product / Batch / Layer Relationship Validation Fails Cleanly
    // ==========================================
    @Test
    fun testK_productBatchLayerRelationshipValidationFailsCleanlyOnInconsistentData() {
        val foreignBatch = StockBatch(
            id = "BATCH-FOREIGN",
            productId = "DIFFERENT-PROD-ID",
            batchNumber = "BF-999",
            expiryDateInt = 20281231,
            receivedDateInt = 20260101,
            supplierId = supplier.id,
            createdAt = testTimestamp
        )

        val candidate = FefoCandidateAllocation(
            batch = foreignBatch,
            allocatedQuantity = Quantity.of(10L, QuantityScale.SCALE_0)
        )

        try {
            CostLayerAllocationService.allocateCostLayers(
                candidateAllocations = listOf(candidate),
                activeLayersByBatch = mapOf(foreignBatch.id to emptyList()),
                consumptionTransactionId = "SALE-TEST-K",
                consumptionItemId = "ITEM-TEST-K",
                productId = product.id, // Does not match foreignBatch.productId
                allocationTimestamp = testTimestamp
            )
            fail("Expected IllegalArgumentException for Product/Batch relationship mismatch")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("relationship violation"))
        }
    }

    // ==========================================
    // TEST L: SQLite Foreign Key Constraints and Unique Indices Enforced
    // ==========================================
    @Test
    fun testL_sqliteForeignKeyConstraintsAndUniqueIndicesEnforcedBySchema() {
        seedStock("BATCH-L-01", 20271231, boxes = 2L, totalCostMinor = 60_000L, receiptNumber = "REC-L-01")

        // 1. Verify Unique Index on goods_receipts.receipt_number
        val duplicateReceipt = GoodsReceipt(
            id = "REC-L-DUP-ID",
            receiptNumber = "REC-L-01", // duplicate receipt number!
            supplierId = supplier.id,
            status = GoodsReceipt.STATUS_DRAFT,
            receivedAt = testTimestamp,
            receivedByUserId = "USER-01",
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
        try {
            goodsReceiptDao.insertReceipt(duplicateReceipt)
            fail("Expected SQLite unique constraint failure on duplicate receipt_number")
        } catch (e: Exception) {
            // Expected SQLiteConstraintException
        }

        // 2. Verify Unique Index on stock_batches (product_id, batch_number, expiry_date_int)
        val duplicateBatch = StockBatch(
            id = "BATCH-L-DUP-ID",
            productId = product.id,
            batchNumber = "BATCH-L-01",
            expiryDateInt = 20271231,
            receivedDateInt = 20260101,
            supplierId = supplier.id,
            createdAt = testTimestamp
        )
        try {
            stockBatchDao.insertBatch(duplicateBatch)
            fail("Expected SQLite unique constraint failure on duplicate batch index")
        } catch (e: Exception) {
            // Expected SQLiteConstraintException
        }

        // 3. Verify Foreign Key Constraint: Inserting StockAllocation referencing non-existent Sale
        val invalidAllocation = StockAllocation(
            id = "ALLOC-INVALID-FK",
            consumptionTransactionId = "NON-EXISTENT-SALE",
            consumptionItemId = "NON-EXISTENT-ITEM",
            productId = product.id,
            stockBatchId = stockBatchDao.getBatchesForProduct(product.id).first().id,
            inventoryCostLayerId = inventoryCostLayerDao.getActiveLayersForProduct(product.id).first().id,
            allocatedQuantity = Quantity.of(10L, QuantityScale.SCALE_0),
            acquisitionUnitCost = Money.ofMinor(300L),
            allocatedCost = Money.ofMinor(3000L),
            allocatedAt = testTimestamp,
            createdAt = testTimestamp
        )
        try {
            stockAllocationDao.insertAllocation(invalidAllocation)
            fail("Expected SQLite foreign key constraint failure on non-existent sale reference")
        } catch (e: Exception) {
            // Expected SQLiteConstraintException
        }
    }
}
