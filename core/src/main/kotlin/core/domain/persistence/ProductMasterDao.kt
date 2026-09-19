package core.domain.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import core.domain.model.ProductMaster
import core.domain.model.ProductIngredient
import core.domain.model.ProductIdentifier
import core.domain.model.ProductEntity
import core.domain.model.ProductAttribute
import core.domain.model.PharmaceuticalDetail
import core.domain.model.ProductImage
import core.domain.model.ProductUnit
import core.domain.model.UnitPriceConfig

@Dao
interface ProductMasterDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertProduct(product: ProductMaster)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertPharmaceuticalDetail(detail: PharmaceuticalDetail)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertProductImage(image: ProductImage)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertProductImages(images: List<ProductImage>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertProductIngredients(ingredients: List<ProductIngredient>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertProductIdentifiers(identifiers: List<ProductIdentifier>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertProductEntities(entities: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertProductAttributes(attributes: List<ProductAttribute>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertUnits(units: List<ProductUnit>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertPriceConfig(config: UnitPriceConfig)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertUnit(unit: ProductUnit)

    @Update
    fun updateProduct(product: ProductMaster)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun savePriceConfig(config: UnitPriceConfig)

    @Query("SELECT * FROM product_masters WHERE id = :id")
    fun getProductById(id: String): ProductMaster?

    @Query("SELECT * FROM product_ingredients WHERE product_id = :productId ORDER BY sequence ASC")
    fun getIngredientsForProduct(productId: String): List<ProductIngredient>

    @Query("SELECT * FROM product_identifiers WHERE product_id = :productId ORDER BY is_primary DESC, identifier_type ASC")
    fun getIdentifiersForProduct(productId: String): List<ProductIdentifier>

    @Query("SELECT * FROM product_entities WHERE product_id = :productId ORDER BY role ASC, sequence ASC")
    fun getEntitiesForProduct(productId: String): List<ProductEntity>

    @Query("SELECT * FROM product_attributes WHERE product_id = :productId ORDER BY definition_key ASC")
    fun getAttributesForProduct(productId: String): List<ProductAttribute>

    @Query("SELECT * FROM product_masters ORDER BY is_active DESC, brand_name ASC, generic_name ASC")
    fun getAllProducts(): List<ProductMaster>

    @Query("SELECT * FROM product_units WHERE product_id = :productId AND is_base_unit = 1 LIMIT 1")
    fun getBaseUnitForProduct(productId: String): ProductUnit?

    @Query("SELECT * FROM product_units WHERE id = :unitId")
    fun getUnitById(unitId: String): ProductUnit?

    @Query("SELECT * FROM product_units WHERE product_id = :productId")
    fun getUnitsForProduct(productId: String): List<ProductUnit>

    @Query("SELECT * FROM product_units")
    fun getAllUnits(): List<ProductUnit>

    @Query("SELECT * FROM unit_price_configs WHERE product_unit_id = :unitId AND is_active = 1 LIMIT 1")
    fun getActivePriceConfigForUnit(unitId: String): UnitPriceConfig?

    @Query("SELECT * FROM unit_price_configs")
    fun getAllPriceConfigs(): List<UnitPriceConfig>
}
