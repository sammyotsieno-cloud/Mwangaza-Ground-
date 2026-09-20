package org.mwangaza.app.scanner

import core.domain.model.ProductType
import org.mwangaza.app.scanner.interpretation.ProductIdentityInterpretation
import org.mwangaza.app.scanner.interpretation.ProductIdentityInterpreter

object ProductExtractionEngine {
    fun extract(
        productType: ProductType,
        observations: List<ProductScanObservation>,
        genericNames: List<String>
    ): ProductIdentityInterpretation =
        ProductIdentityInterpreter.interpret(productType, observations, genericNames)
}
