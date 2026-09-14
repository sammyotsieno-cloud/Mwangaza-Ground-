package core.domain.receiving

import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.InventoryCostLayer
import core.domain.model.Money
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.Quantity
import core.domain.model.StockBatch
import core.domain.model.StockMovement
import java.math.BigInteger
import java.util.UUID

/**
 * Domain service governing goods-receipt preparation:
 *
 * ProductMaster
 *      ↓
 * quantity precision + minimum legal transaction increment
 *      ↓
 * ProductUnit
 *      ↓
 * exact commercial-unit → canonical-base-unit conversion
 *      ↓
 * Quantity
 *      ↓
 * StockBatch + InventoryCostLayer + StockMovement
 *
 * This service prepares a deterministic commit bundle.
 *
 * It does NOT:
 * - persist records itself;
 * - calculate current stock from counters;
 * - perform FEFO stock exit;
 * - perform sales/dispensing;
 * - calculate selling prices;
 * - modify historical records.
 *
 * Persistence atomicity belongs to the persistence/transaction layer.
 */
object GoodsReceiptService {

    /**
     * Canonical physical anchor used for products whose stock is intentionally
     * treated as non-batched.
     *
     * This is a physical-batch identity convention, not a cost identity.
     */
    const val COMMODITY_BATCH_NUMBER: String = "COMMODITY"

    /**
     * Generates a deterministic receipt-scoped physical batch identity for
     * supplier-untracked stock.
     *
     * A different receipt therefore cannot accidentally merge into the same
     * supplier-untracked physical batch.
     */
    fun generateUntrackedBatchNumber(
        goodsReceiptId: String,
        lineIndex: Int
    ): String {
        return "UNSPECIFIED-GR-$goodsReceiptId-$lineIndex"
    }

