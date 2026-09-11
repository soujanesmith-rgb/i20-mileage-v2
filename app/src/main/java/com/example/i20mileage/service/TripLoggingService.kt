package com.example.i20mileage.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.i20mileage.data.AppDatabase
import com.example.i20mileage.data.Trip
import com.google.android.gms.location.*
import kotlinx.coroutines.*

/**
 * Runs as a foreground service so Android doesn't kill GPS updates in the background.
 *
 * Logic:
 *  - Speed > MOVING_THRESHOLD_MPS for STARTUP_HOLD_MS  -> start (or resume) a trip
 *  - Speed < STOPPED_THRESHOLD_MPS for STOP_HOLD_MS     -> end the trip
 *  - While a trip is active, accumulate distance between consecutive GPS fixes
 */
class TripLoggingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var db: AppDatabase

    private var lastLocation: Location? = null
    private var activeTrip: Trip? = null
    private var lastMovingTimestamp = 0L
    private var lastStoppedTimestamp = 0L

    companion object {
        const val CHANNEL_ID = "trip_logging_channel"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.example.i20mileage.action.STOP"

        private const val MOVING_THRESHOLD_MPS = 1.4f   // ~5 km/h
        private const val STOPPED_THRESHOLD_MPS = 0.5f  // ~1.8 km/h
        private const val STARTUP_HOLD_MS = 10_000L     // must be moving 10s before we count it a trip
        private const val STOP_HOLD_MS = 3 * 60_000L    // 3 min stationary = trip ended (parked, not a red light)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            handleNewLocation(loc)
        }
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(applicationContext)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                endActiveTrip(System.currentTimeMillis())
                stopSelf()
            }
            return START_NOT_STICKY
        }
        startForeground()
        startLocationUpdates()
        return START_STICKY
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("i20 Mileage Tracker")
            .setContentText("Tracking your drive...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            // Location permission not granted — handle in UI layer before starting this service.
        }
    }

    private fun handleNewLocation(loc: Location) {
        val speed = loc.speed // meters/sec, from GPS
        val now = System.currentTimeMillis()

        if (speed >= MOVING_THRESHOLD_MPS) {
            lastStoppedTimestamp = 0L
            if (lastMovingTimestamp == 0L) lastMovingTimestamp = now

            val movingLongEnough = now - lastMovingTimestamp >= STARTUP_HOLD_MS
            if (movingLongEnough) {
                scope.launch {
                    if (activeTrip == null) {
                        activeTrip = db.tripDao().getActiveTrip() ?: run {
                            val newTrip = Trip(startTimeMillis = now)
                            val id = db.tripDao().insert(newTrip)
                            newTrip.copy(id = id)
                        }
                    }
                    accumulateDistance(loc)
                }
            }
        } else if (speed <= STOPPED_THRESHOLD_MPS) {
            lastMovingTimestamp = 0L
            if (lastStoppedTimestamp == 0L) lastStoppedTimestamp = now

            val stoppedLongEnough = now - lastStoppedTimestamp >= STOP_HOLD_MS
            if (stoppedLongEnough) {
                scope.launch { endActiveTrip(now) }
            }
        }

        lastLocation = loc
    }

    private suspend fun accumulateDistance(loc: Location) {
        val prev = lastLocation
        val trip = activeTrip ?: return
        if (prev != null) {
            val deltaMeters = prev.distanceTo(loc)
            // Ignore GPS jitter spikes when stationary (e.g. > 100m in one 3-5s fix is implausible in traffic)
            if (deltaMeters in 0.5..300.0) {
                trip.distanceMeters += deltaMeters
                db.tripDao().update(trip)
            }
        }
    }

    private suspend fun endActiveTrip(now: Long) {
        val trip = activeTrip ?: return
        trip.endTimeMillis = now
        trip.isActive = false
        db.tripDao().update(trip)
        activeTrip = null
        lastLocation = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Trip Logging", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private fun Context.startTripLoggingService() {
    val intent = Intent(this, TripLoggingService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
    else startService(intent)
}
