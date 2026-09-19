package org.mwangaza.app.scanner

import core.domain.model.ProductType
import org.mwangaza.app.scanner.interpretation.ProductIdentityInterpreter

/**
 * Backward-compatible extraction facade.
 *
 * New code should provide the selected ProductType so interpretation can be
 * constrained by the user's declared product category.
 */
object ProductExtractionEngine {
    fun extract(
        ocr: List<OcrResult>,
        barcodes: List<BarcodeResult>
    ): ProductScanDraft =
        extract(ProductType.OTHER_HEALTH_COMMODITY, ocr, barcodes)

    fun extract(
        productType: ProductType,
        ocr: List<OcrResult>,
        barcodes: List<BarcodeResult>
    ): ProductScanDraft =
        ProductIdentityInterpreter.interpret(productType, ocr, barcodes).draft
}
