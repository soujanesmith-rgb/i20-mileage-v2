package com.example.i20mileage

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.i20mileage.data.FuelLog
import com.example.i20mileage.data.Trip
import com.example.i20mileage.permissions.PermissionOnboardingScreen
import com.example.i20mileage.permissions.PermissionState
import com.example.i20mileage.service.TripLoggingService
import com.example.i20mileage.ui.MileageViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            I20MileageTheme {
                var ready by remember {
                    mutableStateOf(PermissionState.isFullyReadyForTracking(this@MainActivity))
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (ready) {
                        MileageApp(
                            onStartTracking = { startTripLoggingService() },
                            onStopTracking = { stopTripLoggingService() }
                        )
                    } else {
                        PermissionOnboardingScreen(onAllRequiredGranted = { ready = true })
                    }
                }
            }
        }
    }

    private fun startTripLoggingService() {
        if (!PermissionState.hasForegroundLocation(this)) return
        val intent = Intent(this, TripLoggingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun stopTripLoggingService() {
        val intent = Intent(this, TripLoggingService::class.java).apply {
            action = TripLoggingService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
private fun I20MileageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}

@Composable
private fun MileageApp(
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    vm: MileageViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showFuelDialog by remember { mutableStateOf(false) }
    var trackingRequested by remember { mutableStateOf(false) }

    val latestMileage by vm.latestMileage.collectAsState()
    val average by vm.overallAverage.collectAsState()
    val todayDistance by vm.todayDistanceKm.collectAsState()
    val totalDistance by vm.totalDistanceKm.collectAsState()
    val activeTrip by vm.activeTrip.collectAsState()
    val trips by vm.trips.collectAsState()
    val fuelLogs by vm.fuelLogs.collectAsState()

    val isTracking = trackingRequested || activeTrip != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Speed, null) },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, null) },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> DashboardScreen(
                modifier = Modifier.padding(padding),
                todayDistanceKm = todayDistance,
                totalDistanceKm = totalDistance,
                latestMileage = latestMileage?.kmpl,
                averageMileage = average,
                activeTrip = activeTrip,
                isTracking = isTracking,
                onStartTracking = {
                    trackingRequested = true
                    onStartTracking()
                },
                onStopTracking = {
                    trackingRequested = false
                    onStopTracking()
                    vm.refresh()
                },
                onAddFuel = { showFuelDialog = true },
                recentTrips = trips.take(3),
                latestFuel = fuelLogs.firstOrNull()
            )
            1 -> HistoryScreen(
                modifier = Modifier.padding(padding),
                trips = trips,
                fuelLogs = fuelLogs
            )
            else -> SettingsScreen(modifier = Modifier.padding(padding))
        }
    }

    if (showFuelDialog) {
        AddFuelDialog(
            onDismiss = { showFuelDialog = false },
            onSave = { liters, price, odometer, full, notes ->
                vm.logFuelFillUp(liters, price, odometer, full, notes)
                showFuelDialog = false
            }
        )
    }
}

