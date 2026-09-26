package core.domain.testutil

import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.InventoryCostLayer
import core.domain.model.PharmaceuticalDetail
import core.domain.model.ProductImage
import core.domain.model.ProductIngredient
import core.domain.model.ProductIdentifier
import core.domain.model.ProductEntity
import core.domain.model.ProductAttribute
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.RationalCost
import core.domain.model.Sale
import core.domain.model.SaleItem
import core.domain.model.StockAllocation
import core.domain.model.StockBatch
import core.domain.model.StockMovement
import core.domain.model.UnitPriceConfig
import core.domain.persistence.GoodsReceiptDao
import core.domain.persistence.InventoryCostLayerDao
import core.domain.persistence.ProductMasterDao
import core.domain.persistence.SaleDao
import core.domain.persistence.StockAllocationDao
import core.domain.persistence.StockBatchDao
import core.domain.persistence.StockMovementDao
import core.domain.persistence.TransactionRunner
import java.math.BigInteger

/**
 * In-memory transactional test fixture implementing all DAO contracts and [TransactionRunner]
 * with exact rollback semantics.
 */
class FakeCoreDatabase : TransactionRunner {

    val receipts = mutableMapOf<String, GoodsReceipt>()
    val receiptItems = mutableMapOf<String, MutableList<GoodsReceiptItem>>()
    val batches = mutableMapOf<String, StockBatch>()
    val costLayers = mutableMapOf<String, InventoryCostLayer>()
    val movements = mutableListOf<StockMovement>()
    val allocations = mutableListOf<StockAllocation>()
    val sales = mutableMapOf<String, Sale>()
    val saleItems = mutableMapOf<String, MutableList<SaleItem>>()
    val products = mutableMapOf<String, ProductMaster>()
    val pharmaceuticalDetails = mutableMapOf<String, PharmaceuticalDetail>()
    val productImages = mutableMapOf<String, ProductImage>()
    val productIngredients = mutableMapOf<String, ProductIngredient>()
    val productIdentifiers = mutableMapOf<String, ProductIdentifier>()
    val productEntities = mutableMapOf<String, ProductEntity>()
    val productAttributes = mutableMapOf<String, ProductAttribute>()
    val units = mutableMapOf<String, ProductUnit>()
    val priceConfigs = mutableMapOf<String, UnitPriceConfig>()

    override fun <T> runInTransaction(block: () -> T): T {
        val snapReceipts = receipts.toMap()
        val snapReceiptItems = receiptItems.mapValues { it.value.toMutableList() }.toMutableMap()
        val snapBatches = batches.toMap()
        val snapCostLayers = costLayers.toMap()
        val snapMovements = movements.toMutableList()
        val snapAllocations = allocations.toMutableList()
        val snapSales = sales.toMap()
        val snapSaleItems = saleItems.mapValues { it.value.toMutableList() }.toMutableMap()
        val snapProducts = products.toMap()
        val snapPharmaceuticalDetails = pharmaceuticalDetails.toMap()
        val snapProductImages = productImages.toMap()
        val snapProductIngredients = productIngredients.toMap()
        val snapProductIdentifiers = productIdentifiers.toMap()
        val snapProductEntities = productEntities.toMap()
        val snapProductAttributes = productAttributes.toMap()
        val snapUnits = units.toMap()
        val snapPriceConfigs = priceConfigs.toMap()

        try {
            return block()
        } catch (e: Throwable) {
            receipts.clear()
            receipts.putAll(snapReceipts)
            receiptItems.clear()
            receiptItems.putAll(snapReceiptItems)
            batches.clear()
            batches.putAll(snapBatches)
            costLayers.clear()
            costLayers.putAll(snapCostLayers)
            movements.clear()
            movements.addAll(snapMovements)
            allocations.clear()
            allocations.addAll(snapAllocations)
            sales.clear()
            sales.putAll(snapSales)
            saleItems.clear()
            saleItems.putAll(snapSaleItems)
            products.clear()
            products.putAll(snapProducts)
            pharmaceuticalDetails.clear()
            pharmaceuticalDetails.putAll(snapPharmaceuticalDetails)
            productImages.clear()
            productImages.putAll(snapProductImages)
            productIngredients.clear()
            productIngredients.putAll(snapProductIngredients)
            productIdentifiers.clear()
            productIdentifiers.putAll(snapProductIdentifiers)
            productEntities.clear()
            productEntities.putAll(snapProductEntities)
            productAttributes.clear()
            productAttributes.putAll(snapProductAttributes)
            units.clear()
            units.putAll(snapUnits)
            priceConfigs.clear()
            priceConfigs.putAll(snapPriceConfigs)
            throw e
        }
    }

