package core.domain.receiving

import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.InventoryCostLayer
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.Quantity
import core.domain.model.StockBatch
import core.domain.model.StockMovement
import java.math.BigInteger

/**
 * Domain errors that cause GoodsReceipt validation or commitment to fail safely.
 *
 * Design Rule:
 * - Domain failures are represented as explicit types rather than unhandled runtime crashes.
 * - Every error provides clear contextual attribution (e.g. lineIndex, productId, or receiptId).
 * - Validation owns receiving-domain legality; preparation/persistence services remain responsible
 *   for conversion execution and persistence respectively.
 */
sealed class ReceivingError(val message: String) {
    data class ReceiptNotFound(val receiptId: String) :
        ReceivingError("Goods receipt '$receiptId' not found.")

    data class AlreadyCommitted(val receiptId: String) :
        ReceivingError("Goods receipt '$receiptId' is already committed. Duplicate commit prevented.")

    data class ReceiptVoided(val receiptId: String) :
        ReceivingError("Goods receipt '$receiptId' is voided and cannot be committed.")

    data class EmptyReceipt(val receiptId: String) :
        ReceivingError("Goods receipt '$receiptId' contains no items to receive.")

    /**
     * The supplied receipt ID and receipt number resolve to conflicting
     * persisted receipt identities.
     */
    data class ReceiptIdentityConflict(
        val receiptId: String,
        val receiptNumber: String,
        val persistedReceiptId: String
    ) : ReceivingError(
        "Goods receipt identity conflict: receipt '$receiptId' with number '$receiptNumber' " +
            "conflicts with persisted receipt '$persistedReceiptId'."
    )

    /**
     * The supplied receipt number is already associated with a different
     * persisted receipt identity.
     */
    data class ReceiptNumberConflict(
        val receiptNumber: String,
        val persistedReceiptId: String
    ) : ReceivingError(
        "Goods receipt number '$receiptNumber' is already associated with " +
            "persisted receipt '$persistedReceiptId'."
    )

    /**
     * Downstream receiving artefacts already exist for the receipt.
     *
     * This protects the atomic receiving workflow from creating duplicate
     * cost layers or physical stock movements.
     */
    data class DuplicateReceivingArtifacts(
        val receiptId: String,
        val detail: String
    ) : ReceivingError(
        "Duplicate receiving artefacts detected for receipt '$receiptId': $detail"
    )

    /**
     * More than one ProductUnit is marked as the canonical base unit for
     * the same product.
     *
     * Receiving cannot safely determine which unit defines the canonical
     * physical quantity representation in this state.
     */
    data class MultipleBaseUnits(
        val productId: String,
        val count: Int
    ) : ReceivingError(
        "Product '$productId' has $count canonical base units. " +
            "Exactly one active base unit is required for receiving."
    )

    data class ProductNotFound(val productId: String, val lineIndex: Int) :
        ReceivingError("Product '$productId' on line $lineIndex was not found.")

    data class ProductInactive(val productId: String, val lineIndex: Int) :
        ReceivingError("Product '$productId' on line $lineIndex is inactive and cannot receive stock.")

    data class UnitNotFound(val unitId: String, val lineIndex: Int) :
        ReceivingError("ProductUnit '$unitId' on line $lineIndex was not found.")

    data class UnitInactive(val unitId: String, val lineIndex: Int) :
        ReceivingError("ProductUnit '$unitId' on line $lineIndex is inactive and cannot receive stock.")

    data class UnitNotPurchasable(val unitId: String, val lineIndex: Int) :
        ReceivingError(
            "ProductUnit '$unitId' on line $lineIndex is not configured as a purchase/receiving unit."
        )

    data class UnitProductMismatch(
        val unitId: String,
        val expectedProductId: String,
        val actualProductId: String,
        val lineIndex: Int
    ) : ReceivingError(
        "ProductUnit '$unitId' on line $lineIndex belongs to product '$actualProductId', " +
            "not expected '$expectedProductId'."
    )

