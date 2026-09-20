package org.mwangaza.app.scanner.interpretation

import core.domain.model.ProductType
import org.mwangaza.app.scanner.OcrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryExtractionProfilesTest {
    private fun ocr(text: String) = listOf(
        OcrResult(text = text, confidence = 0.99f, sourceImageUri = "test://image")
    )

    @Test
    fun medicine_extracts_denominator_strength_storage_pack_and_multiple_ingredients() {
        val result = CategoryExtractionProfiles.extract(
            ProductType.MEDICINE,
            ocr(
                """
                Amoxicillin 250 mg/5 mL
                Each 5 mL contains: Amoxicillin 250 mg
                Clavulanic acid 125 mg
                Store at 2–8°C
                Box of 100 capsules
                """.trimIndent()
            )
        )

        assertTrue(result.variables.any { it.definitionKey == "strength" && it.value.contains("250 mg/5 mL") })
        assertTrue(result.variables.any { it.definitionKey == "storage_condition" })
        assertTrue(result.variables.any { it.definitionKey == "pack_size" && it.value.contains("100") })
        assertEquals(1, result.ingredients.size)
        assertEquals("Amoxicillin", result.ingredients.first().ingredientName)
        assertEquals("250", result.ingredients.first().strengthValue)
        assertEquals("5", result.ingredients.first().denominatorValue)
    }

    @Test
    fun diagnostic_requires_explicit_context_for_specimen() {
        val result = CategoryExtractionProfiles.extract(
            ProductType.DIAGNOSTIC,
            ocr("Whole blood specimen
Test for: Malaria antigen
Method: Immunochromatographic")
        )
        assertEquals("Whole blood specimen", result.variables.first { it.definitionKey == "specimen_type" }.value)
        assertEquals("Malaria antigen", result.variables.first { it.definitionKey == "test_analyte" }.value)
        assertEquals("Immunochromatographic", result.variables.first { it.definitionKey == "method" }.value)
    }

    @Test
    fun laboratory_supply_requires_lab_context_for_edta() {
        val result = CategoryExtractionProfiles.extract(
            ProductType.LABORATORY_SPECIMEN_SUPPLY,
            ocr("EDTA tube
Capacity: 5 mL
Pack of 100")
        )
        assertEquals("EDTA", result.variables.first { it.definitionKey == "additive_medium" }.value)
        assertTrue(result.variables.any { it.definitionKey == "volume_capacity" && it.value == "5 mL" })
        assertTrue(result.variables.any { it.definitionKey == "pack_count" })
    }

    @Test
    fun ambiguity_does_not_assign_bare_numbers() {
        val result = CategoryExtractionProfiles.extract(
            ProductType.OTHER_HEALTH_COMMODITY,
            ocr("5 mL 500 mg 100 blood sterile EDTA 23G")
        )
        assertTrue(result.variables.isEmpty())
    }

    @Test
    fun medical_consumable_distinguishes_dimension_sterility_and_single_use() {
        val result = CategoryExtractionProfiles.extract(
            ProductType.MEDICAL_CONSUMABLE,
            ocr("PVC catheter
23G x 10 cm
STERILE
SINGLE USE
Pack of 10")
        )
        assertTrue(result.variables.any { it.definitionKey == "material" })
        assertTrue(result.variables.any { it.definitionKey == "size_gauge" })
        assertTrue(result.variables.any { it.definitionKey == "sterility" })
        assertTrue(result.variables.any { it.definitionKey == "single_use" })
        assertTrue(result.variables.any { it.definitionKey == "pack_count" })
    }
}
