package com.example.i20mileage.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Query("SELECT * FROM trips WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTrip(): Trip?

    @Query("SELECT * FROM trips WHERE startTimeMillis >= :sinceMillis ORDER BY startTimeMillis ASC")
    suspend fun getTripsSince(sinceMillis: Long): List<Trip>

    @Query("SELECT * FROM trips ORDER BY startTimeMillis DESC")
    fun observeAllTrips(): Flow<List<Trip>>

    @Query("SELECT COALESCE(SUM(distanceMeters), 0) FROM trips WHERE startTimeMillis >= :sinceMillis")
    suspend fun totalDistanceSince(sinceMillis: Long): Double

    @Query("SELECT COALESCE(SUM(distanceMeters), 0) FROM trips WHERE startTimeMillis >= :fromMillis AND startTimeMillis < :toMillis")
    suspend fun totalDistanceBetween(fromMillis: Long, toMillis: Long): Double
}

@Dao
interface FuelLogDao {
    @Insert
    suspend fun insert(fuelLog: FuelLog): Long

    @Delete
    suspend fun delete(fuelLog: FuelLog)

    @Query("SELECT * FROM fuel_logs ORDER BY timestampMillis DESC")
    fun observeAllFuelLogs(): Flow<List<FuelLog>>

    @Query("SELECT * FROM fuel_logs ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatestFuelLog(): FuelLog?

    // Second-to-latest full-tank fill, used as the "start point" for a mileage window
    @Query("""
        SELECT * FROM fuel_logs 
        WHERE isFullTank = 1 
        ORDER BY timestampMillis DESC 
        LIMIT 1 OFFSET :offset
    """)
    suspend fun getFullTankFillAtOffset(offset: Int): FuelLog?
}