@Composable
private fun DashboardScreen(
    modifier: Modifier,
    todayDistanceKm: Double,
    totalDistanceKm: Double,
    latestMileage: Double?,
    averageMileage: Double?,
    activeTrip: Trip?,
    isTracking: Boolean,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onAddFuel: () -> Unit,
    recentTrips: List<Trip>,
    latestFuel: FuelLog?
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("i20 Mileage", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Your driving dashboard", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isTracking) "Tracking is on" else "Ready to track", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (activeTrip != null) "Drive detected. Distance is being recorded." else "Start tracking before you drive.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Box(
                            modifier = Modifier.size(12.dp).clip(CircleShape)
                                .background(if (isTracking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        )
                    }
                    if (activeTrip != null) {
                        Text("Current trip: ${formatKm(activeTrip.distanceMeters / 1000.0)} km", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = if (isTracking) onStopTracking else onStartTracking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isTracking) "Stop tracking" else "Start tracking")
                    }
                }
            }
        }

        item {
            Text("Mileage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MileageStat(
                        modifier = Modifier.weight(1f),
                        label = "Latest",
                        value = latestMileage?.let { "${format1(it)} km/L" } ?: "--",
                        icon = Icons.Default.Speed
                    )
                    MileageStat(
                        modifier = Modifier.weight(1f),
                        label = "Average",
                        value = averageMileage?.let { "${format1(it)} km/L" } ?: "--",
                        icon = Icons.Default.Favorite
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric("Today", "${format1(todayDistanceKm)} km", Modifier.weight(1f))
                SmallMetric("All trips", "${format1(totalDistanceKm)} km", Modifier.weight(1f))
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.LocalGasStation, null, tint = MaterialTheme.colorScheme.secondary) }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fuel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            latestFuel?.let { "Last fill: ${format1(it.litersFilled)} L" } ?: "No fill-up recorded yet",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    FilledTonalButton(onClick = onAddFuel) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }
        }

        if (recentTrips.isNotEmpty()) {
            item { Text("Recent trips", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(recentTrips, key = { it.id }) { trip -> TripRow(trip) }
        }
    }
}

@Composable
private fun MileageStat(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SmallMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TripRow(trip: Trip) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(trip.startTimeMillis), fontWeight = FontWeight.SemiBold)
                Text(if (trip.isActive) "In progress" else "Completed", style = MaterialTheme.typography.bodySmall)
            }
            Text("${format1(trip.distanceMeters / 1000.0)} km", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, trips: List<Trip>, fuelLogs: List<FuelLog>) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("Trips and fuel entries", style = MaterialTheme.typography.bodyMedium) }
        if (trips.isEmpty() && fuelLogs.isEmpty()) {
            item { EmptyState("No history yet", "Your trips and fill-ups will appear here.") }
        }
        if (trips.isNotEmpty()) {
            item { Text("Trips", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(trips, key = { "trip-${it.id}" }) { TripRow(it) }
        }
        if (fuelLogs.isNotEmpty()) {
            item { Text("Fuel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(fuelLogs, key = { "fuel-${it.id}" }) { fuel -> FuelRow(fuel) }
        }
    }
}

@Composable
private fun FuelRow(fuel: FuelLog) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalGasStation, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(fuel.timestampMillis), fontWeight = FontWeight.SemiBold)
                Text(if (fuel.isFullTank) "Full tank" else "Partial fill", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${format1(fuel.litersFilled)} L", fontWeight = FontWeight.Bold)
                fuel.pricePerLiter?.let { Text("₹${format1(it)}/L", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Hyundai i20", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("GPS-based mileage tracker", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text("Mileage method", fontWeight = FontWeight.SemiBold)
                Text("Full-tank to full-tank distance divided by litres added at the later fill-up.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tracking tips", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Keep location permission enabled and allow background access so trips can be recorded while the phone is locked.")
                Text("For the most useful mileage numbers, record every full-tank fill-up accurately.")
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.DirectionsCar, null, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AddFuelDialog(
    onDismiss: () -> Unit,
    onSave: (Double, Double?, Double?, Boolean, String?) -> Unit
) {
    var liters by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var fullTank by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add fuel fill-up") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(liters, { liters = it }, label = { Text("Litres added") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Price per litre (optional)") }, singleLine = true)
                OutlinedTextField(odometer, { odometer = it }, label = { Text("Odometer km (optional)") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fullTank, onCheckedChange = { fullTank = it })
                    Text("This was a full-tank fill-up")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = liters.toDoubleOrNull()?.let { it > 0 } == true,
                onClick = {
                    onSave(
                        liters.toDouble(),
                        price.toDoubleOrNull(),
                        odometer.toDoubleOrNull(),
                        fullTank,
                        notes.trim().ifBlank { null }
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)
private fun formatKm(value: Double): String = String.format(Locale.US, "%.1f", value)
private fun formatDate(time: Long): String = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(time))