    /**
     * Converts a received commercial-unit quantity into the product's
     * canonical base quantity.
     *
     * IMPORTANT:
     *
     * QuantityScale describes decimal precision of the quantity being entered.
     * ProductUnit describes commercial-unit conversion.
     *
     * These are deliberately kept separate.
     *
     * Mathematical model:
     *
     *     received commercial quantity
     *       = receivedStorageUnits / 10^receivedScale
     *
     *     canonical base quantity
     *       = commercial quantity
     *         × conversionNumerator / conversionDenominator
     *
     *     base storage units
     *       = canonical base quantity × 10^productScale
     *
     * Therefore:
     *
     *     baseStorageUnits =
     *       receivedStorageUnits
     *       × conversionNumerator
     *       × 10^productScale
     *       --------------------------------
     *       10^receivedScale
     *       × conversionDenominator
     *
     * The division MUST be exact.
     *
     * No truncation and no floating-point arithmetic are permitted.
     *
     * Example:
     *
     *     Product:
     *       base unit = tablet
     *       scale = 0
     *
     *     Box:
     *       1 box = 100 tablets
     *
     *     Receive:
     *       2 boxes
     *
     *     Result:
     *       200 tablets
     *
     * Example with liquid:
     *
     *     Product:
     *       base unit = mL
     *       scale = 3
     *
     *     Bottle:
     *       1 bottle = 100 mL
     *
     *     Receive:
     *       1 bottle
     *       represented as 1.000 commercial units
     *
     *     Result:
     *       100,000 storage units = 100 mL
     *
     * @param receivedQuantity quantity expressed in the receiving commercial unit
     * @param receivingUnit commercial unit selected for the receipt line
     * @param baseUnit canonical base unit of the product
     * @param productQuantityScale authoritative precision of the product
     * @param minimumTransactionIncrement authoritative legal transaction increment
     * @param lineIndex receipt line index used for error attribution
     */
    fun convertToBaseQuantity(
        receivedQuantity: Quantity,
        receivingUnit: ProductUnit,
        baseUnit: ProductUnit,
        productQuantityScale: core.domain.model.QuantityScale,
        minimumTransactionIncrement: Quantity,
        lineIndex: Int
    ): Either<ReceivingError, Quantity> {

        if (!baseUnit.isBaseUnit || !baseUnit.representsExactlyOneBaseUnit) {
            return Either.Left(
                ReceivingError.BaseUnitNotFound(
                    productId = baseUnit.productId,
                    lineIndex = lineIndex
                )
            )
        }

        /*
         * The receiving quantity is expressed using the product's configured
         * quantity precision.
         *
         * This prevents one transaction line from silently changing the
         * interpretation of the product's quantity scale.
         */
        if (receivedQuantity.scale != productQuantityScale) {
            return Either.Left(
                ReceivingError.QuantityConversionOverflow(
                    lineIndex = lineIndex,
                    detail =
                        "Received quantity scale ${receivedQuantity.scale.scale} " +
                            "does not match product quantity scale ${productQuantityScale.scale}; " +
                            "conversion would otherwise reinterpret the quantity."
                )
            )
        }

        val numerator = try {
            BigInteger.valueOf(receivedQuantity.storageUnits)
                .multiply(
                    BigInteger.valueOf(
                        receivingUnit.normalizedConversionNumerator
                    )
                )
                .multiply(
                    BigInteger.valueOf(
                        productQuantityScale.multiplier
                    )
                )
        } catch (e: ArithmeticException) {
            return Either.Left(
                ReceivingError.QuantityConversionOverflow(
                    lineIndex = lineIndex,
                    detail = e.message
                        ?: "Overflow while constructing exact conversion numerator"
                )
            )
        }

        val denominator = try {
            BigInteger.valueOf(receivedQuantity.scale.multiplier)
                .multiply(
                    BigInteger.valueOf(
                        receivingUnit.normalizedConversionDenominator
                    )
                )
        } catch (e: ArithmeticException) {
            return Either.Left(
                ReceivingError.QuantityConversionOverflow(
                    lineIndex = lineIndex,
                    detail = e.message
                        ?: "Overflow while constructing exact conversion denominator"
                )
            )
        }

        if (denominator.signum() <= 0) {
            return Either.Left(
                ReceivingError.QuantityConversionOverflow(
                    lineIndex = lineIndex,
                    detail = "Conversion denominator must be positive."
                )
            )
        }

        val division = numerator.divideAndRemainder(denominator)

        /*
         * Never truncate a rational conversion.
         *
         * Example:
         *
         *     1/3 mL at scale 3
         *
         * would require 333.333... storage units and therefore cannot be
         * represented exactly at that scale.
         *
         * The correct behaviour is rejection, not silent truncation.
         */
        if (division[1].signum() != 0) {
            return Either.Left(
                ReceivingError.QuantityConversionOverflow(
                    lineIndex = lineIndex,
                    detail =
                        "Exact commercial-to-base conversion is not representable " +
                            "at product quantity scale ${productQuantityScale.scale}. " +
                            "No rounding or truncation is permitted."
                )
            )
        }

        val baseStorageUnits = try {
            division[0].longValueExact()
        } catch (e: ArithmeticException) {
            return Either.Left(
                ReceivingError.QuantityConversionOverflow(
                    lineIndex = lineIndex,
                    detail =
                        "Exact converted base quantity exceeds Long storage capacity."
                )
            )
        }

        val baseQuantity = Quantity(
            storageUnits = baseStorageUnits,
            scale = productQuantityScale
        )

        /*
         * The physical base quantity must obey the product's minimum legal
         * transaction increment.
         *
         * This is what prevents illegal quantities such as:
         *
         *     0.333 tablet
         *
         * while allowing legitimate quantities such as:
         *
         *     100.5 mL
         *
         * when the product policy permits that increment.
         */
        if (!baseQuantity.isMultipleOf(minimumTransactionIncrement)) {
            return Either.Left(
                ReceivingError.InvalidDiscreteQuantity(
                    lineIndex = lineIndex,
                    rawUnits = baseQuantity.storageUnits,
                    scale = baseQuantity.scale.scale
                )
            )
        }

        if (baseQuantity.storageUnits <= 0L) {
            return Either.Left(
                ReceivingError.InvalidDiscreteQuantity(
                    lineIndex = lineIndex,
                    rawUnits = baseQuantity.storageUnits,
                    scale = baseQuantity.scale.scale
                )
            )
        }

        return Either.Right(baseQuantity)
    }

