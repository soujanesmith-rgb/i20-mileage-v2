package com.example.i20mileage.util

import com.example.i20mileage.data.AppDatabase
import com.example.i20mileage.data.FuelLog

data class MileageResult(
    val kmpl: Double,
    val distanceKm: Double,
    val litersUsed: Double,
    val fromDate: Long,
    val toDate: Long
)

/**
 * Mileage = distance driven between two consecutive FULL-TANK fill-ups / liters
 * added at the second (later) fill-up.
 *
 * Why this method: you can't know the tank was truly "full" from GPS/OBD alone,
 * so the full-tank-to-full-tank window is the only self-consistent way to measure
 * consumption without a working fuel-level sensor reading.
 */
class MileageCalculator(private val db: AppDatabase) {

    suspend fun calculateLatestMileage(): MileageResult? {
        val latestFill = db.fuelLogDao().getFullTankFillAtOffset(0) ?: return null
        val previousFill = db.fuelLogDao().getFullTankFillAtOffset(1) ?: return null

        return calculateBetween(previousFill, latestFill)
    }

    private suspend fun calculateBetween(from: FuelLog, to: FuelLog): MileageResult? {
        val distanceMeters = db.tripDao().totalDistanceBetween(from.timestampMillis, to.timestampMillis)
        val distanceKm = distanceMeters / 1000.0

        if (to.litersFilled <= 0 || distanceKm <= 0) return null

        val kmpl = distanceKm / to.litersFilled

        return MileageResult(
            kmpl = kmpl,
            distanceKm = distanceKm,
            litersUsed = to.litersFilled,
            fromDate = from.timestampMillis,
            toDate = to.timestampMillis
        )
    }

    /** Simple average across all full-tank windows, for a "lifetime average" stat. */
    suspend fun calculateOverallAverage(): Double? {
        val allFullTanks = mutableListOf<FuelLog>()
        var offset = 0
        while (true) {
            val fill = db.fuelLogDao().getFullTankFillAtOffset(offset) ?: break
            allFullTanks.add(fill)
            offset++
        }
        if (allFullTanks.size < 2) return null

        // oldest -> newest
        val chronological = allFullTanks.reversed()
        var totalDistanceKm = 0.0
        var totalLiters = 0.0

        for (i in 1 until chronological.size) {
            val from = chronological[i - 1]
            val to = chronological[i]
            val distanceMeters = db.tripDao().totalDistanceBetween(from.timestampMillis, to.timestampMillis)
            totalDistanceKm += distanceMeters / 1000.0
            totalLiters += to.litersFilled
        }

        return if (totalLiters > 0) totalDistanceKm / totalLiters else null
    }
}
