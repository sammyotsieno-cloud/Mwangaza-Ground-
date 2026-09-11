package core.domain.receiving

import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.InventoryCostLayer
import core.domain.model.StockBatch
import core.domain.model.StockMovement

/**
 * Domain errors that cause GoodsReceipt validation or commitment to fail safely.
 *
 * Design Rule:
 * - Domain failures are represented as explicit types rather than unhandled runtime crashes.
 * - Every error provides clear contextual attribution (e.g. lineIndex, productId, or receiptId).
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

    data class ProductNotFound(val productId: String, val lineIndex: Int) :
        ReceivingError("Product '$productId' on line $lineIndex was not found.")

    data class ProductInactive(val productId: String, val lineIndex: Int) :
        ReceivingError("Product '$productId' on line $lineIndex is inactive and cannot receive stock.")

    data class UnitNotFound(val unitId: String, val lineIndex: Int) :
        ReceivingError("ProductUnit '$unitId' on line $lineIndex was not found.")

    data class UnitProductMismatch(
        val unitId: String,
        val expectedProductId: String,
        val actualProductId: String,
        val lineIndex: Int
    ) : ReceivingError(
        "ProductUnit '$unitId' on line $lineIndex belongs to product '$actualProductId', not expected '$expectedProductId'."
    )

    data class BaseUnitNotFound(val productId: String, val lineIndex: Int) :
        ReceivingError("Base unit for product '$productId' on line $lineIndex was not found.")

    data class InvalidDiscreteQuantity(val lineIndex: Int, val rawUnits: Long, val scale: Int) :
        ReceivingError(
            "Line $lineIndex has fractional quantity ($rawUnits at scale $scale) for a discrete product."
        )

    data class QuantityConversionOverflow(val lineIndex: Int, val detail: String) :
        ReceivingError("Quantity conversion on line $lineIndex overflows Long storage units: $detail")

    data class CostCalculationOverflow(val lineIndex: Int, val detail: String) :
        ReceivingError("Cost calculation on line $lineIndex overflows minor units: $detail")

    data class InvalidExpiryDate(val lineIndex: Int, val expiryDateInt: Int) :
        ReceivingError("Line $lineIndex has invalid Gregorian expiry date integer: $expiryDateInt")

    data class MissingBatchNumber(val lineIndex: Int, val trackingMode: String) :
        ReceivingError("Line $lineIndex requires a non-blank batch number for tracking mode '$trackingMode'.")
}

/**
 * Non-blocking operational warnings that highlight data-quality concerns
 * without aborting valid receiving events (e.g. receiving expired stock for quarantine/investigation).
 */
sealed class ReceivingWarning(val message: String) {
    data class StockAlreadyExpired(
        val lineIndex: Int,
        val expiryDateInt: Int,
        val referenceDateInt: Int
    ) : ReceivingWarning(
        "Line $lineIndex: Received stock is already expired (expiry: $expiryDateInt, reference: $referenceDateInt)."
    )

    data class StockExpiringToday(val lineIndex: Int, val expiryDateInt: Int) :
        ReceivingWarning("Line $lineIndex: Received stock expires today ($expiryDateInt).")

    data class MissingSupplierReference(val receiptId: String) :
        ReceivingWarning("Receipt '$receiptId' has no supplier reference/invoice number attached.")

    data class NoSupplierSpecified(val receiptId: String) :
        ReceivingWarning("Receipt '$receiptId' has no supplier specified.")
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
