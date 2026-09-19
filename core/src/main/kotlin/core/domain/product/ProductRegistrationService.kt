package core.domain.product

import core.domain.model.PharmaceuticalDetail
import core.domain.model.ProductAttribute
import core.domain.model.ProductEntity
import core.domain.model.ProductIdentifier
import core.domain.model.ProductImage
import core.domain.model.ProductIngredient
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.QuantityScale
import core.domain.model.UnitPriceConfig
import core.domain.persistence.ProductMasterDao
import core.domain.persistence.TransactionRunner
import java.util.UUID
import java.util.Locale

/**
 * Atomic persistence boundary for a verified product identity.
 *
 * Scanner evidence never reaches this service directly. The caller must
 * provide a user-verified identity.
 */
class ProductRegistrationService(
    private val transactionRunner: TransactionRunner,
    private val productMasterDao: ProductMasterDao
) {
    data class RegistrationRequest(
        val identity: VerifiedProductIdentity,
        val productId: String,
        val baseUnit: ProductUnit,
        val quantityScale: QuantityScale = QuantityScale.SCALE_0,
        val minimumTransactionIncrementStorageUnits: Long = 1L,
        val basePriceConfig: UnitPriceConfig? = null,
        val images: List<ProductImage> = emptyList()
    )

    fun register(request: RegistrationRequest): ProductMaster =
        transactionRunner.runInTransaction {
            val now = System.currentTimeMillis()
            val productId = request.productId.trim()
            require(productId.isNotBlank()) { "Product id must not be blank" }
            require(request.baseUnit.productId == productId) { "Base unit must reference the product being registered" }

            val manufacturer = request.identity.manufacturer
                ?: request.identity.entities
                    .firstOrNull { it.role == "MANUFACTURER" }
                    ?.entityName

            val product = ProductMaster(
                id = productId,
                brandName = request.identity.brandName?.trim()?.ifBlank { null },
                genericName = request.identity.genericName?.trim()?.ifBlank { null },
                productType = request.identity.productType.keycode,
                categoryId = request.identity.categoryId?.trim()?.ifBlank { null },
                manufacturer = manufacturer?.trim()?.ifBlank { null },
                description = request.identity.description?.trim()?.ifBlank { null },
                quantityScale = request.quantityScale,
                minimumTransactionIncrementStorageUnits = request.minimumTransactionIncrementStorageUnits,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )

            productMasterDao.insertProduct(product)

            if (request.identity.ingredients.isNotEmpty()) {
                productMasterDao.insertProductIngredients(
                    request.identity.ingredients.map {
                        ProductIngredient(
                            id = UUID.randomUUID().toString(),
                            productId = productId,
                            ingredientName = it.ingredientName.trim(),
                            normalizedIngredientName = it.normalizedIngredientName?.trim()?.ifBlank { null },
                            strengthValue = it.strengthValue?.trim()?.ifBlank { null },
                            strengthUnit = it.strengthUnit?.trim()?.ifBlank { null },
                            denominatorValue = it.denominatorValue?.trim()?.ifBlank { null },
                            denominatorUnit = it.denominatorUnit?.trim()?.ifBlank { null },
                            sequence = it.sequence,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                )
            }

            if (request.identity.identifiers.isNotEmpty()) {
                productMasterDao.insertProductIdentifiers(
                    request.identity.identifiers.map {
                        ProductIdentifier(
                            id = UUID.randomUUID().toString(),
                            productId = productId,
                            identifierType = it.identifierType,
                            value = it.value.trim(),
                            normalizedValue = it.normalizedValue.trim(),
                            isPrimary = it.isPrimary,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                )
            }

            if (request.identity.entities.isNotEmpty()) {
                productMasterDao.insertProductEntities(
                    request.identity.entities.map {
                        ProductEntity(
                            id = UUID.randomUUID().toString(),
                            productId = productId,
                            entityName = it.entityName.trim(),
                            normalizedName = it.entityName.trim().uppercase(),
                            role = it.role,
                            location = it.location?.trim()?.ifBlank { null },
                            address = it.address?.trim()?.ifBlank { null },
                            sequence = it.sequence,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                )
            }

            if (request.identity.attributes.isNotEmpty()) {
                productMasterDao.insertProductAttributes(
                    request.identity.attributes.map {
                        ProductAttribute(
                            id = UUID.randomUUID().toString(),
                            productId = productId,
                            definitionKey = it.definitionKey,
                            valueType = it.valueType,
                            value = it.value,
                            normalizedValue = it.normalizedValue,
                            provenance = it.provenance,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                )
            }

            val hasPharmaceuticalData =
                request.identity.productType.keycode == "MED" ||
                    listOf(
                        request.identity.dosageForm,
                        request.identity.route,
                        request.identity.therapeuticCategory,
                        request.identity.prescriptionClassification,
                        request.identity.storageCondition
                    ).any { !it.isNullOrBlank() }

            if (hasPharmaceuticalData) {
                productMasterDao.insertPharmaceuticalDetail(
                    PharmaceuticalDetail(
                        id = UUID.randomUUID().toString(),
                        productId = productId,
                        activeIngredients = null,
                        strength = null,
                        dosageForm = request.identity.dosageForm?.trim()?.ifBlank { null },
                        route = deriveRoute(request.identity.dosageForm) ?: request.identity.route?.trim()?.ifBlank { null },
                        therapeuticCategory = request.identity.therapeuticCategory?.trim()?.ifBlank { null },
                        prescriptionClassification = request.identity.prescriptionClassification?.trim()?.ifBlank { null },
                        storageCondition = request.identity.storageCondition?.trim()?.ifBlank { null },
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }

            productMasterDao.insertUnit(request.baseUnit.copy(productId = productId))
            request.basePriceConfig?.let {
                productMasterDao.savePriceConfig(it.copy(productUnitId = request.baseUnit.id))
            }
            if (request.images.isNotEmpty()) {
                productMasterDao.insertProductImages(request.images.map { it.copy(productId = productId) })
            }

            product
        private fun deriveRoute(dosageForm: String?): String? = when (dosageForm?.trim()?.lowercase(Locale.ROOT)) {
        "tablet", "tablets", "capsule", "capsules", "syrup", "suspension", "solution", "powder", "sachet" -> "Oral"
        "cream", "ointment", "gel", "patch" -> "Topical"
        "injection" -> "Parenteral"
        "eye drops", "ophthalmic drops", "ophthalmic" -> "Ophthalmic"
        "otic drops", "ear drops", "otic" -> "Otic"
        "nasal drops", "nasal spray", "nasal" -> "Nasal"
        "suppository", "rectal" -> "Rectal"
        else -> null
    }

    }
}