    data class BaseUnitNotFound(val productId: String, val lineIndex: Int) :
        ReceivingError("Base unit for product '$productId' on line $lineIndex was not found.")

    data class BaseUnitProductMismatch(
        val productId: String,
        val unitProductId: String,
        val lineIndex: Int
    ) : ReceivingError(
        "Base unit on line $lineIndex belongs to product '$unitProductId', not expected '$productId'."
    )

    data class InvalidBaseUnit(val unitId: String, val lineIndex: Int) :
        ReceivingError(
            "ProductUnit '$unitId' on line $lineIndex is not a valid canonical base unit."
        )

    data class BaseUnitInactive(val unitId: String, val lineIndex: Int) :
        ReceivingError(
            "Canonical base unit '$unitId' on line $lineIndex is inactive."
        )

    data class QuantityScaleMismatch(
        val lineIndex: Int,
        val receivedScale: Int,
        val expectedScale: Int
    ) : ReceivingError(
        "Line $lineIndex quantity scale $receivedScale does not match product quantity scale $expectedScale."
    )

    data class InvalidProductQuantityPolicy(
        val productId: String,
        val lineIndex: Int,
        val incrementStorageUnits: Long,
        val scale: Int
    ) : ReceivingError(
        "Product '$productId' on line $lineIndex has an invalid minimum transaction increment " +
            "$incrementStorageUnits at quantity scale $scale."
    )

    data class NonPositiveQuantity(
        val lineIndex: Int,
        val rawUnits: Long,
        val scale: Int
    ) : ReceivingError(
        "Line $lineIndex has a non-positive received quantity ($rawUnits at scale $scale)."
    )

    data class QuantityNotMultipleOfIncrement(
        val lineIndex: Int,
        val rawUnits: Long,
        val incrementUnits: Long,
        val scale: Int
    ) : ReceivingError(
        "Line $lineIndex quantity $rawUnits at scale $scale is not an exact multiple " +
            "of the minimum transaction increment $incrementUnits."
    )

    data class InvalidDiscreteQuantity(
        val lineIndex: Int,
        val rawUnits: Long,
        val scale: Int
    ) : ReceivingError(
        "Line $lineIndex has an invalid quantity ($rawUnits at scale $scale)."
    )

    data class QuantityConversionNotExact(
        val lineIndex: Int,
        val numerator: String,
        val denominator: String,
        val scale: Int
    ) : ReceivingError(
        "Line $lineIndex commercial-to-canonical quantity conversion is not exactly representable " +
            "at quantity scale $scale. No rounding or truncation is permitted."
    )

    data class QuantityConversionOverflow(val lineIndex: Int, val detail: String) :
        ReceivingError("Quantity conversion on line $lineIndex overflows Long storage units: $detail")

    data class InvalidUnitCost(val lineIndex: Int, val amountMinorUnits: Long) :
        ReceivingError(
            "Line $lineIndex has an invalid negative unit cost: $amountMinorUnits minor units."
        )

    data class InvalidTotalCost(val lineIndex: Int, val amountMinorUnits: Long) :
        ReceivingError(
            "Line $lineIndex has an invalid negative total cost: $amountMinorUnits minor units."
        )

    data class InvalidExpiryDate(val lineIndex: Int, val expiryDateInt: Int) :
        ReceivingError("Line $lineIndex has invalid Gregorian expiry date integer: $expiryDateInt")

    data class MissingBatchNumber(val lineIndex: Int, val trackingMode: String) :
        ReceivingError(
            "Line $lineIndex requires a non-blank batch number for tracking mode '$trackingMode'."
        )

    data class ReceiptItemMismatch(
        val lineIndex: Int,
        val expectedReceiptId: String,
        val actualReceiptId: String
    ) : ReceivingError(
        "Line $lineIndex belongs to receipt '$actualReceiptId', not expected receipt '$expectedReceiptId'."
    )

