package core.domain.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.UnitPriceConfig

@Dao
interface ProductMasterDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertProduct(product: ProductMaster)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertUnits(units: List<ProductUnit>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertPriceConfig(config: UnitPriceConfig)

    @Query("SELECT * FROM product_masters WHERE id = :id")
    fun getProductById(id: String): ProductMaster?

    @Query("SELECT * FROM product_units WHERE product_id = :productId AND is_base_unit = 1 LIMIT 1")
    fun getBaseUnitForProduct(productId: String): ProductUnit?

    @Query("SELECT * FROM product_units WHERE id = :unitId")
    fun getUnitById(unitId: String): ProductUnit?

    @Query("SELECT * FROM product_units WHERE product_id = :productId")
    fun getUnitsForProduct(productId: String): List<ProductUnit>

    @Query("SELECT * FROM unit_price_configs WHERE product_unit_id = :unitId AND is_active = 1 LIMIT 1")
    fun getActivePriceConfigForUnit(unitId: String): UnitPriceConfig?
}
