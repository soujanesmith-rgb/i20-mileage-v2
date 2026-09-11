package com.example.i20mileage

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

private val Navy = Color(0xFF07111F)
private val Navy2 = Color(0xFF0B1728)
private val Surface1 = Color(0xFF102137)
private val Surface2 = Color(0xFF142A43)
private val Border = Color(0xFF223A54)
private val PrimaryBlue = Color(0xFF1683FF)
private val Cyan = Color(0xFF20D6E8)
private val Success = Color(0xFF18D69A)
private val Warning = Color(0xFFFFB020)
private val Error = Color(0xFFFF5C67)
private val FuelGreen = Color(0xFF20D6A0)
private val TextPrimary = Color(0xFFF5F8FC)
private val TextSecondary = Color(0xFF91A4B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            I20MileageTheme {
                var ready by remember {
                    mutableStateOf(PermissionState.isFullyReadyForTracking(this@MainActivity))
                }
                Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
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
    val colors = darkColorScheme(
        primary = PrimaryBlue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF0E3560),
        onPrimaryContainer = TextPrimary,
        secondary = Cyan,
        onSecondary = Navy,
        secondaryContainer = Color(0xFF123B4A),
        onSecondaryContainer = TextPrimary,
        background = Navy,
        surface = Surface1,
        onSurface = TextPrimary,
        surfaceVariant = Surface2,
        onSurfaceVariant = TextSecondary,
        outline = Border,
        error = Error
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
    var showStopDialog by remember { mutableStateOf(false) }
    var trackingRequested by remember { mutableStateOf(false) }

    val latestMileage by vm.latestMileage.collectAsState()
    val average by vm.overallAverage.collectAsState()
    val todayDistance by vm.todayDistanceKm.collectAsState()
    val totalDistance by vm.totalDistanceKm.collectAsState()
    val activeTrip by vm.activeTrip.collectAsState()
    val trips by vm.trips.collectAsState()
    val fuelLogs by vm.fuelLogs.collectAsState()
    val isTracking = trackingRequested || activeTrip != null
    var currentSpeedKmh by remember { mutableStateOf(0f) }
    val context = LocalContext.current

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                if (intent?.action == TripLoggingService.ACTION_SPEED_UPDATE) {
                    currentSpeedKmh = intent.getFloatExtra(TripLoggingService.EXTRA_SPEED_KMH, 0f)
                }
            }
        }
        val filter = IntentFilter(TripLoggingService.ACTION_SPEED_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    Scaffold(
        containerColor = Navy,
        bottomBar = {
            AutomotiveNavigation(selectedTab = selectedTab, onSelect = { selectedTab = it })
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
                onStopTracking = { showStopDialog = true },
                onAddFuel = { showFuelDialog = true },
                recentTrips = trips.take(4),
                latestFuel = fuelLogs.firstOrNull(),
                currentSpeedKmh = currentSpeedKmh
            )
            1 -> FuelScreen(
                modifier = Modifier.padding(padding),
                fuelLogs = fuelLogs,
                latestMileage = latestMileage?.kmpl,
                onAddFuel = { showFuelDialog = true }
            )
            2 -> HistoryScreen(modifier = Modifier.padding(padding), trips = trips, fuelLogs = fuelLogs)
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

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            containerColor = Surface1,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Stop trip tracking?", fontWeight = FontWeight.Bold) },
            text = { Text("The current trip will be saved to History.") },
            confirmButton = {
                Button(
                    onClick = {
                        showStopDialog = false
                        trackingRequested = false
                        onStopTracking()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Error)
                ) { Text("Stop & Save") }
            },
            dismissButton = { TextButton(onClick = { showStopDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AutomotiveNavigation(selectedTab: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        "Dashboard" to Icons.Default.Speed,
        "Fuel" to Icons.Default.LocalGasStation,
        "History" to Icons.Default.History,
        "Settings" to Icons.Default.Settings
    )
    Surface(
        color = Navy2,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .border(1.dp, Border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, (label, icon) ->
                val selected = index == selectedTab
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Color(0xFF0E3560) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(icon, contentDescription = label, tint = if (selected) Cyan else TextSecondary, modifier = Modifier.size(24.dp))
                    Text(label, color = if (selected) TextPrimary else TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
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
    latestFuel: FuelLog?,
    currentSpeedKmh: Float
) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val tripDistance = activeTrip?.distanceMeters?.div(1000.0) ?: 0.0
    val animatedTripDistance by animateFloatAsState(
        targetValue = tripDistance.toFloat(),
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "tripDistance"
    )
    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeedKmh.coerceIn(0f, 220f),
        animationSpec = tween(durationMillis = 450, easing = LinearEasing),
        label = "speed"
    )
    val tripDuration = activeTrip?.let { formatDuration(it.startTimeMillis, if (it.isActive) nowMillis else it.endTimeMillis ?: nowMillis) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = maxWidth >= 760.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (landscape) 22.dp else 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DashboardHeader(nowMillis = nowMillis, isTracking = isTracking)
            }

            if (landscape) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CurrentTripPanel(
                            modifier = Modifier.weight(1.02f),
                            animatedTripDistance = animatedTripDistance.toDouble(),
                            tripDuration = tripDuration,
                            isTracking = isTracking,
                            onStartTracking = onStartTracking,
                            onStopTracking = onStopTracking
                        )
                        SpeedometerPanel(modifier = Modifier.weight(0.95f), speedKmh = animatedSpeed)
                        FuelPanel(
                            modifier = Modifier.weight(1.02f),
                            fuel = latestFuel,
                            onAddFuel = onAddFuel
                        )
                    }
                }
            } else {
                item { SpeedometerPanel(modifier = Modifier.fillMaxWidth(), speedKmh = animatedSpeed) }
                item {
                    CurrentTripPanel(
                        modifier = Modifier.fillMaxWidth(),
                        animatedTripDistance = animatedTripDistance.toDouble(),
                        tripDuration = tripDuration,
                        isTracking = isTracking,
                        onStartTracking = onStartTracking,
                        onStopTracking = onStopTracking
                    )
                }
                item { FuelPanel(modifier = Modifier.fillMaxWidth(), fuel = latestFuel, onAddFuel = onAddFuel) }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("LATEST", formatMileage(latestMileage), "km/L", Modifier.weight(1f))
                    MetricCard("AVERAGE", formatMileage(averageMileage), "km/L", Modifier.weight(1f))
                    MetricCard("TODAY", formatThreeDecimal(todayDistanceKm), "km", Modifier.weight(1f))
                    MetricCard("TOTAL", formatThreeDecimal(totalDistanceKm), "km", Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickInsight(latestMileage, averageMileage, Modifier.weight(1f))
                    if (!landscape) FuelSnapshot(latestFuel, onAddFuel, Modifier.weight(1f))
                    else DrivingStatusCard(isTracking, Modifier.weight(1f))
                }
            }

            item { SectionHeader("RECENT TRIPS", "HISTORY") }
            if (recentTrips.isEmpty()) {
                item { EmptyState("No trips recorded yet", "Start tracking your next drive to build your history.", "START TRACKING") }
            } else {
                items(recentTrips, key = { it.id }) { trip -> TripRow(trip) }
            }
        }
    }
}

