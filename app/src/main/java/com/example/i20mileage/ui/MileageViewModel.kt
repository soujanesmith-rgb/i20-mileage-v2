package com.example.i20mileage.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.i20mileage.data.AppDatabase
import com.example.i20mileage.data.FuelLog
import com.example.i20mileage.data.Trip
import com.example.i20mileage.util.MileageCalculator
import com.example.i20mileage.util.MileageResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class MileageViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val calculator = MileageCalculator(db)

    private val _latestMileage = MutableStateFlow<MileageResult?>(null)
    val latestMileage: StateFlow<MileageResult?> = _latestMileage.asStateFlow()

    private val _overallAverage = MutableStateFlow<Double?>(null)
    val overallAverage: StateFlow<Double?> = _overallAverage.asStateFlow()

    val trips: StateFlow<List<Trip>> = db.tripDao().observeAllTrips()
        .let { flow ->
            val state = MutableStateFlow<List<Trip>>(emptyList())
            viewModelScope.launch { flow.collect { state.value = it } }
            state
        }

    val fuelLogs: StateFlow<List<FuelLog>> = db.fuelLogDao().observeAllFuelLogs()
        .let { flow ->
            val state = MutableStateFlow<List<FuelLog>>(emptyList())
            viewModelScope.launch { flow.collect { state.value = it } }
            state
        }

    private val _todayDistanceKm = MutableStateFlow(0.0)
    val todayDistanceKm: StateFlow<Double> = _todayDistanceKm.asStateFlow()

    private val _totalDistanceKm = MutableStateFlow(0.0)
    val totalDistanceKm: StateFlow<Double> = _totalDistanceKm.asStateFlow()

    private val _activeTrip = MutableStateFlow<Trip?>(null)
    val activeTrip: StateFlow<Trip?> = _activeTrip.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            db.tripDao().observeAllTrips().collect { list ->
                val start = startOfToday()
                _todayDistanceKm.value = list
                    .filter { it.startTimeMillis >= start }
                    .sumOf { it.distanceMeters } / 1000.0
                _totalDistanceKm.value = list.sumOf { it.distanceMeters } / 1000.0
                _activeTrip.value = list.firstOrNull { it.isActive }
                _latestMileage.value = calculator.calculateLatestMileage()
                _overallAverage.value = calculator.calculateOverallAverage()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _latestMileage.value = calculator.calculateLatestMileage()
            _overallAverage.value = calculator.calculateOverallAverage()
            _activeTrip.value = db.tripDao().getActiveTrip()
        }
    }

    fun logFuelFillUp(
        liters: Double,
        pricePerLiter: Double?,
        odometerKm: Double?,
        isFullTank: Boolean,
        notes: String? = null
    ) {
        viewModelScope.launch {
            db.fuelLogDao().insert(
                FuelLog(
                    timestampMillis = System.currentTimeMillis(),
                    litersFilled = liters,
                    pricePerLiter = pricePerLiter,
                    odometerKm = odometerKm,
                    isFullTank = isFullTank,
                    notes = notes
                )
            )
            refresh()
        }
    }

    private fun startOfToday(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
