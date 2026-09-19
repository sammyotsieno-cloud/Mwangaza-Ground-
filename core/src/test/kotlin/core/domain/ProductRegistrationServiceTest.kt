package core.domain

import core.domain.model.Money
import core.domain.model.ProductIdentifier
import core.domain.model.ProductType
import core.domain.model.ProductUnit
import core.domain.model.UnitPriceConfig
import core.domain.product.ProductEntityIdentity
import core.domain.product.ProductIngredientIdentity
import core.domain.product.ProductRegistrationService
import core.domain.product.VerifiedProductIdentity
import core.domain.testutil.FakeCoreDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ProductRegistrationServiceTest {

    @Test
    fun registers_multi_ingredient_identity_atomically() {
        val db = FakeCoreDatabase()
        val productId = "PRODUCT-1"
        val unitId = "UNIT-1"
        val now = 1_700_000_000_000L

        val identity = VerifiedProductIdentity(
            productType = ProductType.MEDICINE,
            brandName = "Augmentin",
            genericName = "Amoxicillin + Clavulanic acid",
            ingredients = listOf(
                ProductIngredientIdentity("Amoxicillin", strengthValue = "500", strengthUnit = "mg", sequence = 0),
                ProductIngredientIdentity("Clavulanic acid", strengthValue = "125", strengthUnit = "mg", sequence = 1)
            ),
            entities = listOf(
                ProductEntityIdentity("Company A", role = "MANUFACTURER", location = "Kenya")
            ),
            dosageForm = "Tablet",
            route = "Oral",
            routeSource = "EXPLICIT",
            manufacturer = "Company A",
            identifiers = listOf(core.domain.product.ProductIdentifierIdentity("EAN_13", "1234567890128"))
        )

        val unit = ProductUnit(
            id = unitId,
            productId = productId,
            name = "Tablet",
            conversionNumerator = 1L,
            conversionDenominator = 1L,
            isBaseUnit = true,
            isPurchaseUnit = true,
            isDispensingUnit = true,
            isDisplayUnit = true,
            createdAt = now,
            updatedAt = now
        )

        val price = UnitPriceConfig(
            id = "PRICE-1",
            productUnitId = unitId,
            sellingPrice = Money(100),
            isActive = true,
            createdAt = now,
            updatedAt = now
        )

        val service = ProductRegistrationService(db, db.productMasterDao)

        val product = service.register(
            ProductRegistrationService.RegistrationRequest(
                identity = identity,
                productId = productId,
                baseUnit = unit,
                basePriceConfig = price
            )
        )

        assertEquals(productId, product.id)
        assertEquals(ProductType.MEDICINE.name, product.productType)
        assertEquals("Augmentin", product.brandName)
        assertEquals("Company A", product.manufacturer)
        assertEquals("1234567890128", db.productIdentifiers.values.single().value)
        assertEquals("Tablet", db.pharmaceuticalDetails.values.single().dosageForm)
        assertEquals("Oral", db.pharmaceuticalDetails.values.single().route)
        assertEquals("Company A", db.productEntities.values.single().entityName)
        assertEquals(2, db.productIngredients.size)
        assertEquals(1, db.productEntities.size)
        assertEquals("500", db.productIngredients.values.first { it.sequence == 0 }.strengthValue)
        assertEquals("125", db.productIngredients.values.first { it.sequence == 1 }.strengthValue)
        assertEquals(1, db.units.size)
        assertTrue(db.products.containsKey(productId))
    }

    @Test
    fun registration_rolls_back_when_persistence_constraint_fails() {
        val db = FakeCoreDatabase()
        val productId = "PRODUCT-ROLLBACK"
        val unit = ProductUnit(
            id = "UNIT-ROLLBACK",
            productId = productId,
            name = "Tablet",
            conversionNumerator = 1L,
            conversionDenominator = 1L,
            isBaseUnit = true,
            isPurchaseUnit = true,
            isDispensingUnit = true,
            isDisplayUnit = true,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )
        val identity = VerifiedProductIdentity(
            productType = ProductType.MEDICINE,
            brandName = "Rollback Test",
            identifiers = listOf(
                core.domain.product.ProductIdentifierIdentity(
                    identifierType = "EAN_13",
                    value = "1234567890123",
                    normalizedValue = "1234567890123"
                )
            )
        )
        val service = ProductRegistrationService(db, db.productMasterDao)

        db.productIdentifiers["existing"] = ProductIdentifier(
            id = "existing",
            productId = "OTHER",
            identifierType = "EAN_13",
            value = "1234567890123",
            normalizedValue = "1234567890123",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        try {
            service.register(
                ProductRegistrationService.RegistrationRequest(
                    identity = identity,
                    productId = productId,
                    baseUnit = unit
                )
            )
        } catch (_: Throwable) {
            // The test fixture's transaction runner must restore pre-transaction state.
        }

        assertTrue(db.products[productId] == null)
    }
}
