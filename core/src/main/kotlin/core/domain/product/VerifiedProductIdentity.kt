package core.domain.product

import core.domain.model.ProductType

data class ProductIngredientIdentity(
    val ingredientName: String,
    val normalizedIngredientName: String? = null,
    val strengthValue: String? = null,
    val strengthUnit: String? = null,
    val denominatorValue: String? = null,
    val denominatorUnit: String? = null,
    val sequence: Int = 0
)

data class ProductIdentifierIdentity(
    val identifierType: String,
    val value: String,
    val normalizedValue: String = value.trim().uppercase(),
    val isPrimary: Boolean = false
)

data class ProductEntityIdentity(
    val entityName: String,
    val role: String,
    val location: String? = null,
    val address: String? = null,
    val sequence: Int = 0
)

data class ProductAttributeIdentity(
    val definitionKey: String,
    val valueType: String,
    val value: String,
    val normalizedValue: String? = null,
    val provenance: String? = null
)

data class VerifiedProductIdentity(
    val productType: ProductType,
    val brandName: String? = null,
    val genericName: String? = null,
    val categoryId: String? = null,
    val description: String? = null,
    val manufacturer: String? = null,
    val ingredients: List<ProductIngredientIdentity> = emptyList(),
    val identifiers: List<ProductIdentifierIdentity> = emptyList(),
    val entities: List<ProductEntityIdentity> = emptyList(),
    val attributes: List<ProductAttributeIdentity> = emptyList(),
    val dosageForm: String? = null,
    val route: String? = null,
    val therapeuticCategory: String? = null,
    val prescriptionClassification: String? = null,
    val storageCondition: String? = null,
    val sourceImageUris: List<String> = emptyList()
) {
    init {
        require(brandName?.isNotBlank() == true || genericName?.isNotBlank() == true) {
            "Verified product identity requires a brand or generic name"
        }
    }
}
