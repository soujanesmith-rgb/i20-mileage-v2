package com.example.i20mileage.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Central place to check "am I allowed to do X" — used both by the permission
 * request UI and by MainActivity before starting TripLoggingService.
 */
object PermissionState {

    fun hasForegroundLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** Only meaningful on API 29+; returns true on older versions since it's implied by foreground grant. */
    fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Only meaningful on API 33+; true on older versions since notifications didn't need runtime permission. */
    fun hasNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** True once every permission needed to reliably run TripLoggingService is in place. */
    fun isFullyReadyForTracking(context: Context): Boolean =
        hasForegroundLocation(context) &&
            hasBackgroundLocation(context) &&
            hasNotifications(context)
    // Battery optimization exemption is strongly recommended but not a hard requirement,
    // so it's checked separately rather than folded into "fully ready".
}
