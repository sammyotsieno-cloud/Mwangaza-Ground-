package org.mwangaza.app.scanner

import core.domain.model.ProductType
import org.mwangaza.app.scanner.interpretation.ProductIdentityInterpretation
import org.mwangaza.app.scanner.interpretation.ProductIdentityInterpreter

object ProductExtractionEngine {
    fun extract(
        productType: ProductType,
        ocr: List<OcrResult>,
        barcodes: List<BarcodeResult>
    ): ProductIdentityInterpretation =
        ProductIdentityInterpreter.interpret(productType, ocr, barcodes)
}
