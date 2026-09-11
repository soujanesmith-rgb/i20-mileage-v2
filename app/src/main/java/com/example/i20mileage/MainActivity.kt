package com.example.i20mileage

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val colors = lightColorScheme(
        primary = Color(0xFF244B73),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9E9FA),
        onPrimaryContainer = Color(0xFF0B2943),
        secondary = Color(0xFF48657E),
        secondaryContainer = Color(0xFFDCEAF5),
        background = Color(0xFFF5F7FA),
        surface = Color.White
    )
    MaterialTheme(colorScheme = colors, content = content)
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
                    icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.LocalGasStation, contentDescription = null) },
                    label = { Text("Fuel") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
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
                recentTrips = trips.take(4),
                latestFuel = fuelLogs.firstOrNull()
            )
            1 -> FuelScreen(
                modifier = Modifier.padding(padding),
                fuelLogs = fuelLogs,
                latestMileage = latestMileage?.kmpl,
                onAddFuel = { showFuelDialog = true }
            )
            2 -> HistoryScreen(
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
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("i20 Mileage", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Driving dashboard", style = MaterialTheme.typography.bodyMedium)
                }
                StatusPill(isTracking)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isTracking) "TRIP IN PROGRESS" else "READY TO DRIVE",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                if (isTracking) "GPS distance recording"
                                else "Tap Start before your journey",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Icon(Icons.Default.Timer, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    }

                    Text(
                        activeTrip?.let { formatKm(it.distanceMeters / 1000.0) } ?: "0.0",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("CURRENT TRIP • km", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    Button(
                        onClick = if (isTracking) onStopTracking else onStartTracking,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isTracking) "Stop & Save Trip" else "Start Tracking", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BigStat("Latest", latestMileage?.let { "${formatKm(it)} km/L" } ?: "--", Modifier.weight(1f))
                BigStat("Average", averageMileage?.let { "${formatKm(it)} km/L" } ?: "--", Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BigStat("Today", "${formatKm(todayDistanceKm)} km", Modifier.weight(1f))
                BigStat("All trips", "${formatKm(totalDistanceKm)} km", Modifier.weight(1f))
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalGasStation, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fuel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            latestFuel?.let { "Last fill: ${formatKm(it.litersFilled)} L" } ?: "No fill-up recorded yet",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    FilledTonalButton(onClick = onAddFuel) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add fuel")
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
private fun StatusPill(isTracking: Boolean) {
    val bg = if (isTracking) Color(0xFFDDF4E5) else Color(0xFFE9EEF3)
    val fg = if (isTracking) Color(0xFF1D6A38) else Color(0xFF52606D)
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(7.dp))
        Text(if (isTracking) "TRACKING" else "READY", color = fg, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BigStat(title: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FuelScreen(
    modifier: Modifier,
    fuelLogs: List<FuelLog>,
    latestMileage: Double?,
    onAddFuel: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fuel & Mileage", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Record full-tank fills for accurate km/L", style = MaterialTheme.typography.bodyMedium)
                }
                FilledTonalButton(onClick = onAddFuel) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add fill-up")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BigStat("Latest mileage", latestMileage?.let { "${formatKm(it)} km/L" } ?: "--", Modifier.weight(1f))
                BigStat("Fill-ups", fuelLogs.size.toString(), Modifier.weight(1f))
            }
        }
        if (fuelLogs.isEmpty()) {
            item { EmptyState("No fuel records", "Add each full-tank fill-up to calculate real tank-to-tank mileage.") }
        } else {
            item { Text("Fill-up history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(fuelLogs, key = { it.id }) { FuelRow(it) }
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, trips: List<Trip>, fuelLogs: List<FuelLog>) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
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
            items(fuelLogs, key = { "fuel-${it.id}" }) { FuelRow(it) }
        }
    }
}

@Composable
private fun TripRow(trip: Trip) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(trip.startTimeMillis), fontWeight = FontWeight.SemiBold)
                Text(if (trip.isActive) "In progress" else "Completed", style = MaterialTheme.typography.bodySmall)
            }
            Text("${formatKm(trip.distanceMeters / 1000.0)} km", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FuelRow(fuel: FuelLog) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(fuel.timestampMillis), fontWeight = FontWeight.SemiBold)
                Text(if (fuel.isFullTank) "Full tank" else "Partial fill", style = MaterialTheme.typography.bodySmall)
                fuel.odometerKm?.let { Text("Odometer ${formatKm(it)} km", style = MaterialTheme.typography.bodySmall) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatKm(fuel.litersFilled)} L", fontWeight = FontWeight.Bold)
                fuel.pricePerLiter?.let { Text("₹${formatKm(it)}/L", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Hyundai i20", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("GPS mileage tracker", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Divider()
                Text("Mileage calculation", fontWeight = FontWeight.Bold)
                Text("Mileage is calculated from the distance between two full-tank fill-ups divided by the litres added at the later fill-up.")
            }
        }
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GPS tracking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("The app now calculates trip distance from GPS position changes instead of relying on the vehicle speed value. This is more reliable on Android car head units that report GPS speed as 0.")
            }
        }
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("For accurate mileage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Keep location permission enabled, allow background location when requested, and record every full-tank fill-up.")
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(liters, { liters = it }, label = { Text("Litres added") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Price per litre") }, singleLine = true)
                OutlinedTextField(odometer, { odometer = it }, label = { Text("Odometer km") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, singleLine = true)
                androidx.compose.material3.Checkbox(checked = fullTank, onCheckedChange = { fullTank = it })
                Text("This was a full-tank fill-up")
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
            ) { Text("Save fill-up") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatKm(value: Double): String = String.format(Locale.US, "%.1f", value)
private fun formatDate(time: Long): String = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(time))