    val goodsReceiptDao = object : GoodsReceiptDao {
        override fun insertReceipt(receipt: GoodsReceipt) {
            if (receipts.containsKey(receipt.id)) {
                throw IllegalStateException("Duplicate receipt id: ${receipt.id}")
            }

            if (receipts.values.any { it.receiptNumber == receipt.receiptNumber }) {
                throw IllegalStateException(
                    "Duplicate receipt number constraint violation: ${receipt.receiptNumber}"
                )
            }

            receipts[receipt.id] = receipt
        }

        override fun insertReceiptItems(items: List<GoodsReceiptItem>) {
            for (item in items) {
                receiptItems
                    .getOrPut(item.goodsReceiptId) { mutableListOf() }
                    .add(item)
            }
        }

        override fun updateReceipt(receipt: GoodsReceipt) {
            receipts[receipt.id] = receipt
        }

        override fun getAllReceipts(): List<GoodsReceipt> =
            receipts.values.sortedByDescending { it.receivedAt }

        override fun getReceiptById(id: String): GoodsReceipt? =
            receipts[id]

        override fun getReceiptByNumber(receiptNumber: String): GoodsReceipt? =
            receipts.values.firstOrNull { it.receiptNumber == receiptNumber }

        override fun getItemsForReceipt(receiptId: String): List<GoodsReceiptItem> =
            receiptItems[receiptId]?.sortedBy { it.lineIndex } ?: emptyList()
    }

    val stockBatchDao = object : StockBatchDao {
        override fun insertBatch(batch: StockBatch) {
            if (batches.containsKey(batch.id)) {
                throw IllegalStateException("Duplicate batch id: ${batch.id}")
            }

            if (
                batches.values.any {
                    it.productId == batch.productId &&
                        it.batchNumber == batch.batchNumber &&
                        it.expiryDateInt == batch.expiryDateInt
                }
            ) {
                throw IllegalStateException(
                    "Duplicate batch constraint violation: ${batch.batchNumber}"
                )
            }

            batches[batch.id] = batch
        }

        override fun insertBatches(batches: List<StockBatch>) {
            batches.forEach { insertBatch(it) }
        }

        override fun getAllBatches(): List<StockBatch> =
            batches.values.sortedWith(
                compareBy<StockBatch> { it.expiryDateInt }
                    .thenBy { it.createdAt }
            )

        override fun getBatchById(id: String): StockBatch? =
            batches[id]

        override fun getBatchesForProduct(productId: String): List<StockBatch> =
            batches.values
                .filter { it.productId == productId }
                .sortedWith(
                    compareBy<StockBatch> { it.expiryDateInt }
                        .thenBy { it.createdAt }
                )

        override fun findMatchingBatch(
            productId: String,
            batchNumber: String,
            expiryDateInt: Int
        ): StockBatch? =
            batches.values.firstOrNull {
                it.productId == productId &&
                    it.batchNumber == batchNumber &&
                    it.expiryDateInt == expiryDateInt
            }
    }

    val inventoryCostLayerDao = object : InventoryCostLayerDao {
        override fun insertLayer(layer: InventoryCostLayer) {
            if (costLayers.containsKey(layer.id)) {
                throw IllegalStateException("Duplicate cost layer id: ${layer.id}")
            }

            costLayers[layer.id] = layer
        }

        override fun insertLayers(layers: List<InventoryCostLayer>) {
            layers.forEach { insertLayer(it) }
        }

        override fun updateLayer(layer: InventoryCostLayer) {
            costLayers[layer.id] = layer
        }

        override fun updateLayers(layers: List<InventoryCostLayer>) {
            layers.forEach { updateLayer(it) }
        }

        override fun getLayerById(id: String): InventoryCostLayer? =
            costLayers[id]

        override fun getActiveLayersForBatch(batchId: String): List<InventoryCostLayer> =
            costLayers.values
                .filter {
                    it.stockBatchId == batchId &&
                        it.remainingQuantity.isPositive
                }
                .sortedWith(
                    compareBy<InventoryCostLayer> { it.acquiredAt }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                )

        override fun getActiveLayersForProduct(productId: String): List<InventoryCostLayer> =
            costLayers.values
                .filter {
                    it.productId == productId &&
                        it.remainingQuantity.isPositive
                }
                .sortedWith(
                    compareBy<InventoryCostLayer> { it.acquiredAt }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                )

        override fun getAllActiveLayers(): List<InventoryCostLayer> =
            costLayers.values
                .filter {
                    it.remainingQuantity.isPositive
                }
                .sortedWith(
                    compareBy<InventoryCostLayer> { it.acquiredAt }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                )

        override fun getLayersForReceiptRef(
            receiptRef: String
        ): List<InventoryCostLayer> =
            costLayers.values.filter { it.sourceReceiptRef == receiptRef }

        override fun decrementRemainingQuantity(
            layerId: String,
            decrementUnits: Long,
            updatedAt: Long
        ): Int {
            val layer = costLayers[layerId] ?: return 0

            if (layer.remainingQuantity.storageUnits < decrementUnits) {
                return 0
            }

            val newQty = layer.remainingQuantity.copy(
                storageUnits =
                    layer.remainingQuantity.storageUnits - decrementUnits
            )

            costLayers[layerId] = layer.copy(
                remainingQuantity = newQty,
                updatedAt = updatedAt
            )

            return 1
        }

        override fun incrementRemainingQuantity(
            layerId: String,
            incrementUnits: Long,
            updatedAt: Long
        ): Int {
            val layer = costLayers[layerId] ?: return 0

            if (
                layer.remainingQuantity.storageUnits + incrementUnits >
                layer.initialQuantity.storageUnits
            ) {
                return 0
            }

            val newQty = layer.remainingQuantity.copy(
                storageUnits =
                    layer.remainingQuantity.storageUnits + incrementUnits
            )

            costLayers[layerId] = layer.copy(
                remainingQuantity = newQty,
                updatedAt = updatedAt
            )

            return 1
        }
    }