    data class DuplicateLineIndex(val lineIndex: Int) :
        ReceivingError("Goods receipt contains duplicate line index $lineIndex.")
}

/**
 * Non-blocking operational warnings that highlight data-quality concerns
 * without aborting valid receiving events.
 */
sealed class ReceivingWarning(val message: String) {
    data class StockAlreadyExpired(
        val lineIndex: Int,
        val expiryDateInt: Int,
        val referenceDateInt: Int
    ) : ReceivingWarning(
        "Line $lineIndex: Received stock is already expired " +
            "(expiry: $expiryDateInt, reference: $referenceDateInt)."
    )

    data class StockExpiringToday(
        val lineIndex: Int,
        val expiryDateInt: Int
    ) : ReceivingWarning(
        "Line $lineIndex: Received stock expires today ($expiryDateInt)."
    )

    data class MissingSupplierReference(val receiptId: String) :
        ReceivingWarning(
            "Receipt '$receiptId' has no supplier reference/invoice number attached."
        )

    data class NoSupplierSpecified(val receiptId: String) :
        ReceivingWarning(
            "Receipt '$receiptId' has no supplier specified."
        )
}

/**
 * Reusable validation boundary for the receiving domain.
 *
 * Responsibilities:
 * - validate receipt state and receipt/item identity;
 * - validate product lifecycle;
 * - validate receiving-unit/product compatibility;
 * - validate canonical base-unit integrity;
 * - validate quantity scale and product transaction increment;
 * - validate exact commercial-unit → canonical-unit representability;
 * - validate acquisition-cost field legality;
 * - validate expiry/tracking constraints.
 *
 * COST AUTHORITY
 * --------------
 * totalCost on GoodsReceiptItem is the authoritative acquisition cost for
 * the receipt line.
 *
 * unitCost is a supplier/invoice unit-price snapshot or nominal commercial
 * unit cost. It is NOT required to multiply exactly to totalCost because a
 * legitimate acquisition total can be indivisible across the received
 * quantity when represented in currency minor units.
 *
 * GoodsReceiptService is responsible for converting the authoritative
 * totalCost into exact InventoryCostLayer cost tranches whose monetary
 * values conserve the original receipt-line total exactly.
 *
 * Non-responsibilities:
 * - creating StockBatch records;
 * - creating InventoryCostLayer records;
 * - creating StockMovement records;
 * - calculating FEFO;
 * - persisting anything.
 *
 * GoodsReceiptService remains responsible for preparing the deterministic
 * commit bundle. GoodsReceiptPersistenceService remains responsible for
 * atomic persistence.
 */
object GoodsReceiptValidation {

    /**
     * Validates the receipt-level state and structural relationship between
     * the receipt and its items.
     */
    fun validateReceipt(
        receipt: GoodsReceipt,
        items: List<GoodsReceiptItem>
    ): List<ReceivingError> {
        val errors = mutableListOf<ReceivingError>()

        if (receipt.isCommitted) {
            errors += ReceivingError.AlreadyCommitted(receipt.id)
        }

        if (receipt.isVoided) {
            errors += ReceivingError.ReceiptVoided(receipt.id)
        }

        if (items.isEmpty()) {
            errors += ReceivingError.EmptyReceipt(receipt.id)
            return errors
        }

        val seenLineIndexes = mutableSetOf<Int>()

        for (item in items) {
            if (item.goodsReceiptId != receipt.id) {
                errors += ReceivingError.ReceiptItemMismatch(
                    lineIndex = item.lineIndex,
                    expectedReceiptId = receipt.id,
                    actualReceiptId = item.goodsReceiptId
                )
            }

            if (!seenLineIndexes.add(item.lineIndex)) {
                errors += ReceivingError.DuplicateLineIndex(
                    lineIndex = item.lineIndex
                )
            }
        }

        return errors
    }

