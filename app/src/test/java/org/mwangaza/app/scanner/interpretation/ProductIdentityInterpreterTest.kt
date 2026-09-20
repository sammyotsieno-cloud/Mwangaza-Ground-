package org.mwangaza.app.scanner.interpretation

import core.domain.model.ProductType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mwangaza.app.scanner.BarcodeResult
import org.mwangaza.app.scanner.OcrResult
import org.mwangaza.app.scanner.ProductScanObservation

class ProductIdentityInterpreterTest {
    private fun observation(uri: String, text: String = "", barcodes: List<BarcodeResult> = emptyList()) =
        ProductScanObservation(
            sourceImageUri = uri,
            ocrResults = if (text.isBlank()) emptyList() else listOf(
                OcrResult(text = text, confidence = 0.99f, sourceImageUri = uri)
            ),
            barcodeResults = barcodes
        )

    @Test
    fun same_brand_across_photos_reconciles_to_one_value_with_provenance() {
        val result = ProductIdentityInterpreter.interpret(
            ProductType.OTHER_HEALTH_COMMODITY,
            listOf(
                observation("photo://one", "ACME"),
                observation("photo://two", "ACME")
            )
        )

        assertEquals("ACME", result.draft.brandName)
        assertEquals("AGREEMENT", result.reconciliationFindings.first { it.field == "brandName" }.status)
        assertEquals(
            setOf("photo://one", "photo://two"),
            result.reconciliationFindings.first { it.field == "brandName" }.sourceImageUris.toSet()
        )
    }

    @Test
    fun unique_category_finding_is_preserved() {
        val result = ProductIdentityInterpreter.interpret(
            ProductType.MEDICINE,
            listOf(
                observation("photo://one", "ACME"),
                observation("photo://two", "Store at 2–8°C")
            )
        )

        assertTrue(result.draft.categoryVariables.any { it.definitionKey == "storage_condition" })
        assertEquals(
            "UNIQUE",
            result.reconciliationFindings.first { it.field == "storage_condition" }.status
        )
    }

    @Test
    fun conflicting_strength_is_not_silently_selected() {
        val result = ProductIdentityInterpreter.interpret(
            ProductType.MEDICINE,
            listOf(
                observation("photo://one", "ACME\n250 mg/5 mL"),
                observation("photo://two", "ACME\n500 mg/5 mL")
            )
        )

        assertNull(result.draft.strength)
        val finding = result.reconciliationFindings.first { it.field == "strength" }
        assertEquals("CONFLICT", finding.status)
        assertEquals(setOf("250 mg/5 mL", "500 mg/5 mL"), finding.conflictingValues.toSet())
    }

    @Test
    fun matching_barcode_observations_reconcile() {
        val barcode = BarcodeResult(
            rawValue = "1234567890123",
            format = "EAN_13",
            sourceImageUri = "photo://one",
            validationState = "PLAUSIBLE"
        )
        val barcode2 = barcode.copy(sourceImageUri = "photo://two")

        val result = ProductIdentityInterpreter.interpret(
            ProductType.OTHER_HEALTH_COMMODITY,
            listOf(
                observation("photo://one", "ACME", listOf(barcode)),
                observation("photo://two", "ACME", listOf(barcode2))
            )
        )

        assertEquals("1234567890123", result.draft.barcodeValue)
        assertEquals("AGREEMENT", result.reconciliationFindings.first { it.field == "identifier" }.status)
    }

    @Test
    fun conflicting_barcodes_are_not_silently_resolved() {
        val result = ProductIdentityInterpreter.interpret(
            ProductType.OTHER_HEALTH_COMMODITY,
            listOf(
                observation("photo://one", "ACME", listOf(BarcodeResult("1234567890123", "EAN_13", sourceImageUri = "photo://one"))),
                observation("photo://two", "ACME", listOf(BarcodeResult("9999999999999", "EAN_13", sourceImageUri = "photo://two")))
            )
        )

        assertNull(result.draft.barcodeValue)
        val finding = result.reconciliationFindings.first { it.field == "identifier" }
        assertEquals("CONFLICT", finding.status)
        assertEquals(setOf("1234567890123", "9999999999999"), finding.conflictingValues.toSet())
    }

    @Test
    fun matching_ocr_identifier_and_barcode_are_one_finding() {
        val result = ProductIdentityInterpreter.interpret(
            ProductType.OTHER_HEALTH_COMMODITY,
            listOf(
                observation("photo://one", "ACME\n4006381333931"),
                observation("photo://two", "ACME", listOf(
                    BarcodeResult("4006381333931", "EAN_13", sourceImageUri = "photo://two", validationState = "PLAUSIBLE")
                ))
            )
        )

        val identifier = result.reconciliationFindings.first { it.field == "identifier" }
        assertEquals("4006381333931", result.draft.barcodeValue)
        assertEquals("AGREEMENT", identifier.status)
        assertEquals(setOf("photo://one", "photo://two"), identifier.sourceImageUris.toSet())
        assertTrue(identifier.evidence.contains("OCR_IDENTIFIER"))
    }

    @Test
    fun all_product_categories_use_category_extraction_in_multi_photo_path() {
        val cases = listOf(
            ProductType.MEDICINE to ("Amoxicillin 250 mg" to "strength"),
            ProductType.MEDICAL_CONSUMABLE to ("Material: PVC\n23G x 10 cm\nSTERILE\nSINGLE USE\nPack of 10" to "material"),
            ProductType.DIAGNOSTIC to ("Test for: Malaria antigen\nSpecimen: Whole blood\nMethod: Immunochromatographic" to "test_analyte"),
            ProductType.WOUND_CARE to ("Gauze dressing\n10 cm x 10 cm\nSTERILE\nPack of 10" to "dressing_type"),
            ProductType.ANTISEPTIC_DISINFECTANT to ("Alcohol 70%\nIntended use: skin disinfection\nDilute 1:10\nVolume: 500 mL" to "active_concentration"),
            ProductType.PERSONAL_CARE_HYGIENE to ("For: skin\nAlcohol 70%\nNet content: 200 mL\nMint" to "volume_pack_size"),
            ProductType.MEDICAL_DEVICE_EQUIPMENT to ("Blood pressure monitor\nModel: X1\nSize: Adult" to "device_type"),
            ProductType.LABORATORY_SPECIMEN_SUPPLY to ("EDTA tube\nCapacity: 5 mL\nPack of 100" to "additive_medium"),
            ProductType.NUTRITION_THERAPEUTIC_FOOD to ("Purpose: therapeutic nutrition\nProtein 10 g\n10 g per serving\nVanilla\nNet content: 200 mL" to "purpose"),
            ProductType.OTHER_HEALTH_COMMODITY to ("Intended use: wound cleaning\nPack of 10\nStore at 2–8°C" to "intended_use")
        )

        cases.forEach { (type, input) ->
            val result = ProductIdentityInterpreter.interpret(
                type,
                listOf(
                    observation("photo://a", input.first),
                    observation("photo://b", input.first)
                )
            )
            assertTrue(type.toString() + " should extract " + input.second, result.draft.categoryVariables.any { it.definitionKey == input.second })
        }
    }

    @Test
    fun single_photo_path_remains_compatible() {
        val result = ProductIdentityInterpreter.interpret(
            ProductType.MEDICINE,
            listOf(OcrResult("Amoxicillin 250 mg/5 mL", 0.99f, "photo://one")),
            emptyList()
        )

        assertEquals(ProductType.MEDICINE, result.draft.productType)
        assertEquals("250 mg/5 mL", result.draft.strength)
    }
}
