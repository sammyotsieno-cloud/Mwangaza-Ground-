package org.mwangaza.app.scanner

import core.domain.model.ProductMaster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductIdentificationFoundationTest {
    @Test fun tabletAndCapsuleAliasesNormalize() {
        assertEquals("TABLET", EmbeddedOfflineProductKnowledge.normalize("tabs"))
        assertEquals("CAPSULE", EmbeddedOfflineProductKnowledge.normalize("caps"))
    }

    @Test fun categoryCluesAreRecognized() {
        val consumable = ProductIdentificationEngine.identify(
            ProductObservationFactory.fromOcr(listOf(ocr("sterile syringe")))
        )
        assertEquals(ProductKnowledgeCategory.MEDICAL_CONSUMABLE, consumable.category.category)

        val diagnostic = ProductIdentificationEngine.identify(
            ProductObservationFactory.fromOcr(listOf(ocr("rapid test cassette")))
        )
        assertEquals(ProductKnowledgeCategory.DIAGNOSTIC, diagnostic.category.category)
    }

    @Test fun insufficientEvidenceRemainsUnknown() {
        val result = ProductIdentificationEngine.identify(
            ProductObservationFactory.fromOcr(listOf(ocr("health product")))
        )
        assertEquals(IdentificationState.UNKNOWN, result.state)
    }

    @Test fun observationProvenanceIsPreserved() {
        val observation = ProductObservation(
            rawValue = "tabs",
            normalizedValue = EmbeddedOfflineProductKnowledge.normalize("tabs"),
            type = ObservationType.TEXT,
            source = ObservationSource.OCR,
            confidence = 0.8f,
            sourceImageUri = "file://front.jpg"
        )
        assertEquals("tabs", observation.rawValue)
        assertEquals("TABLET", observation.normalizedValue)
        assertEquals(ObservationSource.OCR, observation.source)
        assertEquals(0.8f, observation.confidence)
        assertEquals("file://front.jpg", observation.sourceImageUri)
    }

    @Test fun conflictingStrengthsProduceConflict() {
        val observations = ProductObservationFactory.fromOcr(listOf(
            ocr("Amoxicillin 500 mg capsule"),
            ocr("Amoxicillin 250 mg capsule")
        ))
        val result = ProductIdentificationEngine.identify(observations)
        assertEquals(IdentificationState.CONFLICT, result.state)
        assertTrue(result.contradictions.isNotEmpty())
    }

    @Test fun multipleLocalProductsAreAmbiguous() {
        val products = listOf(
            product("1", "Brand A", "Amoxicillin", "CAPSULE"),
            product("2", "Brand B", "Amoxicillin", "CAPSULE")
        )
        val result = ProductIdentificationEngine.identify(
            ProductObservationFactory.fromOcr(listOf(ocr("Amoxicillin capsule"))),
            facilityProducts = products
        )
        assertEquals(IdentificationState.AMBIGUOUS, result.state)
    }

    @Test fun localProductMasterIsUsedAsAuthority() {
        val products = listOf(product("1", "Brand A", "Amoxicillin", "CAPSULE"))
        val result = ProductIdentificationEngine.identify(
            ProductObservationFactory.fromOcr(listOf(ocr("Amoxicillin capsule"))),
            facilityProducts = products
        )
        assertTrue(result.matches.any { it.product.id == "1" })
    }

    private fun ocr(text: String) = OcrResult(text, 0.9f, "file://test.jpg")

    private fun product(id: String, brand: String, generic: String, type: String) = ProductMaster(
        id = id,
        brandName = brand,
        genericName = generic,
        productType = type,
        manufacturer = null,
        description = null,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )
}