@Composable
private fun DashboardHeader(nowMillis: Long, isTracking: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("i20 MILEAGE", color = TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Drive smarter. Go farther.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatClock(nowMillis), color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(formatHeaderDate(nowMillis), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            StatusPill(active = isTracking)
        }
    }
}

@Composable
private fun CurrentTripPanel(
    modifier: Modifier,
    animatedTripDistance: Double,
    tripDuration: String?,
    isTracking: Boolean,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Cyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("CURRENT TRIP", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                StatusPill(active = isTracking)
            }
            Divider(color = Border)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DataBlock("DISTANCE", formatTripDistance(animatedTripDistance), "km", Modifier.weight(1f))
                DataBlock("DURATION", tripDuration ?: "00:00", "", Modifier.weight(1f))
                DataBlock("STATUS", if (isTracking) "LIVE" else "READY", "", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (isTracking) "GPS distance tracking is active" else "Start when you begin driving", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if (isTracking) {
                    Button(
                        onClick = onStopTracking,
                        colors = ButtonDefaults.buttonColors(containerColor = Error),
                        shape = RoundedCornerShape(50),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("STOP TRIP", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStartTracking,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(50),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("START TRIP", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DataBlock(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, color = Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SpeedometerPanel(modifier: Modifier, speedKmh: Float) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Navy2),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
            Speedometer(speedKmh = speedKmh)
        }
    }
}

@Composable
private fun Speedometer(speedKmh: Float) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        val dialSize = minOf(maxWidth.value, maxHeight.value).dp
        val animatedArc by animateFloatAsState(speedKmh / 220f, tween(450), label = "speedArc")
        Canvas(modifier = Modifier.size(dialSize)) {
            val center = androidx.compose.ui.geometry.Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension * 0.40f
            val stroke = this.size.minDimension * 0.035f
            drawCircle(color = Color(0xFF0A1B2E), radius = radius * 1.15f, center = center)
            drawArc(
                color = Border,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Cyan, PrimaryBlue, Error)),
                startAngle = 135f,
                sweepAngle = 270f * animatedArc,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke * 1.5f, cap = StrokeCap.Round)
            )
            for (i in 0..22) {
                val angle = Math.toRadians((135 + i * (270.0 / 22.0)).toDouble())
                val outer = radius * 1.03f
                val inner = if (i % 2 == 0) radius * 0.90f else radius * 0.94f
                val p1 = androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner)
                val p2 = androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer)
                drawLine(if (i <= (speedKmh / 10f).roundToInt()) Cyan else TextSecondary, p1, p2, strokeWidth = if (i % 2 == 0) stroke * 0.8f else stroke * 0.45f, cap = StrokeCap.Round)
            }
            val needleAngle = Math.toRadians((135.0 + (speedKmh / 220f) * 270.0))
            val needleLength = radius * 0.76f
            val needleEnd = androidx.compose.ui.geometry.Offset(
                center.x + cos(needleAngle).toFloat() * needleLength,
                center.y + sin(needleAngle).toFloat() * needleLength
            )
            drawLine(Cyan, center, needleEnd, strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
            drawCircle(color = TextPrimary, radius = stroke * 1.8f, center = center)
            drawCircle(color = Cyan, radius = stroke * 0.8f, center = center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SPEED", color = TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(speedKmh.roundToInt().toString(), color = TextPrimary, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text("km/h", color = Cyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text("0", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 24.dp))
        Text("110", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
        Text("220", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 24.dp))
    }
}

