package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing the identity and operational configuration of the facility
 * where this software installation is deployed.
 *
 * Reusability & Facility Identity:
 * - This entity models facility configuration as DATA, not code or hard-coded domain logic.
 * - While the initial deployment operates at "Mwangaza-Ground", the software is designed to be
 *   reusable across any health facility (chemist, clinic, dispensary, pharmacy, hospital store)
 *   without altering business logic, database schemas, inventory rules, or transaction processing.
 *
 * Operational & Time Configuration:
 * - [facilityTimezone] defines the IANA timezone identifier (e.g. "Africa/Nairobi") used by the
 *   future centralized time abstraction to translate absolute epoch instants ([createdAt], [updatedAt],
 *   transaction timestamps, batch expiry dates) into facility-local dates and times.
 * - Does NOT require or use Android Calendar permissions (READ_CALENDAR / WRITE_CALENDAR).
 * - Operates offline using the local system/device clock as the initial physical time source.
 *
 * Single-Facility Phase 1 Scope:
 * - In Phase 1, the installation manages exactly one active facility profile record.
 * - This deployment constraint is managed at the repository and database initialization layers;
 *   the entity itself does not artificially restrict multiple instances or force singleton state.
 *
 * Currency Authority:
 * - Monetary values throughout the application are governed exclusively by [Money] (fixed to Phase 1 KES).
 * - [FacilityProfile] does NOT introduce a competing currency authority or multi-currency configuration.
 *
 * Media / Logo Reference:
 * - [logoUri] stores a URI/file path reference only, avoiding storage of heavy image binary data
 *   within the database configuration entity.
 */
@Entity(tableName = "facility_profiles")
data class FacilityProfile(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "facility_name")
    val facilityName: String,

    @ColumnInfo(name = "facility_type")
    val facilityType: String,

    @ColumnInfo(name = "facility_timezone")
    val facilityTimezone: String,

    @ColumnInfo(name = "license_number")
    val licenseNumber: String? = null,

    @ColumnInfo(name = "address")
    val address: String? = null,

    @ColumnInfo(name = "phone")
    val phone: String? = null,

    @ColumnInfo(name = "email")
    val email: String? = null,

    @ColumnInfo(name = "logo_uri")
    val logoUri: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank()) { "FacilityProfile id must not be blank" }
        require(facilityName.isNotBlank()) { "FacilityProfile facilityName must not be blank" }
        require(facilityType.isNotBlank()) { "FacilityProfile facilityType must not be blank" }
        require(facilityTimezone.isNotBlank()) { "FacilityProfile facilityTimezone must not be blank" }
        require(createdAt > 0L) {
            "FacilityProfile createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
        require(updatedAt > 0L) {
            "FacilityProfile updatedAt must be a positive epoch timestamp, got: $updatedAt (id=$id)"
        }
        require(updatedAt >= createdAt) {
            "FacilityProfile updatedAt ($updatedAt) must not precede createdAt ($createdAt) (id=$id)"
        }
    }
}