    /**
     * Calculates exact acquisition-cost tranches using the currently persisted
     * InventoryCostLayer representation.
     *
     * The current InventoryCostLayer model stores an acquisition unit cost.
     * When total acquisition cost is not divisible by the acquired quantity,
     * the quantity is deterministically divided into:
     *
     * - primary quantity at floor(totalCost / quantity)
     * - remainder quantity at floor(totalCost / quantity) + 1 minor unit
     *
     * This guarantees:
     *
     *     sum(quantity × acquisitionUnitCost) == total acquisition cost
     *
     * exactly.
     *
     * Example:
     *
     *     3 tablets purchased for KSh 100.00
     *     = 10,000 cents
     *
     *     floor(10,000 / 3) = 3,333
     *     remainder = 1
     *
     *     2 tablets @ 3,333 cents
     *     1 tablet  @ 3,334 cents
     *
     *     6,666 + 3,334 = 10,000 cents
     *
     * No floating-point arithmetic is used.
     *
     * NOTE:
     * The final acquisition-cost representation remains subject to the later
     * InventoryCostLayer hardening step. This function therefore does not
     * introduce a second financial authority.
     */
    fun calculateCostLayerTranches(
        totalCost: Money,
        baseQuantity: Quantity,
        productId: String,
        stockBatchId: String,
        supplierId: String?,
        sourceReceiptRef: String?,
        acquiredAt: Long,
        createdAt: Long,
        idGenerator: (index: Int) -> String =
            { index -> "ICL-${UUID.randomUUID()}-$index" }
    ): List<InventoryCostLayer> {

        val units = baseQuantity.storageUnits

        require(units > 0L) {
            "Base storage units must be strictly positive: $units"
        }

        require(totalCost.amountMinorUnits >= 0L) {
            "Goods receipt acquisition cost must not be negative: " +
                totalCost.amountMinorUnits
        }

        val totalMinor = totalCost.amountMinorUnits

        val unitCostMinor = totalMinor / units
        val remainderMinor = totalMinor % units

        return if (remainderMinor == 0L) {
            listOf(
                InventoryCostLayer(
                    id = idGenerator(0),
                    productId = productId,
                    stockBatchId = stockBatchId,
                    supplierId = supplierId,
                    initialQuantity = baseQuantity,
                    remainingQuantity = baseQuantity,
                    acquisitionUnitCost = Money(unitCostMinor),
                    acquiredAt = acquiredAt,
                    sourceReceiptRef = sourceReceiptRef,
                    createdAt = createdAt,
                    updatedAt = createdAt
                )
            )
        } else {
            /*
             * remainderMinor is strictly smaller than units, therefore
             * primaryUnits remains positive.
             */
            val primaryUnits = units - remainderMinor

            val primaryQuantity = Quantity(
                storageUnits = primaryUnits,
                scale = baseQuantity.scale
            )

            val remainderQuantity = Quantity(
                storageUnits = remainderMinor,
                scale = baseQuantity.scale
            )

            val remainderUnitCost = try {
                Math.addExact(unitCostMinor, 1L)
            } catch (e: ArithmeticException) {
                throw ArithmeticException(
                    "Acquisition unit-cost remainder allocation overflow: " +
                        "baseCost=$unitCostMinor"
                )
            }

            listOf(
                InventoryCostLayer(
                    id = idGenerator(0),
                    productId = productId,
                    stockBatchId = stockBatchId,
                    supplierId = supplierId,
                    initialQuantity = primaryQuantity,
                    remainingQuantity = primaryQuantity,
                    acquisitionUnitCost = Money(unitCostMinor),
                    acquiredAt = acquiredAt,
                    sourceReceiptRef = sourceReceiptRef,
                    createdAt = createdAt,
                    updatedAt = createdAt
                ),
                InventoryCostLayer(
                    id = idGenerator(1),
                    productId = productId,
                    stockBatchId = stockBatchId,
                    supplierId = supplierId,
                    initialQuantity = remainderQuantity,
                    remainingQuantity = remainderQuantity,
                    acquisitionUnitCost = Money(remainderUnitCost),
                    acquiredAt = acquiredAt,
                    sourceReceiptRef = sourceReceiptRef,
                    createdAt = createdAt,
                    updatedAt = createdAt
                )
            )
        }
    }

