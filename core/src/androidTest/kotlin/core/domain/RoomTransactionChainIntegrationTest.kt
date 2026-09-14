package core.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import core.domain.consumption.ConsumptionLineRequest
import core.domain.consumption.ConsumptionRequest
import core.domain.consumption.ConsumptionService
import core.domain.consumption.InsufficientStockException
import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.Money
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.Quantity
import core.domain.model.QuantityScale
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

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

    private val product = ProductMaster(
        id = "PROD-AMOX-500",
        brandName = "Amoxicillin",
        genericName = "Amoxicillin 500mg Capsules",
        productType = "Capsule",
        createdAt = testTimestamp,
        updatedAt = testTimestamp
    )

    private val unitCapsule = ProductUnit(
        id = "UNIT-CAPSULE",
        productId = product.id,
        name = "Capsule",
        abbreviation = "cap",
        conversionNumerator = 1L,
        conversionDenominator = 1L,
        isBaseUnit = true,
        isDispensingUnit = true,
        createdAt = testTimestamp,
        updatedAt = testTimestamp
    )

    private val unitBox100 = ProductUnit(
        id = "UNIT-BOX-100",
        productId = product.id,
        name = "Box of 100",
        abbreviation = "bx100",
        conversionNumerator = 100L,
        conversionDenominator = 1L,
        isBaseUnit = false,
        isPurchaseUnit = true,
        isDispensingUnit = true,
        createdAt = testTimestamp,
        updatedAt = testTimestamp
    )

    private val priceConfigBox = UnitPriceConfig(
        id = "PRICE-BOX-100",
        productUnitId = unitBox100.id,
        sellingPrice = Money.ofMinor(50_000L),
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

        db = Room.inMemoryDatabaseBuilder(
            context,
            CoreDatabase::class.java
        )
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

        productMasterDao.insertProduct(product)

        productMasterDao.insertUnits(
            listOf(
                unitCapsule,
                unitBox100
            )
        )

        productMasterDao.insertPriceConfig(priceConfigBox)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createReceipt(
        receiptNumber: String,
        id: String = "ID-$receiptNumber"
    ): GoodsReceipt {
        return GoodsReceipt(
            id = id,
            receiptNumber = receiptNumber,
            supplierId = supplier.id,
            status = GoodsReceipt.STATUS_DRAFT,
            receivedAt = testTimestamp,
            receivedByUserId = "USER-STORE-01",
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
    }

    private fun createItem(
        receipt: GoodsReceipt,
        quantity: Long,
        totalCostMinor: Long,
        batchNumber: String,
        expiryDateInt: Int,
        lineIndex: Int = 0,
        productId: String = product.id,
        receivingUnitId: String = unitBox100.id
    ): GoodsReceiptItem {
        require(quantity > 0L)
        require(totalCostMinor >= 0L)

        /*
         * unitCost remains an acquisition-context snapshot.
         *
         * totalCost is the authoritative monetary amount. The integration
         * tests intentionally use totals divisible by quantity here except
         * where a dedicated test explicitly exercises indivisible totals.
         */
        val unitCostMinor = totalCostMinor / quantity

        return GoodsReceiptItem(
            id = "ITEM-${receipt.receiptNumber}-$lineIndex",
            goodsReceiptId = receipt.id,
            lineIndex = lineIndex,
            productId = productId,
            receivingUnitId = receivingUnitId,
            receivedQuantity = Quantity.of(
                quantity,
                QuantityScale.SCALE_0
            ),
            unitCost = Money.ofMinor(unitCostMinor),
            totalCost = Money.ofMinor(totalCostMinor),
            batchNumber = batchNumber,
            expiryDateInt = expiryDateInt,
            createdAt = testTimestamp,
            updatedAt = testTimestamp
        )
    }

    private fun commitReceipt(
        receipt: GoodsReceipt,
        items: List<GoodsReceiptItem>
    ) {
        receivingPersistenceService.commitReceipt(
            receipt = receipt,
            items = items,
            productsById = mapOf(product.id to product),
            unitsById = mapOf(
                unitCapsule.id to unitCapsule,
                unitBox100.id to unitBox100
            ),
            commitTimestamp = testTimestamp
        )
    }

    private fun seedStock(
        batchNumber: String,
        expiryDateInt: Int,
        boxes: Long,
        totalCostMinor: Long,
        receiptNumber: String = "REC-$batchNumber"
    ) {
        val receipt = createReceipt(receiptNumber)

        val item = createItem(
            receipt = receipt,
            quantity = boxes,
            totalCostMinor = totalCostMinor,
            batchNumber = batchNumber,
            expiryDateInt = expiryDateInt
        )

        commitReceipt(
            receipt = receipt,
            items = listOf(item)
        )
    }

    @Test
    fun testA_receivingCreatesExactBatchCostLayerAndMovement() {
        seedStock(
            batchNumber = "BATCH-A-01",
            expiryDateInt = 20271231,
            boxes = 10L,
            totalCostMinor = 300_000L,
            receiptNumber = "REC-A-01"
        )

        val receipt = goodsReceiptDao.getReceiptByNumber("REC-A-01")

        assertNotNull(receipt)
        assertTrue(receipt!!.isCommitted)

        val batches = stockBatchDao.getBatchesForProduct(product.id)

        assertEquals(1, batches.size)
        assertEquals(
            "BATCH-A-01",
            batches[0].batchNumber
        )

        val layers =
            inventoryCostLayerDao.getActiveLayersForProduct(product.id)

        assertEquals(1, layers.size)

        assertEquals(
            1_000L,
            layers[0].initialQuantity.storageUnits
        )

        assertEquals(
            1_000L,
            layers[0].remainingQuantity.storageUnits
        )

        assertEquals(
            Money.ofMinor(300L),
            layers[0].acquisitionUnitCost
        )

        val movements =
            stockMovementDao.getMovementsForProduct(product.id)

        assertEquals(1, movements.size)

        assertEquals(
            StockMovement.TYPE_PURCHASE_RECEIPT,
            movements[0].movementType
        )

        assertEquals(
            1_000L,
            movements[0].quantity.storageUnits
        )

        assertEquals(
            1_000L,
            stockMovementDao.getPhysicalStockUnitsForProduct(
                product.id
            )
        )
    }

    @Test
    fun testB_receivingFailureRollsBackEntireTransaction() {
        val receipt = createReceipt("REC-FAIL-01")

        val validItem = createItem(
            receipt = receipt,
            quantity = 5L,
            totalCostMinor = 150_000L,
            batchNumber = "BATCH-ROLLBACK-01",
            expiryDateInt = 20271231,
            lineIndex = 0
        )

        val invalidItem = createItem(
            receipt = receipt,
            quantity = 5L,
            totalCostMinor = 150_000L,
            batchNumber = "BATCH-ROLLBACK-02",
            expiryDateInt = 20271231,
            lineIndex = 1,
            productId = "NON-EXISTENT-PRODUCT"
        )

        try {
            commitReceipt(
                receipt,
                listOf(
                    validItem,
                    invalidItem
                )
            )

            fail("Expected receiving transaction to fail")
        } catch (_: Exception) {
            // Expected.
        }

        assertEquals(
            0,
            goodsReceiptDao
                .getItemsForReceipt(receipt.id)
                .size
        )

        assertEquals(
            0,
            stockBatchDao
                .getBatchesForProduct(product.id)
                .size
        )

        assertEquals(
            0,
            inventoryCostLayerDao
                .getActiveLayersForProduct(product.id)
                .size
        )

        assertEquals(
            0L,
            stockMovementDao
                .getPhysicalStockUnitsForProduct(product.id)
        )
    }

    @Test
    fun testC_fefoSelectsEarliestEligibleBatch() {
        seedStock(
            "BATCH-C-LATER",
            20281231,
            5L,
            150_000L,
            "REC-C-01"
        )

        seedStock(
            "BATCH-C-EARLIER",
            20270630,
            5L,
            200_000L,
            "REC-C-02"
        )

        val result = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-C-01",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = product.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity =
                            Quantity.of(
                                2L,
                                QuantityScale.SCALE_0
                            )
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 10_000L
            )
        )

        val allocation = result.allocations.first()

        val earlierBatch = stockBatchDao.findMatchingBatch(
            product.id,
            "BATCH-C-EARLIER",
            20270630
        )

        assertNotNull(earlierBatch)

        assertEquals(
            earlierBatch!!.id,
            allocation.stockBatchId
        )

        assertEquals(
            200L,
            allocation.allocatedQuantity.storageUnits
        )

        val layer =
            inventoryCostLayerDao.getLayerById(
                allocation.inventoryCostLayerId
            )!!

        assertEquals(
            300L,
            layer.remainingQuantity.storageUnits
        )

        val movements =
            stockMovementDao.getMovementsBySourceRef("SALE-C-01")

        assertEquals(1, movements.size)

        assertEquals(
            -200L,
            movements[0].quantity.storageUnits
        )

        assertEquals(
            StockMovement.TYPE_SALE,
            movements[0].movementType
        )

        assertEquals(
            800L,
            stockMovementDao.getPhysicalStockUnitsForProduct(
                product.id
            )
        )
    }

    @Test
    fun testD_insufficientPhysicalStockCommitsNothing() {
        seedStock(
            "BATCH-D-01",
            20271231,
            1L,
            30_000L
        )

        try {
            consumptionService.consumeStock(
                ConsumptionRequest(
                    saleNumber = "SALE-D-FAIL",
                    items = listOf(
                        ConsumptionLineRequest(
                            productId = product.id,
                            dispensingUnitId = unitBox100.id,
                            requestedQuantity =
                                Quantity.of(
                                    5L,
                                    QuantityScale.SCALE_0
                                )
                        )
                    ),
                    facilityCalendarDate = testCalendarDate,
                    transactionTimestamp = testTimestamp + 10_000L
                )
            )

            fail("Expected insufficient stock")
        } catch (_: InsufficientStockException) {
            // Expected.
        }

        assertEquals(
            0,
            stockAllocationDao
                .getAllocationsForSale("SALE-D-FAIL")
                .size
        )

        assertEquals(
            100L,
            stockMovementDao.getPhysicalStockUnitsForProduct(
                product.id
            )
        )

        assertEquals(
            100L,
            inventoryCostLayerDao
                .getActiveLayersForProduct(product.id)
                .first()
                .remainingQuantity
                .storageUnits
        )
    }

    @Test
    fun testE_costLayerFailureDoesNotCommitSale() {
        seedStock(
            "BATCH-E-01",
            20271231,
            2L,
            60_000L
        )

        val layer =
            inventoryCostLayerDao
                .getActiveLayersForProduct(product.id)
                .first()

        inventoryCostLayerDao.decrementRemainingQuantity(
            layer.id,
            150L,
            testTimestamp
        )

        try {
            consumptionService.consumeStock(
                ConsumptionRequest(
                    saleNumber = "SALE-E-FAIL",
                    items = listOf(
                        ConsumptionLineRequest(
                            productId = product.id,
                            dispensingUnitId = unitBox100.id,
                            requestedQuantity =
                                Quantity.of(
                                    1L,
                                    QuantityScale.SCALE_0
                                )
                        )
                    ),
                    facilityCalendarDate = testCalendarDate,
                    transactionTimestamp = testTimestamp + 10_000L
                )
            )

            fail("Expected cost-layer allocation failure")
        } catch (_: Exception) {
            // Expected.
        }

        assertEquals(
            0,
            stockAllocationDao
                .getAllocationsForSale("SALE-E-FAIL")
                .size
        )

        assertEquals(
            50L,
            inventoryCostLayerDao
                .getLayerById(layer.id)!!
                .remainingQuantity
                .storageUnits
        )
    }

    @Test
    fun testF_expiredStockIsRejected() {
        seedStock(
            "BATCH-EXPIRED",
            20260228,
            5L,
            150_000L
        )

        try {
            consumptionService.consumeStock(
                ConsumptionRequest(
                    saleNumber = "SALE-EXPIRED",
                    items = listOf(
                        ConsumptionLineRequest(
                            productId = product.id,
                            dispensingUnitId = unitBox100.id,
                            requestedQuantity =
                                Quantity.of(
                                    1L,
                                    QuantityScale.SCALE_0
                                )
                        )
                    ),
                    facilityCalendarDate = testCalendarDate,
                    transactionTimestamp = testTimestamp + 10_000L
                )
            )

            fail("Expected expired stock to be rejected")
        } catch (_: InsufficientStockException) {
            // Expected.
        }

        assertEquals(
            0,
            stockAllocationDao
                .getAllocationsForSale("SALE-EXPIRED")
                .size
        )

        assertEquals(
            500L,
            stockMovementDao.getPhysicalStockUnitsForProduct(
                product.id
            )
        )
    }

    @Test
    fun testG_multiLayerConsumptionCalculatesExactCogs() {
        seedStock(
            "BATCH-G-01",
            20271231,
            1L,
            20_000L,
            "REC-G-01"
        )

        seedStock(
            "BATCH-G-02",
            20281231,
            2L,
            70_000L,
            "REC-G-02"
        )

        val result = consumptionService.consumeStock(
            ConsumptionRequest(
                saleNumber = "SALE-G-01",
                items = listOf(
                    ConsumptionLineRequest(
                        productId = product.id,
                        dispensingUnitId = unitBox100.id,
                        requestedQuantity =
                            Quantity.of(
                                2L,
                                QuantityScale.SCALE_0
                            )
                    )
                ),
                facilityCalendarDate = testCalendarDate,
                transactionTimestamp = testTimestamp + 10_000L
            )
        )

        assertEquals(
            2,
            result.allocations.size
        )

        assertEquals(
            Money.ofMinor(20_000L),
            result.allocations[0].allocatedCost
        )

        assertEquals(
            Money.ofMinor(35_000L),
            result.allocations[1].allocatedCost
        )

        assertEquals(
            Money.ofMinor(55_000L),
            result.totalCogs
        )

        assertEquals(
            0L,
            inventoryCostLayerDao
                .getLayerById(
                    result.allocations[0].inventoryCostLayerId
                )!!
                .remainingQuantity
                .storageUnits
        )

        assertEquals(
            100L,
            inventoryCostLayerDao
                .getLayerById(
                    result.allocations[1].inventoryCostLayerId
                )!!
                .remainingQuantity
                .storageUnits
        )
    }

    @Test
    fun testH_duplicateReceiptAndSaleNumbersCannotDoubleCount() {
        val firstReceipt = createReceipt(
            receiptNumber = "REC-H-01",
            id = "ID-REC-H-01"
        )

        val firstItem = createItem(
            receipt = firstReceipt,
            quantity = 2L,
            totalCostMinor = 60_000L,
            batchNumber = "BATCH-H-01",
            expiryDateInt = 20271231
        )

        commitReceipt(
            firstReceipt,
            listOf(firstItem)
        )

        val duplicateReceipt = createReceipt(
            receiptNumber = "REC-H-01",
            id = "ID-REC-H-02"
        )

        val duplicateItem = createItem(
            receipt = duplicateReceipt,
            quantity = 2L,
            totalCostMinor = 60_000L,
            batchNumber = "BATCH-H-02",
            expiryDateInt = 20281231
        )

        try {
            commitReceipt(
                duplicateReceipt,
                listOf(duplicateItem)
            )

            fail("Expected duplicate receipt number to be rejected")
        } catch (_: Exception) {
            // Expected.
        }

        assertEquals(
            1,
            goodsReceiptDao.getReceiptByNumber("REC-H-01")?.let { 1 } ?: 0
        )

        assertEquals(
            1,
            stockBatchDao
                .getBatchesForProduct(product.id)
                .size
        )

        val request = ConsumptionRequest(
            saleId = "SALE-ID-H-01",
            saleNumber = "SALE-H-01",
            items = listOf(
                ConsumptionLineRequest(
                    productId = product.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity =
                        Quantity.of(
                            1L,
                            QuantityScale.SCALE_0
                        )
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 20_000L
        )

        consumptionService.consumeStock(request)

        try {
            consumptionService.consumeStock(request)

            fail("Expected duplicate sale number to be rejected")
        } catch (_: Exception) {
            // Expected.
        }

        assertNotNull(
            saleDao.getSaleByNumber("SALE-H-01")
        )

        assertEquals(
            1,
            stockAllocationDao
                .getAllocationsForSale("SALE-H-01")
                .size
        )

        assertEquals(
            100L,
            stockMovementDao.getPhysicalStockUnitsForProduct(
                product.id
            )
        )
    }

    @Test
    fun testI_concurrentConsumptionCannotOverAllocate() {
        seedStock(
            "BATCH-I-01",
            20271231,
            1L,
            30_000L
        )

        val startGate = CountDownLatch(1)
        val finishedGate = CountDownLatch(2)
        val successCount = AtomicInteger(0)

        val request1 = ConsumptionRequest(
            saleId = "SALE-ID-I-01",
            saleNumber = "SALE-I-01",
            items = listOf(
                ConsumptionLineRequest(
                    productId = product.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity =
                        Quantity.of(
                            1L,
                            QuantityScale.SCALE_0
                        )
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 30_000L
        )

        val request2 = ConsumptionRequest(
            saleId = "SALE-ID-I-02",
            saleNumber = "SALE-I-02",
            items = listOf(
                ConsumptionLineRequest(
                    productId = product.id,
                    dispensingUnitId = unitBox100.id,
                    requestedQuantity =
                        Quantity.of(
                            1L,
                            QuantityScale.SCALE_0
                        )
                )
            ),
            facilityCalendarDate = testCalendarDate,
            transactionTimestamp = testTimestamp + 30_001L
        )

        val thread1 = Thread {
            try {
                startGate.await()
                consumptionService.consumeStock(request1)
                successCount.incrementAndGet()
            } catch (_: Exception) {
                // One concurrent consumer is expected to lose the race.
            } finally {
                finishedGate.countDown()
            }
        }

        val thread2 = Thread {
            try {
                startGate.await()
                consumptionService.consumeStock(request2)
                successCount.incrementAndGet()
            } catch (_: Exception) {
                // One concurrent consumer is expected to lose the race.
            } finally {
                finishedGate.countDown()
            }
        }

        thread1.start()
        thread2.start()

        startGate.countDown()

        finishedGate.await()

        assertEquals(
            1,
            successCount.get()
        )

        assertEquals(
            0L,
            stockMovementDao.getPhysicalStockUnitsForProduct(
                product.id
            )
        )

        val allocationsForFirstSale =
            stockAllocationDao.getAllocationsForSale(
                request1.saleNumber
            )

        val allocationsForSecondSale =
            stockAllocationDao.getAllocationsForSale(
                request2.saleNumber
            )

        assertEquals(
            1,
            allocationsForFirstSale.size +
                allocationsForSecondSale.size
        )
    }
}
