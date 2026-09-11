package com.example.i20mileage.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.i20mileage.data.AppDatabase
import com.example.i20mileage.data.Trip
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Foreground GPS service used while the user explicitly has tracking enabled.
 *
 * Important: we do NOT depend on Location.speed to decide whether the car is moving.
 * Many Android head units report speed as 0 even while GPS position is changing.
 * Distance is therefore calculated from consecutive valid GPS coordinates instead.
 */
class TripLoggingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var db: AppDatabase

    private var lastLocation: Location? = null
    private var activeTrip: Trip? = null
    private val locationMutex = Mutex()

    companion object {
        const val CHANNEL_ID = "trip_logging_channel"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.example.i20mileage.action.STOP"
        const val ACTION_SPEED_UPDATE = "com.example.i20mileage.action.SPEED_UPDATE"
        const val EXTRA_SPEED_KMH = "extra_speed_kmh"

        // GPS fixes that are too inaccurate should not affect mileage.
        private const val MAX_ACCURACY_METERS = 60f

        // A normal 3-5 second GPS update should never jump hundreds of kilometres.
        // A generous ceiling avoids rejecting legitimate highway movement.
        private const val MAX_DELTA_METERS = 500f

        // If GPS disappears for a long time, establish a new baseline instead of
        // counting the entire gap as driving distance.
        private const val MAX_GAP_MILLIS = 90_000L
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::handleNewLocation)
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

        startForegroundServiceNotification()

        // Recover an active trip before accepting GPS callbacks. This also makes
        // tracking resilient if Android recreates the service.
        scope.launch {
            activeTrip = db.tripDao().getActiveTrip()
            startLocationUpdates()
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("i20 Mileage Tracker")
            .setContentText("Recording GPS distance")
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
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMaxUpdateDelayMillis(6_000L)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (_: SecurityException) {
            // Permissions are checked before the service is started.
        }
    }

    private fun handleNewLocation(location: Location) {
        if (!location.hasAccuracy() || location.accuracy > MAX_ACCURACY_METERS) return

        scope.launch {
            locationMutex.withLock {
                if (activeTrip == null) {
                activeTrip = db.tripDao().getActiveTrip()
                    ?: run {
                        val trip = Trip(startTimeMillis = System.currentTimeMillis())
                        val id = db.tripDao().insert(trip)
                        trip.copy(id = id)
                    }
            }

            val previous = lastLocation
            val timeGap = if (previous == null) 0L else location.time - previous.time
            var derivedSpeedKmh = 0f

            if (previous != null && timeGap in 1..MAX_GAP_MILLIS) {
                val deltaMeters = previous.distanceTo(location)

                // Ignore GPS jitter and implausible jumps. A 0.5m lower bound
                // prevents tiny stationary movements from inflating mileage.
                if (deltaMeters >= 0.5f && deltaMeters <= MAX_DELTA_METERS) {
                    val trip = activeTrip ?: return@launch
                    trip.distanceMeters += deltaMeters.toDouble()
                    db.tripDao().update(trip)
                    derivedSpeedKmh = (deltaMeters / (timeGap / 1000f)) * 3.6f
                }
            }

            // Prefer GPS-reported speed when available. Some head units report 0,
            // so fall back to speed derived from the coordinate change.
            val speedKmh = when {
                location.hasSpeed() && location.speed >= 0.5f -> location.speed * 3.6f
                derivedSpeedKmh >= 0.5f -> derivedSpeedKmh
                else -> 0f
            }.coerceIn(0f, 220f)
            sendBroadcast(Intent(ACTION_SPEED_UPDATE).apply {
                putExtra(EXTRA_SPEED_KMH, speedKmh)
                setPackage(packageName)
            })

            // Only accepted/usable GPS fixes become the next distance baseline.
            lastLocation = Location(location)
            }
        }
    }

    private suspend fun endActiveTrip(now: Long) {
        val trip = activeTrip ?: db.tripDao().getActiveTrip() ?: return
        trip.endTimeMillis = now
        trip.isActive = false
        db.tripDao().update(trip)
        activeTrip = null
        lastLocation = null
        sendBroadcast(Intent(ACTION_SPEED_UPDATE).apply {
            putExtra(EXTRA_SPEED_KMH, 0f)
            setPackage(packageName)
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip Logging",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