    /**
     * Validates one receipt line against its resolved product, receiving unit,
     * and canonical base unit.
     *
     * This method is deliberately side-effect free.
     */
    fun validateItem(
        item: GoodsReceiptItem,
        product: ProductMaster?,
        receivingUnit: ProductUnit?,
        baseUnit: ProductUnit?
    ): List<ReceivingError> {
        val errors = mutableListOf<ReceivingError>()

        if (product == null) {
            errors += ReceivingError.ProductNotFound(
                productId = item.productId,
                lineIndex = item.lineIndex
            )
            return errors
        }

        if (!product.isActive) {
            errors += ReceivingError.ProductInactive(
                productId = product.id,
                lineIndex = item.lineIndex
            )
        }

        if (product.minimumTransactionIncrementStorageUnits <= 0L) {
            errors += ReceivingError.InvalidProductQuantityPolicy(
                productId = product.id,
                lineIndex = item.lineIndex,
                incrementStorageUnits = product.minimumTransactionIncrementStorageUnits,
                scale = product.quantityScale.scale
            )
        }

        if (receivingUnit == null) {
            errors += ReceivingError.UnitNotFound(
                unitId = item.receivingUnitId,
                lineIndex = item.lineIndex
            )
            return errors
        }

        if (receivingUnit.productId != product.id) {
            errors += ReceivingError.UnitProductMismatch(
                unitId = receivingUnit.id,
                expectedProductId = product.id,
                actualProductId = receivingUnit.productId,
                lineIndex = item.lineIndex
            )
        }

        if (!receivingUnit.isActive) {
            errors += ReceivingError.UnitInactive(
                unitId = receivingUnit.id,
                lineIndex = item.lineIndex
            )
        }

        if (!receivingUnit.isPurchaseUnit) {
            errors += ReceivingError.UnitNotPurchasable(
                unitId = receivingUnit.id,
                lineIndex = item.lineIndex
            )
        }

        if (baseUnit == null) {
            errors += ReceivingError.BaseUnitNotFound(
                productId = product.id,
                lineIndex = item.lineIndex
            )
        } else {
            if (baseUnit.productId != product.id) {
                errors += ReceivingError.BaseUnitProductMismatch(
                    productId = product.id,
                    unitProductId = baseUnit.productId,
                    lineIndex = item.lineIndex
                )
            }

            if (!baseUnit.isBaseUnit || !baseUnit.representsExactlyOneBaseUnit) {
                errors += ReceivingError.InvalidBaseUnit(
                    unitId = baseUnit.id,
                    lineIndex = item.lineIndex
                )
            }

            if (!baseUnit.isActive) {
                errors += ReceivingError.BaseUnitInactive(
                    unitId = baseUnit.id,
                    lineIndex = item.lineIndex
                )
            }
        }

        errors += validateQuantity(
            item = item,
            product = product,
            receivingUnit = receivingUnit
        )

        errors += validateMoney(
            item = item
        )

        errors += validateExpiryAndTracking(
            item = item
        )

        return errors
    }