    /**
     * Resolves the physical StockBatch for a receipt item.
     *
     * Physical identity is deliberately independent from acquisition cost.
     *
     * Multiple InventoryCostLayer records may therefore point to the same
     * StockBatch when the same physical manufacturer batch is received at
     * different acquisition costs.
     */
    fun resolveBatch(
        item: GoodsReceiptItem,
        receiptId: String,
        existingBatches: List<StockBatch>,
        createdAt: Long,
        idGenerator: () -> String = { "SB-${UUID.randomUUID()}" }
    ): Pair<StockBatch, Boolean> {

        val (normalizedBatchNumber, resolvedExpiry) =
            when (item.trackingMode) {

                StockBatch.TRACKING_STANDARD_BATCHED -> {
                    item.batchNumber!!.trim().uppercase() to item.expiryDateInt
                }

                StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY -> {
                    item.batchNumber!!.trim().uppercase() to
                        StockBatch.EXPIRY_UNKNOWN_OR_NONE
                }

                StockBatch.TRACKING_SUPPLIER_UNTRACKED -> {
                    generateUntrackedBatchNumber(
                        goodsReceiptId = receiptId,
                        lineIndex = item.lineIndex
                    ) to item.expiryDateInt
                }

                StockBatch.TRACKING_NON_BATCHED_COMMODITY -> {
                    COMMODITY_BATCH_NUMBER to
                        StockBatch.EXPIRY_UNKNOWN_OR_NONE
                }

                else -> {
                    (
                        item.batchNumber
                            ?.trim()
                            ?.uppercase()
                            ?: generateUntrackedBatchNumber(
                                goodsReceiptId = receiptId,
                                lineIndex = item.lineIndex
                            )
                    ) to item.expiryDateInt
                }
            }

        val existing = existingBatches.find { batch ->
            batch.productId == item.productId &&
                batch.batchNumber == normalizedBatchNumber &&
                batch.expiryDateInt == resolvedExpiry
        }

        return if (existing != null) {
            existing to false
        } else {
            val newBatch = StockBatch(
                id = idGenerator(),
                productId = item.productId,
                batchNumber = normalizedBatchNumber,
                expiryDateInt = resolvedExpiry,
                trackingMode = item.trackingMode,
                createdAt = createdAt,
                updatedAt = createdAt
            )

            newBatch to true
        }
    }

