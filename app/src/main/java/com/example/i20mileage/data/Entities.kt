package com.example.i20mileage.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One continuous drive, auto-detected by the trip logger.
 * distanceMeters accumulates while the trip is "active".
 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMillis: Long,
    var endTimeMillis: Long? = null,
    var distanceMeters: Double = 0.0,
    var isActive: Boolean = true
)

/**
 * A fuel fill-up entry, logged manually by the user.
 * odometerKm is optional (only if you want to cross-check against your car's actual odometer).
 */
@Entity(tableName = "fuel_logs")
data class FuelLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val litersFilled: Double,
    val pricePerLiter: Double? = null,
    val odometerKm: Double? = null,
    val isFullTank: Boolean = true, // partial fills throw off mileage math, so flag them
    val notes: String? = null
)