    /**
     * Validates quantity semantics before conversion is committed.
     *
     * ProductMaster owns:
     * - quantity scale;
     * - minimum legal transaction increment.
     *
     * ProductUnit owns:
     * - exact commercial-unit conversion.
     *
     * Quantity owns:
     * - exact mathematical representation.
     */
    fun validateQuantity(
        item: GoodsReceiptItem,
        product: ProductMaster,
        receivingUnit: ProductUnit?
    ): List<ReceivingError> {
        val errors = mutableListOf<ReceivingError>()

        if (item.receivedQuantity.scale != product.quantityScale) {
            errors += ReceivingError.QuantityScaleMismatch(
                lineIndex = item.lineIndex,
                receivedScale = item.receivedQuantity.scale.scale,
                expectedScale = product.quantityScale.scale
            )
            return errors
        }

        if (product.minimumTransactionIncrementStorageUnits <= 0L) {
            errors += ReceivingError.InvalidProductQuantityPolicy(
                productId = product.id,
                lineIndex = item.lineIndex,
                incrementStorageUnits = product.minimumTransactionIncrementStorageUnits,
                scale = product.quantityScale.scale
            )
        }

        if (!item.receivedQuantity.isPositive) {
            errors += ReceivingError.NonPositiveQuantity(
                lineIndex = item.lineIndex,
                rawUnits = item.receivedQuantity.storageUnits,
                scale = item.receivedQuantity.scale.scale
            )
            return errors
        }

        val minimumIncrement = product.minimumTransactionIncrement

        if (
            minimumIncrement.storageUnits > 0L &&
            !item.receivedQuantity.isMultipleOf(minimumIncrement)
        ) {
            errors += ReceivingError.QuantityNotMultipleOfIncrement(
                lineIndex = item.lineIndex,
                rawUnits = item.receivedQuantity.storageUnits,
                incrementUnits = minimumIncrement.storageUnits,
                scale = item.receivedQuantity.scale.scale
            )
        }

        if (receivingUnit == null) {
            return errors
        }

        /*
         * Validate the exact commercial-unit conversion independently of
         * GoodsReceiptService's conversion execution.
         *
         * We deliberately use BigInteger here so validation cannot itself
         * overflow while deciding whether the conversion is safe.
         */
        try {
            val numerator = BigInteger
                .valueOf(item.receivedQuantity.storageUnits)
                .multiply(
                    BigInteger.valueOf(
                        receivingUnit.normalizedConversionNumerator
                    )
                )
                .multiply(
                    BigInteger.valueOf(
                        product.quantityScale.multiplier
                    )
                )

            val denominator = BigInteger
                .valueOf(item.receivedQuantity.scale.multiplier)
                .multiply(
                    BigInteger.valueOf(
                        receivingUnit.normalizedConversionDenominator
                    )
                )

            if (denominator.signum() <= 0) {
                errors += ReceivingError.QuantityConversionOverflow(
                    lineIndex = item.lineIndex,
                    detail = "Commercial conversion denominator must be positive."
                )
            } else {
                val division = numerator.divideAndRemainder(denominator)

                if (division[1].signum() != 0) {
                    errors += ReceivingError.QuantityConversionNotExact(
                        lineIndex = item.lineIndex,
                        numerator = numerator.toString(),
                        denominator = denominator.toString(),
                        scale = product.quantityScale.scale
                    )
                } else {
                    val baseStorageUnits = division[0]
                        .toString()
                        .toLongOrNull()

                    if (baseStorageUnits == null) {
                        errors += ReceivingError.QuantityConversionOverflow(
                            lineIndex = item.lineIndex,
                            detail = "Exact converted canonical quantity exceeds Long storage capacity."
                        )
                    } else if (baseStorageUnits <= 0L) {
                        errors += ReceivingError.NonPositiveQuantity(
                            lineIndex = item.lineIndex,
                            rawUnits = baseStorageUnits,
                            scale = product.quantityScale.scale
                        )
                    } else {
                        val baseQuantity = Quantity(
                            storageUnits = baseStorageUnits,
                            scale = product.quantityScale
                        )

                        if (!baseQuantity.isMultipleOf(minimumIncrement)) {
                            errors += ReceivingError.QuantityNotMultipleOfIncrement(
                                lineIndex = item.lineIndex,
                                rawUnits = baseQuantity.storageUnits,
                                incrementUnits = minimumIncrement.storageUnits,
                                scale = baseQuantity.scale.scale
                            )
                        }
                    }
                }
            }
        } catch (e: ArithmeticException) {
            errors += ReceivingError.QuantityConversionOverflow(
                lineIndex = item.lineIndex,
                detail = e.message
                    ?: "Overflow while validating exact commercial-unit conversion."
            )
        }

        return errors
    }