@Composable
private fun FuelPanel(modifier: Modifier, fuel: FuelLog?, onAddFuel: () -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF103A2F)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = FuelGreen, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("FUEL STATUS", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextSecondary)
            }
            if (fuel == null) {
                Text("No fuel records yet", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Add a full-tank entry to calculate mileage.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    StatPair("LAST FILL", "${formatOneDecimal(fuel.litersFilled)} L")
                    StatPair("PRICE", fuel.pricePerLiter?.let { "₹${formatOneDecimal(it)}/L" } ?: "—")
                }
                Text(formatDate(fuel.timestampMillis) + if (fuel.isFullTank) "  ·  FULL TANK" else "  ·  PARTIAL", color = FuelGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onAddFuel, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue), shape = RoundedCornerShape(50), modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ADD FUEL", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DrivingStatusCard(isTracking: Boolean, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF0C2036)), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E486D))) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("DRIVING STATUS", color = TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(if (isTracking) "TRACKING ACTIVE" else "READY TO DRIVE", color = if (isTracking) Success else TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (isTracking) "GPS distance and live speed are updating." else "Start tracking when you begin your drive.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (active) Color(0xFF103A2F) else Color(0xFF14263A)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (active) Success else TextSecondary))
        Spacer(Modifier.width(7.dp))
        Text(if (active) "TRACKING" else "READY", color = if (active) Success else TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Text(unit, color = Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FuelSnapshot(fuel: FuelLog?, onAddFuel: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF103A2F)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = FuelGreen)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("FUEL STATUS", color = TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextSecondary)
            }
            if (fuel == null) {
                Text("No fuel records yet", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Add a full-tank entry to calculate mileage.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick = onAddFuel, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF123B4A), contentColor = Cyan)) { Text("ADD FUEL") }
            } else {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    StatPair("LAST FILL", "${formatOneDecimal(fuel.litersFilled)} L")
                    StatPair("PRICE", fuel.pricePerLiter?.let { "₹${formatOneDecimal(it)}/L" } ?: "Not set")
                    StatPair("ODOMETER", fuel.odometerKm?.let { formatOneDecimal(it) } ?: "Not set")
                }
                Text(formatDate(fuel.timestampMillis) + if (fuel.isFullTank) "  ·  FULL TANK" else "  ·  PARTIAL", color = FuelGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QuickInsight(latest: Double?, average: Double?, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF0C2036)), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E486D))) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MILEAGE INSIGHT", color = TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(formatMileage(latest) + " km/L", color = Cyan, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (latest != null && average != null) {
                val diff = ((latest - average) / average * 100).roundToInt()
                if (diff >= 0) "$diff% above your overall average" else "${-diff}% below your overall average"
            } else "Record two full-tank fills to unlock mileage comparison.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (action != null) Text(action, color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TripRow(trip: Trip) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF123B4A)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Cyan, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(trip.startTimeMillis), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(formatDuration(trip.startTimeMillis, trip.endTimeMillis ?: System.currentTimeMillis()), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatOneDecimal(trip.distanceMeters / 1000.0)} km", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(if (trip.isActive) "ONGOING" else "COMPLETED", color = if (trip.isActive) Success else TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FuelScreen(modifier: Modifier, fuelLogs: List<FuelLog>, latestMileage: Double?, onAddFuel: () -> Unit) {
    val totalFuel = fuelLogs.sumOf { it.litersFilled }
    val totalCost = fuelLogs.sumOf { (it.pricePerLiter ?: 0.0) * it.litersFilled }
    val priced = fuelLogs.filter { it.pricePerLiter != null }
    val avgPrice = if (priced.isNotEmpty()) priced.sumOf { it.pricePerLiter!! * it.litersFilled } / priced.sumOf { it.litersFilled } else null

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("FUEL", "Fill-ups, cost and mileage inputs") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF103A2F)), contentAlignment = Alignment.Center) { Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = FuelGreen, modifier = Modifier.size(28.dp)) }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("FUEL LOG", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Keep full-tank entries consistent for reliable mileage.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = onAddFuel, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("ADD FUEL") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard("TOTAL FUEL", formatOneDecimal(totalFuel), "L", Modifier.weight(1f))
                        MetricCard("AVG PRICE", avgPrice?.let { "₹${formatOneDecimal(it)}" } ?: "—", "/L", Modifier.weight(1f))
                        MetricCard("TOTAL COST", if (totalCost > 0) "₹${formatWhole(totalCost)}" else "—", "", Modifier.weight(1f))
                        MetricCard("LATEST", formatMileage(latestMileage), "km/L", Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionHeader("FUEL HISTORY") }
        if (fuelLogs.isEmpty()) item { EmptyState("No fuel records yet", "Add your first full-tank fill-up to start calculating mileage.", "ADD FUEL", onAddFuel) }
        else items(fuelLogs, key = { it.id }) { FuelRow(it) }
    }
}

