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
 * Full-tank-to-full-tank mileage calculation.
 *
 * When both full-tank entries contain odometer readings, the car's odometer
 * difference is the preferred distance source. GPS trip distance remains the
 * fallback for older entries that do not have usable odometer readings.
 */
class MileageCalculator(private val db: AppDatabase) {

    suspend fun calculateLatestMileage(): MileageResult? {
        val latestFill = db.fuelLogDao().getFullTankFillAtOffset(0) ?: return null
        val previousFill = db.fuelLogDao().getFullTankFillAtOffset(1) ?: return null
        return calculateBetween(previousFill, latestFill)
    }

    private suspend fun calculateBetween(from: FuelLog, to: FuelLog): MileageResult? {
        if (to.litersFilled <= 0) return null

        val odometerDistance = if (from.odometerKm != null && to.odometerKm != null) {
            val difference = to.odometerKm - from.odometerKm
            if (difference > 0) difference else null
        } else null

        val distanceKm = odometerDistance ?: run {
            val distanceMeters = db.tripDao().totalDistanceBetween(from.timestampMillis, to.timestampMillis)
            distanceMeters / 1000.0
        }

        if (distanceKm <= 0) return null

        return MileageResult(
            kmpl = distanceKm / to.litersFilled,
            distanceKm = distanceKm,
            litersUsed = to.litersFilled,
            fromDate = from.timestampMillis,
            toDate = to.timestampMillis
        )
    }

    suspend fun calculateOverallAverage(): Double? {
        val allFullTanks = mutableListOf<FuelLog>()
        var offset = 0
        while (true) {
            val fill = db.fuelLogDao().getFullTankFillAtOffset(offset) ?: break
            allFullTanks.add(fill)
            offset++
        }
        if (allFullTanks.size < 2) return null

        val chronological = allFullTanks.reversed()
        var totalDistanceKm = 0.0
        var totalLiters = 0.0

        for (i in 1 until chronological.size) {
            val from = chronological[i - 1]
            val to = chronological[i]
            val odometerDistance = if (from.odometerKm != null && to.odometerKm != null) {
                val difference = to.odometerKm - from.odometerKm
                if (difference > 0) difference else null
            } else null
            val distanceKm = odometerDistance ?: db.tripDao()
                .totalDistanceBetween(from.timestampMillis, to.timestampMillis) / 1000.0

            if (distanceKm > 0 && to.litersFilled > 0) {
                totalDistanceKm += distanceKm
                totalLiters += to.litersFilled
            }
        }

        return if (totalLiters > 0) totalDistanceKm / totalLiters else null
    }
}
