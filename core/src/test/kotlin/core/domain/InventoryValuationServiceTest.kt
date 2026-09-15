package core.domain

import core.domain.model.InventoryCostLayer
import core.domain.model.Quantity
import core.domain.model.QuantityScale
import core.domain.model.RationalCost
import core.domain.persistence.InventoryCostLayerDao
import core.domain.reporting.InventoryValuationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class InventoryValuationServiceTest {

    @Test
    fun `indivisible acquisition cost is preserved exactly`() {
        val layer = layer(
            id = "L1",
            initialQuantity = 3L,
            remainingQuantity = 3L,
            acquisitionUnitCost = RationalCost(
                numerator = BigInteger.valueOf(100L),
                denominator = BigInteger.valueOf(3L)
            )
        )

        val service = InventoryValuationService(
            FakeInventoryCostLayerDao(listOf(layer))
        )

        val valuation = service.calculateLayerValuation(layer)

        assertEquals(
            RationalCost(
                numerator = BigInteger.valueOf(100L),
                denominator = BigInteger.valueOf(3L)
            ).multiply(
                BigInteger.valueOf(3L)
            ),
            valuation
        )

        assertEquals(
            "100/1",
            valuation.toString()
        )
    }

    @Test
    fun `partial remaining quantity is valued from exact original layer cost`() {
        val layer = layer(
            id = "L1",
            initialQuantity = 3L,
            remainingQuantity = 2L,
            acquisitionUnitCost = RationalCost(
                numerator = BigInteger.valueOf(100L),
                denominator = BigInteger.valueOf(3L)
            )
        )

        val service = InventoryValuationService(
            FakeInventoryCostLayerDao(listOf(layer))
        )

        assertEquals(
            "200/3",
            service.calculateLayerValuation(layer).toString()
        )
    }

    @Test
    fun `multiple active layers are summed exactly`() {
        val layer1 = layer(
            id = "L1",
            initialQuantity = 3L,
            remainingQuantity = 3L,
            acquisitionUnitCost = RationalCost(
                numerator = BigInteger.valueOf(100L),
                denominator = BigInteger.valueOf(3L)
            )
        )

        val layer2 = layer(
            id = "L2",
            initialQuantity = 2L,
            remainingQuantity = 2L,
            acquisitionUnitCost = RationalCost(
                numerator = BigInteger.valueOf(50L),
                denominator = BigInteger.valueOf(2L)
            )
        )

        val service = InventoryValuationService(
            FakeInventoryCostLayerDao(
                listOf(layer1, layer2)
            )
        )

        assertEquals(
            "150/1",
            service.calculateTotalValuation().toString()
        )
    }

    @Test
    fun `exhausted layer contributes zero valuation`() {
        val layer = layer(
            id = "L1",
            initialQuantity = 3L,
            remainingQuantity = 0L,
            acquisitionUnitCost = exactMinor(100L)
        )

        val service = InventoryValuationService(
            FakeInventoryCostLayerDao(listOf(layer))
        )

        assertEquals(
            "0/1",
            service.calculateTotalValuation().toString()
        )
    }

    @Test
    fun `recurring fractions remain exact when summed across layers`() {
        val layer1 = layer(
            id = "L1",
            initialQuantity = 3L,
            remainingQuantity = 1L,
            acquisitionUnitCost = exactMinor(100L)
        )

        val layer2 = layer(
            id = "L2",
            initialQuantity = 3L,
            remainingQuantity = 1L,
            acquisitionUnitCost = exactMinor(100L)
        )

        val layer3 = layer(
            id = "L3",
            initialQuantity = 3L,
            remainingQuantity = 1L,
            acquisitionUnitCost = exactMinor(100L)
        )

        val service = InventoryValuationService(
            FakeInventoryCostLayerDao(
                listOf(layer1, layer2, layer3)
            )
        )

        assertEquals(
            "100/1",
            service.calculateTotalValuation().toString()
        )
    }

    @Test
    fun `zero active layers produce zero valuation`() {
        val service = InventoryValuationService(
            FakeInventoryCostLayerDao(emptyList())
        )

        assertEquals(
            "0/1",
            service.calculateTotalValuation().toString()
        )
    }

    @Test
    fun `inventory cost layer rejects zero initial quantity`() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryCostLayer(
                id = "INVALID",
                productId = "P1",
                stockBatchId = "B1",
                supplierId = "S1",
                initialQuantity = Quantity(
                    storageUnits = 0L,
                    scale = QuantityScale.SCALE_0
                ),
                remainingQuantity = Quantity(
                    storageUnits = 0L,
                    scale = QuantityScale.SCALE_0
                ),
                acquisitionUnitCost = exactMinor(100L),
                acquiredAt = 1_000L,
                sourceReceiptRef = "R1",
                createdAt = 1_000L,
                updatedAt = 1_000L
            )
        }
    }

    @Test
    fun `inventory cost layer rejects remaining quantity above initial quantity`() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryCostLayer(
                id = "INVALID",
                productId = "P1",
                stockBatchId = "B1",
                supplierId = "S1",
                initialQuantity = Quantity(
                    storageUnits = 3L,
                    scale = QuantityScale.SCALE_0
                ),
                remainingQuantity = Quantity(
                    storageUnits = 4L,
                    scale = QuantityScale.SCALE_0
                ),
                acquisitionUnitCost = exactMinor(100L),
                acquiredAt = 1_000L,
                sourceReceiptRef = "R1",
                createdAt = 1_000L,
                updatedAt = 1_000L
            )
        }
    }

    @Test
    fun `equivalent rational arithmetic is canonicalized exactly`() {
        val oneThird = RationalCost(
            numerator = BigInteger.valueOf(100L),
            denominator = BigInteger.valueOf(3L)
        )

        val twoSixths = RationalCost(
            numerator = BigInteger.valueOf(200L),
            denominator = BigInteger.valueOf(6L)
        )

        val canonicalEquivalent = twoSixths.multiply(
            numerator = BigInteger.ONE,
            denominator = BigInteger.ONE
        )

        assertEquals(
            oneThird,
            canonicalEquivalent
        )

        assertEquals(
            "100/3",
            canonicalEquivalent.toString()
        )
    }

    private fun layer(
        id: String,
        initialQuantity: Long,
        remainingQuantity: Long,
        acquisitionUnitCost: RationalCost
    ): InventoryCostLayer {
        return InventoryCostLayer(
            id = id,
            productId = "P1",
            stockBatchId = "B1",
            supplierId = "S1",
            initialQuantity = Quantity(
                storageUnits = initialQuantity,
                scale = QuantityScale.SCALE_0
            ),
            remainingQuantity = Quantity(
                storageUnits = remainingQuantity,
                scale = QuantityScale.SCALE_0
            ),
            acquisitionUnitCost = acquisitionUnitCost,
            acquiredAt = 1_000L,
            sourceReceiptRef = "R1",
            createdAt = 1_000L,
            updatedAt = 1_000L
        )
    }

    private fun exactMinor(
        amountMinor: Long
    ): RationalCost {
        return RationalCost(
            numerator = BigInteger.valueOf(amountMinor),
            denominator = BigInteger.ONE
        )
    }

    private class FakeInventoryCostLayerDao(
        private val layers: List<InventoryCostLayer>
    ) : InventoryCostLayerDao {

        override fun insertLayer(
            layer: InventoryCostLayer
        ) {
            error("Not required by InventoryValuationServiceTest")
        }

        override fun insertLayers(
            layers: List<InventoryCostLayer>
        ) {
            error("Not required by InventoryValuationServiceTest")
        }

        override fun updateLayer(
            layer: InventoryCostLayer
        ) {
            error("Not required by InventoryValuationServiceTest")
        }

        override fun updateLayers(
            layers: List<InventoryCostLayer>
        ) {
            error("Not required by InventoryValuationServiceTest")
        }

        override fun getLayerById(
            id: String
        ): InventoryCostLayer? {
            return layers.firstOrNull {
                it.id == id
            }
        }

        override fun getActiveLayersForBatch(
            batchId: String
        ): List<InventoryCostLayer> {
            return layers.filter {
                it.stockBatchId == batchId &&
                    it.remainingQuantity.storageUnits > 0L
            }
        }

        override fun getActiveLayersForProduct(
            productId: String
        ): List<InventoryCostLayer> {
            return layers.filter {
                it.productId == productId &&
                    it.remainingQuantity.storageUnits > 0L
            }
        }

        override fun getAllActiveLayers(): List<InventoryCostLayer> {
            return layers.filter {
                it.remainingQuantity.storageUnits > 0L
            }
        }

        override fun getLayersForReceiptRef(
            receiptRef: String
        ): List<InventoryCostLayer> {
            return layers.filter {
                it.sourceReceiptRef == receiptRef
            }
        }

        override fun decrementRemainingQuantity(
            layerId: String,
            decrementUnits: Long,
            updatedAt: Long
        ): Int {
            error("Not required by InventoryValuationServiceTest")
        }

        override fun incrementRemainingQuantity(
            layerId: String,
            incrementUnits: Long,
            updatedAt: Long
        ): Int {
            error("Not required by InventoryValuationServiceTest")
        }
    }
}