@Composable
private fun FuelRow(fuel: FuelLog) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF103A2F)), contentAlignment = Alignment.Center) { Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = FuelGreen, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(fuel.timestampMillis), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(if (fuel.isFullTank) "Full tank" else "Partial fill", color = if (fuel.isFullTank) FuelGreen else Warning, style = MaterialTheme.typography.bodySmall)
                fuel.odometerKm?.let { Text("Odometer ${formatOneDecimal(it)} km", color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatOneDecimal(fuel.litersFilled)} L", color = TextPrimary, fontWeight = FontWeight.Bold)
                fuel.pricePerLiter?.let { Text("₹${formatOneDecimal(it)}/L", color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, trips: List<Trip>, fuelLogs: List<FuelLog>) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("TRIP HISTORY", "Your recorded drives") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("TRIPS", trips.size.toString(), "", Modifier.weight(1f))
                MetricCard("DISTANCE", formatOneDecimal(trips.sumOf { it.distanceMeters } / 1000.0), "km", Modifier.weight(1f))
                MetricCard("FUEL ENTRIES", fuelLogs.size.toString(), "", Modifier.weight(1f))
            }
        }
        item { SectionHeader("RECORDED TRIPS") }
        if (trips.isEmpty()) item { EmptyState("No trips yet", "Your completed drives will appear here.", null) }
        else items(trips, key = { it.id }) { TripRow(it) }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("SETTINGS", "Vehicle, tracking and app information")
        SettingsGroup("VEHICLE") {
            SettingsRow(Icons.Default.DirectionsCar, "Hyundai i20", "Petrol · Mileage tracker")
        }
        SettingsGroup("TRACKING") {
            SettingsRow(Icons.Default.LocationOn, "GPS distance tracking", "Uses position changes instead of GPS speed")
            SettingsRow(Icons.Default.Timer, "Trip updates", "High-accuracy location every few seconds")
        }
        SettingsGroup("MILEAGE") {
            SettingsRow(Icons.Default.LocalGasStation, "Full-tank method", "Odometer difference is preferred when both fill-ups have readings")
            SettingsRow(Icons.Default.CheckCircle, "Recommended logging", "Record each full-tank fill with exact litres and odometer")
        }
        SettingsGroup("ABOUT") {
            SettingsRow(Icons.Default.Info, "i20 Mileage", "Version 3.0 · Optimized for Nakamichi NAM5240")
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) { content() }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF123B4A)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = Cyan) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFuelDialog(onDismiss: () -> Unit, onSave: (Double, Double?, Double?, Boolean, String?) -> Unit) {
    var liters by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var fullTank by remember { mutableStateOf(true) }
    val valid = liters.toDoubleOrNull()?.let { it > 0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("ADD FUEL", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the exact pump and odometer values.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = liters, onValueChange = { liters = it }, label = { Text("Litres added") }, suffix = { Text("L") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price / litre") }, prefix = { Text("₹") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Odometer km") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Navy2).clickable { fullTank = !fullTank }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (fullTank) "✓" else "○", color = if (fullTank) FuelGreen else TextSecondary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("This was a full-tank fill-up", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Recommended for mileage calculations", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onSave(liters.toDouble(), price.toDoubleOrNull(), odometer.toDoubleOrNull(), fullTank, notes.ifBlank { null }) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) { Text("SAVE FILL-UP", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, action: String?, onAction: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF123B4A)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Info, contentDescription = null, tint = Cyan) }
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (action != null) Button(onClick = { onAction?.invoke() }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue), shape = RoundedCornerShape(12.dp)) { Text(action) }
        }
    }
}

@Composable
private fun StatPair(label: String, value: String) {
    Column {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}

private fun formatOneDecimal(value: Double): String = String.format(Locale.US, "%,.1f", value)
private fun formatTripDistance(value: Double): String = String.format(Locale.US, "%.3f", value)
private fun formatWhole(value: Double): String = String.format(Locale.US, "%,.0f", value)
private fun formatMileage(value: Double?): String = value?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
private fun formatKm(value: Double): String = String.format(Locale.US, "%.1f", value)
private fun formatClock(timeMillis: Long): String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timeMillis))

private fun formatHeaderDate(timeMillis: Long): String = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(timeMillis))

private fun formatThreeDecimal(value: Double): String = String.format(Locale.getDefault(), "%.3f", value)

private fun formatDate(millis: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(millis))
private fun formatDuration(start: Long, end: Long): String {
    val seconds = ((end - start).coerceAtLeast(0L)) / 1000L
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val secs = seconds % 60L
    return if (hours > 0) String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs) else String.format(Locale.US, "%02d:%02d", minutes, secs)
}