    val stockMovementDao = object : StockMovementDao {
        override fun insertMovement(movement: StockMovement) {
            movements.add(movement)
        }

        override fun insertMovements(movements: List<StockMovement>) {
            this@FakeCoreDatabase.movements.addAll(movements)
        }

        override fun getAllMovements(): List<StockMovement> =
            movements.sortedByDescending { it.occurredAt }

        override fun getMovementsForProduct(
            productId: String
        ): List<StockMovement> =
            movements
                .filter { it.productId == productId }
                .sortedBy { it.occurredAt }

        override fun getMovementsForBatch(
            batchId: String
        ): List<StockMovement> =
            movements
                .filter { it.stockBatchId == batchId }
                .sortedBy { it.occurredAt }

        override fun getPhysicalStockUnitsForProduct(
            productId: String
        ): Long =
            movements
                .filter { it.productId == productId }
                .sumOf { it.quantity.storageUnits }

        override fun getPhysicalStockUnitsForBatch(
            batchId: String
        ): Long =
            movements
                .filter { it.stockBatchId == batchId }
                .sumOf { it.quantity.storageUnits }

        override fun getMovementsBySourceRef(
            ref: String
        ): List<StockMovement> =
            movements.filter { it.sourceTransactionRef == ref }
    }

    val stockAllocationDao = object : StockAllocationDao {
        override fun insertAllocation(allocation: StockAllocation) {
            allocations.add(allocation)
        }

        override fun insertAllocations(
            allocations: List<StockAllocation>
        ) {
            this@FakeCoreDatabase.allocations.addAll(allocations)
        }

        override fun getAllocationsForSale(
            saleId: String
        ): List<StockAllocation> =
            allocations
                .filter { it.consumptionTransactionId == saleId }
                .sortedBy { it.allocatedAt }

        override fun getAllocationsForSaleItem(
            saleItemId: String
        ): List<StockAllocation> =
            allocations
                .filter { it.consumptionItemId == saleItemId }
                .sortedBy { it.allocatedAt }

        override fun getAllocationsForCostLayer(
            layerId: String
        ): List<StockAllocation> =
            allocations
                .filter { it.inventoryCostLayerId == layerId }
                .sortedBy { it.allocatedAt }

        override fun getEffectiveAllocationsForProduct(
            productId: String
        ): List<StockAllocation> =
            allocations
                .filter { allocation ->
                    allocation.productId == productId &&
                        sales[allocation.consumptionTransactionId]?.status ==
                        Sale.STATUS_COMPLETED
                }
                .sortedBy { it.allocatedAt }

        override fun getEffectiveAllocationsForSale(
            saleId: String
        ): List<StockAllocation> =
            if (sales[saleId]?.status == Sale.STATUS_COMPLETED) {
                allocations
                    .filter {
                        it.consumptionTransactionId == saleId
                    }
                    .sortedBy { it.allocatedAt }
            } else {
                emptyList()
            }
    }