    /**
     * Validates monetary fields at the receiving boundary.
     *
     * totalCost is the authoritative acquisition amount for the receipt line.
     *
     * unitCost is retained as the supplier/invoice unit-price snapshot or
     * nominal commercial unit cost. It is intentionally NOT required to
     * multiply exactly to totalCost.
     *
     * This distinction is essential because an acquisition total can be
     * indivisible across the received quantity in currency minor units.
     *
     * Example:
     *
     *     3 units purchased for KSh 100.00
     *
     * The exact total is 10,000 minor units. There is no requirement that
     * 10,000 / 3 be representable as one finite integer minor-unit unit cost.
     *
     * GoodsReceiptService later preserves the exact total by creating
     * deterministic cost tranches, for example:
     *
     *     2 units × 3,333 minor units
     *     1 unit  × 3,334 minor units
     *
     *     total = 10,000 minor units
     *
     * No rounding is performed on the authoritative total.
     */
    fun validateMoney(
        item: GoodsReceiptItem
    ): List<ReceivingError> {
        val errors = mutableListOf<ReceivingError>()

        if (item.unitCost.amountMinorUnits < 0L) {
            errors += ReceivingError.InvalidUnitCost(
                lineIndex = item.lineIndex,
                amountMinorUnits = item.unitCost.amountMinorUnits
            )
        }

        if (item.totalCost.amountMinorUnits < 0L) {
            errors += ReceivingError.InvalidTotalCost(
                lineIndex = item.lineIndex,
                amountMinorUnits = item.totalCost.amountMinorUnits
            )
        }

        return errors
    }

    /**
     * Validates expiry and tracking semantics at the receiving boundary.
     *
     * Entity constructors also protect these invariants, but keeping the
     * rules here makes validation explicit when receiving data is assembled
     * before persistence.
     */
    fun validateExpiryAndTracking(
        item: GoodsReceiptItem
    ): List<ReceivingError> {
        val errors = mutableListOf<ReceivingError>()

        if (!StockBatch.isValidExpiryDateInt(item.expiryDateInt)) {
            errors += ReceivingError.InvalidExpiryDate(
                lineIndex = item.lineIndex,
                expiryDateInt = item.expiryDateInt
            )
        }

        when (item.trackingMode) {
            StockBatch.TRACKING_STANDARD_BATCHED -> {
                if (item.batchNumber.isNullOrBlank()) {
                    errors += ReceivingError.MissingBatchNumber(
                        lineIndex = item.lineIndex,
                        trackingMode = item.trackingMode
                    )
                }
            }

            StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY -> {
                if (item.batchNumber.isNullOrBlank()) {
                    errors += ReceivingError.MissingBatchNumber(
                        lineIndex = item.lineIndex,
                        trackingMode = item.trackingMode
                    )
                }
            }

            StockBatch.TRACKING_SUPPLIER_UNTRACKED -> {
                // Batch identity is generated by GoodsReceiptService.
            }

            StockBatch.TRACKING_NON_BATCHED_COMMODITY -> {
                // Shared commodity identity is generated by GoodsReceiptService.
            }

            else -> {
                /*
                 * GoodsReceiptItem's constructor currently prevents this state,
                 * but retaining the check here makes the validation boundary
                 * defensive if an item is constructed through another path.
                 */
                errors += ReceivingError.MissingBatchNumber(
                    lineIndex = item.lineIndex,
                    trackingMode = item.trackingMode
                )
            }
        }

        return errors
    }
}

/**
 * Result of preparing or committing a goods receipt.
 */
sealed class ReceivingResult {
    data class Success(
        val committedReceipt: GoodsReceipt,
        val newBatches: List<StockBatch>,
        val costLayers: List<InventoryCostLayer>,
        val stockMovements: List<StockMovement>,
        val warnings: List<ReceivingWarning> = emptyList()
    ) : ReceivingResult()

    data class Failure(
        val errors: List<ReceivingError>,
        val warnings: List<ReceivingWarning> = emptyList()
    ) : ReceivingResult()
}