    /**
     * Prepares the complete deterministic bundle for a GoodsReceipt commit.
     *
     * No database writes occur here.
     *
     * The caller is responsible for persisting the returned bundle atomically.
     */
    fun prepareCommit(
        receipt: GoodsReceipt,
        items: List<GoodsReceiptItem>,
        productsById: Map<String, ProductMaster>,
        unitsById: Map<String, ProductUnit>,
        baseUnitsByProductId: Map<String, ProductUnit>,
        existingBatches: List<StockBatch>,
        referenceDateInt: Int,
        commitInstant: Long
    ): ReceivingResult {

        if (receipt.isCommitted) {
            return ReceivingResult.Failure(
                listOf(
                    ReceivingError.AlreadyCommitted(receipt.id)
                )
            )
        }

        if (receipt.isVoided) {
            return ReceivingResult.Failure(
                listOf(
                    ReceivingError.ReceiptVoided(receipt.id)
                )
            )
        }

        if (items.isEmpty()) {
            return ReceivingResult.Failure(
                listOf(
                    ReceivingError.EmptyReceipt(receipt.id)
                )
            )
        }

        val errors = mutableListOf<ReceivingError>()
        val warnings = mutableListOf<ReceivingWarning>()

        if (receipt.supplierId == null) {
            warnings.add(
                ReceivingWarning.NoSupplierSpecified(receipt.id)
            )
        }

        if (receipt.sourceDocumentRef.isNullOrBlank()) {
            warnings.add(
                ReceivingWarning.MissingSupplierReference(receipt.id)
            )
        }

        val allKnownBatches = existingBatches.toMutableList()

        val createdBatches = mutableListOf<StockBatch>()
        val createdCostLayers = mutableListOf<InventoryCostLayer>()
        val createdMovements = mutableListOf<StockMovement>()

        for (item in items) {

            val product = productsById[item.productId]

            if (product == null) {
                errors.add(
                    ReceivingError.ProductNotFound(
                        productId = item.productId,
                        lineIndex = item.lineIndex
                    )
                )
                continue
            }

            if (!product.isActive) {
                errors.add(
                    ReceivingError.ProductInactive(
                        productId = item.productId,
                        lineIndex = item.lineIndex
                    )
                )
                continue
            }

            val receivingUnit = unitsById[item.receivingUnitId]

            if (receivingUnit == null) {
                errors.add(
                    ReceivingError.UnitNotFound(
                        unitId = item.receivingUnitId,
                        lineIndex = item.lineIndex
                    )
                )
                continue
            }

            if (receivingUnit.productId != product.id) {
                errors.add(
                    ReceivingError.UnitProductMismatch(
                        unitId = item.receivingUnitId,
                        expectedProductId = product.id,
                        actualProductId = receivingUnit.productId,
                        lineIndex = item.lineIndex
                    )
                )
                continue
            }

            val baseUnit = baseUnitsByProductId[product.id]

            if (baseUnit == null) {
                errors.add(
                    ReceivingError.BaseUnitNotFound(
                        productId = product.id,
                        lineIndex = item.lineIndex
                    )
                )
                continue
            }

            if (baseUnit.productId != product.id) {
                errors.add(
                    ReceivingError.BaseUnitNotFound(
                        productId = product.id,
                        lineIndex = item.lineIndex
                    )
                )
                continue
            }

            /*
             * Expiry validation remains here because receiving must reject
             * malformed calendar dates before physical stock is prepared.
             *
             * Whether expired stock should be accepted operationally remains
             * a policy concern handled by the receiving validation layer.
             */
            if (item.expiryDateInt != StockBatch.EXPIRY_UNKNOWN_OR_NONE) {

                if (!StockBatch.isValidExpiryDateInt(item.expiryDateInt)) {
                    errors.add(
                        ReceivingError.InvalidExpiryDate(
                            lineIndex = item.lineIndex,
                            expiryDateInt = item.expiryDateInt
                        )
                    )
                    continue
                }

                if (item.expiryDateInt < referenceDateInt) {
                    warnings.add(
                        ReceivingWarning.StockAlreadyExpired(
                            lineIndex = item.lineIndex,
                            expiryDateInt = item.expiryDateInt,
                            referenceDateInt = referenceDateInt
                        )
                    )
                } else if (item.expiryDateInt == referenceDateInt) {
                    warnings.add(
                        ReceivingWarning.StockExpiringToday(
                            lineIndex = item.lineIndex,
                            expiryDateInt = item.expiryDateInt
                        )
                    )
                }
            }

            /*
             * Commercial-unit conversion.
             *
             * The ProductMaster is now authoritative for:
             *
             * - quantityScale
             * - minimumTransactionIncrement
             *
             * ProductUnit is authoritative for:
             *
             * - exact commercial → canonical-base conversion
             */
            val baseQuantityResult = convertToBaseQuantity(
                receivedQuantity = item.receivedQuantity,
                receivingUnit = receivingUnit,
                baseUnit = baseUnit,
                productQuantityScale = product.quantityScale,
                minimumTransactionIncrement = product.minimumTransactionIncrement,
                lineIndex = item.lineIndex
            )

            val baseQuantity = when (baseQuantityResult) {

                is Either.Left -> {
                    errors.add(baseQuantityResult.value)
                    continue
                }

                is Either.Right -> {
                    baseQuantityResult.value
                }
            }

            /*
             * Resolve physical batch identity independently of acquisition cost.
             */
            val (resolvedBatch, isNew) = resolveBatch(
                item = item,
                receiptId = receipt.id,
                existingBatches = allKnownBatches,
                createdAt = commitInstant
            )

            if (isNew) {
                createdBatches.add(resolvedBatch)
                allKnownBatches.add(resolvedBatch)
            }

            /*
             * Acquisition cost belongs to this receipt acquisition event.
             *
             * The human-facing receipt number is the stable historical
             * reference. Supplier invoice/source-document information remains
             * on GoodsReceipt itself.
             */
            val costLayers = calculateCostLayerTranches(
                totalCost = item.totalCost,
                baseQuantity = baseQuantity,
                productId = product.id,
                stockBatchId = resolvedBatch.id,
                supplierId = receipt.supplierId,
                sourceReceiptRef = receipt.receiptNumber,
                acquiredAt = receipt.receivedAt,
                createdAt = commitInstant,
                idGenerator = { trancheIndex ->
                    "ICL-GR-${receipt.id}-${item.lineIndex}-$trancheIndex"
                }
            )

            createdCostLayers.addAll(costLayers)

            /*
             * StockMovement is the physical stock-flow record.
             *
             * It receives the converted canonical base quantity.
             * It does NOT contain acquisition-cost authority.
             */
            val movement = StockMovement(
                id = "SM-GR-${receipt.id}-${item.lineIndex}",
                productId = product.id,
                stockBatchId = resolvedBatch.id,
                movementType = StockMovement.TYPE_PURCHASE_RECEIPT,
                quantity = baseQuantity,
                occurredAt = receipt.receivedAt,
                sourceTransactionRef = receipt.id,
                sourceTransactionType = "GOODS_RECEIPT",
                initiatedByUserId = receipt.receivedByUserId,
                reason =
                    "Purchasing receipt: ${receipt.receiptNumber} " +
                        "(Line ${item.lineIndex})",
                createdAt = commitInstant
            )

            createdMovements.add(movement)
        }

        if (errors.isNotEmpty()) {
            return ReceivingResult.Failure(
                errors = errors,
                warnings = warnings
            )
        }

        val committedReceipt = receipt.copy(
            status = GoodsReceipt.STATUS_COMMITTED,
            committedAt = commitInstant,
            updatedAt = commitInstant
        )

        return ReceivingResult.Success(
            committedReceipt = committedReceipt,
            newBatches = createdBatches,
            costLayers = createdCostLayers,
            stockMovements = createdMovements,
            warnings = warnings
        )
    }

    /**
     * Lightweight functional Either construct used by this service so
     * conversion failures remain explicit without adding an external
     * dependency.
     */
    sealed class Either<out L, out R> {

        data class Left<out L>(
            val value: L
        ) : Either<L, Nothing>()

        data class Right<out R>(
            val value: R
        ) : Either<Nothing, R>()
    }
}
