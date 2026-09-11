package com.example.i20mileage.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Walks the user through every permission TripLoggingService needs, IN ORDER.
 * Order matters:
 *   1. Foreground location (required before background location can even be requested)
 *   2. Background location (separate step — see note below)
 *   3. Notifications (needed for the foreground-service notification on API 33+)
 *   4. Battery optimization exemption (not a runtime "permission", but just as critical —
 *      without it Android will silently kill the tracking service after a while)
 *
 * IMPORTANT — background location on Android 11+ (API 30+):
 * Google removed "Allow all the time" from the in-app runtime dialog. Requesting
 * ACCESS_BACKGROUND_LOCATION via the normal launcher will just show the same
 * foreground-only dialog again on these versions, so on API 30+ we send the user
 * straight to the app's Settings page and explain what to tap. On API 29 (Android 10)
 * exactly, the in-app dialog still works and includes the "Allow all the time" option.
 */
@Composable
fun PermissionOnboardingScreen(
    onAllRequiredGranted: () -> Unit
) {
    val context = LocalContext.current

    var hasForegroundLocation by remember { mutableStateOf(PermissionState.hasForegroundLocation(context)) }
    var hasBackgroundLocation by remember { mutableStateOf(PermissionState.hasBackgroundLocation(context)) }
    var hasNotifications by remember { mutableStateOf(PermissionState.hasNotifications(context)) }
    var ignoringBatteryOpt by remember { mutableStateOf(PermissionState.isIgnoringBatteryOptimizations(context)) }

    // Re-check every time this screen comes back into focus (e.g. returning from Settings)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasForegroundLocation = PermissionState.hasForegroundLocation(context)
                hasBackgroundLocation = PermissionState.hasBackgroundLocation(context)
                hasNotifications = PermissionState.hasNotifications(context)
                ignoringBatteryOpt = PermissionState.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasForegroundLocation, hasBackgroundLocation, hasNotifications) {
        if (hasForegroundLocation && hasBackgroundLocation && hasNotifications) {
            onAllRequiredGranted()
        }
    }

    // --- Launchers ---

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasForegroundLocation = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundLocation = granted
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifications = granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Set up tracking", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The app needs a few permissions to log your drives automatically, " +
                "even when your phone is locked or the app is in the background.",
            style = MaterialTheme.typography.bodyMedium
        )

        PermissionStepCard(
            title = "1. Location access",
            granted = hasForegroundLocation,
            description = "Needed to measure how far you've driven.",
            buttonLabel = "Allow location",
            onClick = {
                foregroundLocationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        )

        PermissionStepCard(
            title = "2. Background location",
            granted = hasBackgroundLocation,
            enabled = hasForegroundLocation, // Android requires foreground granted first
            description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                "On your Android version, tap below, then choose \"Allow all the time\" on the settings screen that opens."
            else
                "Lets tracking continue when the app isn't on screen.",
            buttonLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Open settings" else "Allow always",
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // API 30+: runtime dialog can't grant "Allow all the time" anymore — go to Settings
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // API 29: in-app dialog still offers "Allow all the time"
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                // Below API 29: background location is implied by the foreground grant, nothing to do.
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionStepCard(
                title = "3. Notifications",
                granted = hasNotifications,
                description = "Required to show the \"tracking active\" status notification while driving.",
                buttonLabel = "Allow notifications",
                onClick = {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }

        PermissionStepCard(
            title = "4. Disable battery optimization",
            granted = ignoringBatteryOpt,
            description = "Without this, Android may kill tracking mid-drive to save battery. Strongly recommended.",
            buttonLabel = "Open battery settings",
            onClick = {
                // Sends user to the system's battery-optimization exemption list rather than
                // requesting REQUEST_IGNORE_BATTERY_OPTIMIZATIONS directly — Play Store review
                // is strict about apps that pop that dialog unprompted.
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        )
    }
}

@Composable
private fun PermissionStepCard(
    title: String,
    granted: Boolean,
    description: String,
    buttonLabel: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (granted) {
                    Text("✓ Granted", color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(description, style = MaterialTheme.typography.bodySmall)
            if (!granted) {
                Button(onClick = onClick, enabled = enabled) {
                    Text(buttonLabel)
                }
            }
        }
    }
}