    val saleDao = object : SaleDao {
        override fun insertSale(sale: Sale) {
            if (sales.containsKey(sale.id)) {
                throw IllegalStateException("Duplicate sale id: ${sale.id}")
            }

            if (sales.values.any { it.saleNumber == sale.saleNumber }) {
                throw IllegalStateException(
                    "Duplicate sale number constraint violation: ${sale.saleNumber}"
                )
            }

            sales[sale.id] = sale
        }

        override fun insertSaleItems(items: List<SaleItem>) {
            for (item in items) {
                saleItems
                    .getOrPut(item.saleId) { mutableListOf() }
                    .add(item)
            }
        }

        override fun updateSale(sale: Sale) {
            sales[sale.id] = sale
        }

        override fun getAllSales(): List<Sale> =
            sales.values.sortedByDescending { it.occurredAt }

        override fun getSaleById(id: String): Sale? =
            sales[id]

        override fun getSaleByNumber(saleNumber: String): Sale? =
            sales.values.firstOrNull { it.saleNumber == saleNumber }

        override fun getItemsForSale(
            saleId: String
        ): List<SaleItem> =
            saleItems[saleId]?.sortedBy { it.lineIndex } ?: emptyList()
    }

    val productMasterDao = object : ProductMasterDao {
        override fun insertProduct(product: ProductMaster) {
            products[product.id] = product
        }

        override fun insertPharmaceuticalDetail(detail: PharmaceuticalDetail) {
            pharmaceuticalDetails[detail.productId] = detail
        }

        override fun getPharmaceuticalDetailForProduct(productId: String): PharmaceuticalDetail? =
            pharmaceuticalDetails[productId]

        override fun insertProductImage(image: ProductImage) {
            productImages[image.id] = image
        }

        override fun insertProductImages(images: List<ProductImage>) {
            images.forEach { productImages[it.id] = it }
        }

        override fun insertProductIngredients(ingredients: List<ProductIngredient>) {
            ingredients.forEach { productIngredients[it.id] = it }
        }

        override fun insertProductIdentifiers(identifiers: List<ProductIdentifier>) {
            identifiers.forEach { identifier ->
                if (productIdentifiers.values.any {
                    it.identifierType == identifier.identifierType &&
                        it.normalizedValue == identifier.normalizedValue
                }) {
                    throw IllegalStateException("Duplicate product identifier")
                }
                productIdentifiers[identifier.id] = identifier
            }
        }

        override fun insertProductEntities(entities: List<ProductEntity>) {
            entities.forEach { productEntities[it.id] = it }
        }

        override fun insertProductAttributes(attributes: List<ProductAttribute>) {
            attributes.forEach { productAttributes[it.id] = it }
        }

        override fun getIngredientsForProduct(productId: String): List<ProductIngredient> =
            productIngredients.values.filter { it.productId == productId }.sortedBy { it.sequence }

        override fun getIdentifiersForProduct(productId: String): List<ProductIdentifier> =
            productIdentifiers.values.filter { it.productId == productId }.sortedWith(compareByDescending<ProductIdentifier> { it.isPrimary }.thenBy { it.identifierType })

        override fun getEntitiesForProduct(productId: String): List<ProductEntity> =
            productEntities.values.filter { it.productId == productId }.sortedWith(compareBy<ProductEntity> { it.role }.thenBy { it.sequence })

        override fun getAttributesForProduct(productId: String): List<ProductAttribute> =
            productAttributes.values.filter { it.productId == productId }.sortedBy { it.definitionKey }

        override fun insertUnits(units: List<ProductUnit>) {
            units.forEach { this@FakeCoreDatabase.units[it.id] = it }
        }

        override fun insertUnit(unit: ProductUnit) {
            units[unit.id] = unit
        }

        override fun updateProduct(product: ProductMaster) {
            products[product.id] = product
        }

        override fun insertPriceConfig(config: UnitPriceConfig) {
            priceConfigs[config.productUnitId] = config
        }

        override fun savePriceConfig(config: UnitPriceConfig) {
            priceConfigs[config.productUnitId] = config
        }

        override fun getProductById(id: String): ProductMaster? =
            products[id]

        override fun getAllProducts(): List<ProductMaster> =
            products.values.sortedWith(
                compareByDescending<ProductMaster> { it.isActive }
                    .thenBy { it.displayName }
            )

        override fun getBaseUnitForProduct(
            productId: String
        ): ProductUnit? =
            units.values.firstOrNull {
                it.productId == productId && it.isBaseUnit
            }

        override fun getUnitById(unitId: String): ProductUnit? =
            units[unitId]

        override fun getUnitsForProduct(
            productId: String
        ): List<ProductUnit> =
            units.values.filter { it.productId == productId }

        override fun getAllUnits(): List<ProductUnit> =
            units.values.toList()

        override fun getActivePriceConfigForUnit(
            unitId: String
        ): UnitPriceConfig? =
            priceConfigs[unitId]?.takeIf { it.isActive }

        override fun getAllPriceConfigs(): List<UnitPriceConfig> =
            priceConfigs.values.toList()
    }
}
