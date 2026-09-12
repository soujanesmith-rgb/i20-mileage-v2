package com.example.i20mileage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.i20mileage.data.FuelLog
import com.example.i20mileage.data.Trip
import com.example.i20mileage.permissions.PermissionOnboardingScreen
import com.example.i20mileage.permissions.PermissionState
import com.example.i20mileage.service.TripLoggingService
import com.example.i20mileage.ui.MileageViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Bg = Color(0xFF020A14)
private val Bg2 = Color(0xFF061526)
private val Surface = Color(0xFF0A1B2C)
private val Surface2 = Color(0xFF0D2238)
private val Border = Color(0xFF1E3550)
private val BorderBright = Color(0xFF1268A8)
private val Blue = Color(0xFF00A3FF)
private val Cyan = Color(0xFF00E5FF)
private val Green = Color(0xFF00E676)
private val Red = Color(0xFFFF3B3B)
private val Orange = Color(0xFFFFA726)
private val Purple = Color(0xFF6C63FF)
private val White = Color(0xFFF7FAFF)
private val Secondary = Color(0xFFA0B3CC)

private const val DESIGN_W = 1818f
private const val DESIGN_H = 1024f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            I20MileageTheme {
                var ready by remember { mutableStateOf(PermissionState.isFullyReadyForTracking(this@MainActivity)) }
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    if (ready) MileageApp(
                        onStartTracking = { startTripLoggingService() },
                        onStopTracking = { stopTripLoggingService() }
                    ) else PermissionOnboardingScreen(onAllRequiredGranted = { ready = true })
                }
            }
        }
    }

    private fun startTripLoggingService() {
        if (!PermissionState.hasForegroundLocation(this)) return
        val intent = Intent(this, TripLoggingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopTripLoggingService() {
        startService(Intent(this, TripLoggingService::class.java).apply { action = TripLoggingService.ACTION_STOP })
    }
}

@Composable
private fun I20MileageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue,
            secondary = Cyan,
            background = Bg,
            surface = Surface,
            onSurface = White,
            onSurfaceVariant = Secondary,
            outline = Border,
            error = Red
        ),
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
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == TripLoggingService.ACTION_SPEED_UPDATE) {
                    currentSpeedKmh = intent.getFloatExtra(TripLoggingService.EXTRA_SPEED_KMH, 0f)
                }
            }
        }
        val filter = IntentFilter(TripLoggingService.ACTION_SPEED_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    when (selectedTab) {
        0 -> ReferenceDashboard(
            todayDistanceKm = todayDistance,
            totalDistanceKm = totalDistance,
            latestMileage = latestMileage?.kmpl,
            averageMileage = average,
            activeTrip = activeTrip,
            isTracking = isTracking,
            recentTrips = trips.take(2),
            latestFuel = fuelLogs.firstOrNull(),
            speedKmh = currentSpeedKmh,
            onStartTracking = { trackingRequested = true; onStartTracking() },
            onStopTracking = { showStopDialog = true },
            onAddFuel = { showFuelDialog = true },
            onViewHistory = { selectedTab = 2 },
            onFuel = { selectedTab = 1 },
            onSettings = { selectedTab = 3 }
        )
        1 -> FuelScreen(Modifier.fillMaxSize(), fuelLogs, latestMileage?.kmpl) { showFuelDialog = true }
        2 -> HistoryScreen(Modifier.fillMaxSize(), trips, fuelLogs)
        else -> SettingsScreen(Modifier.fillMaxSize())
    }

    if (showFuelDialog) AddFuelDialog(
        onDismiss = { showFuelDialog = false },
        onSave = { liters, price, odometer, full, notes ->
            vm.logFuelFillUp(liters, price, odometer, full, notes)
            showFuelDialog = false
        }
    )
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            containerColor = Surface,
            titleContentColor = White,
            textContentColor = Secondary,
            title = { Text("Stop trip tracking?", fontWeight = FontWeight.Bold) },
            text = { Text("The current trip will be saved to History.") },
            confirmButton = {
                Button(onClick = {
                    showStopDialog = false
                    trackingRequested = false
                    onStopTracking()
                }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Stop & Save") }
            },
            dismissButton = { TextButton(onClick = { showStopDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ReferenceDashboard(
    todayDistanceKm: Double,
    totalDistanceKm: Double,
    latestMileage: Double?,
    averageMileage: Double?,
    activeTrip: Trip?,
    isTracking: Boolean,
    recentTrips: List<Trip>,
    latestFuel: FuelLog?,
    speedKmh: Float,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onAddFuel: () -> Unit,
    onViewHistory: () -> Unit,
    onFuel: () -> Unit,
    onSettings: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val tripDistance = activeTrip?.distanceMeters?.div(1000.0) ?: 0.0
    val duration = activeTrip?.let { formatDuration(it.startTimeMillis, if (it.isActive) now else it.endTimeMillis ?: now) } ?: "00:28"
    val speed by animateFloatAsState(speedKmh.coerceIn(0f, 220f), tween(220, easing = LinearEasing), label = "speed")

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val scale = minOf(maxWidth.value / DESIGN_W, maxHeight.value / DESIGN_H)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.width(DESIGN_W.dp).height(DESIGN_H.dp).graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(0.dp))
            ) {
                DashboardAtmosphere(Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    ReferenceHeader(now)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().height(286.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CurrentTripReference(Modifier.weight(1.08f), tripDistance, duration, isTracking, speed, onStartTracking, onStopTracking)
                        SpeedometerReference(Modifier.width(590.dp).fillMaxHeight(), speed)
                        FuelReference(Modifier.weight(1.08f), latestFuel, onAddFuel)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ReferenceMetric("LATEST", latestMileage?.let { formatMileage(it) } ?: "—", "km/L", Icons.Default.Eco, Blue, Modifier.weight(1f))
                        ReferenceMetric("AVERAGE", averageMileage?.let { formatMileage(it) } ?: "—", "km/L", Icons.Default.BarChart, Green, Modifier.weight(1f))
                        ReferenceMetric("TODAY", formatThreeDecimal(todayDistanceKm), "km", Icons.Default.TurnRight, Orange, Modifier.weight(1f))
                        ReferenceMetric("TOTAL", formatThreeDecimal(totalDistanceKm), "km", Icons.Default.Directions, Purple, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(155.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RecentTripsReference(Modifier.weight(1.62f), recentTrips, onViewHistory)
                        InsightReference(Modifier.weight(0.82f), latestMileage, averageMileage)
                    }
                    Spacer(Modifier.height(12.dp))
                    ReferenceBottomNavigation(Modifier.fillMaxWidth().height(92.dp), onFuel, onViewHistory, onSettings)
                }
            }
        }
    }
}

@Composable
private fun DashboardAtmosphere(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF03101F), Bg)))
        val horizon = size.height * .20f
        val path = Path().apply {
            moveTo(0f, horizon + 60)
            lineTo(size.width * .12f, horizon + 10)
            lineTo(size.width * .20f, horizon + 44)
            lineTo(size.width * .28f, horizon - 8)
            lineTo(size.width * .35f, horizon + 32)
            lineTo(size.width * .43f, horizon - 35)
            lineTo(size.width * .51f, horizon + 30)
            lineTo(size.width * .62f, horizon - 14)
            lineTo(size.width * .72f, horizon + 40)
            lineTo(size.width * .81f, horizon + 4)
            lineTo(size.width, horizon + 38)
            lineTo(size.width, horizon + 100)
            lineTo(0f, horizon + 100)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFF0A2038), Color(0xFF061425))))
        drawRect(Color(0xFF00A3FF).copy(alpha = .035f), topLeft = androidx.compose.ui.geometry.Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(size.width, size.height - horizon))
    }
}

@Composable
private fun ReferenceHeader(now: Long) {
    Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("i20", color = White, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    Spacer(Modifier.width(5.dp))
                    Text("MILEAGE", color = Blue, fontSize = 37.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("Drive smarter. Go farther.", color = Secondary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(18.dp))
            CarSilhouette(Modifier.width(170.dp).height(58.dp))
        }
        ClockCard(now)
        Spacer(Modifier.width(12.dp))
        WeatherCard()
    }
}

@Composable
private fun CarSilhouette(modifier: Modifier) {
    Canvas(modifier) {
        val p = Path().apply {
            moveTo(size.width*.08f, size.height*.68f)
            cubicTo(size.width*.18f, size.height*.45f, size.width*.27f, size.height*.18f, size.width*.53f, size.height*.16f)
            cubicTo(size.width*.70f, size.height*.15f, size.width*.79f, size.height*.35f, size.width*.88f, size.height*.46f)
            lineTo(size.width*.96f, size.height*.55f)
            lineTo(size.width*.93f, size.height*.70f)
            lineTo(size.width*.11f, size.height*.70f)
            close()
        }
        drawPath(p, Brush.horizontalGradient(listOf(Color(0xFF0076C9), Blue, Cyan.copy(alpha=.8f))), style = Stroke(width = 3f))
        drawLine(Offset(size.width*.29f, size.height*.45f), Offset(size.width*.65f, size.height*.42f), Color(0xFF0A8CD9), 2f)
        drawCircle(Blue, size.height*.10f, Offset(size.width*.27f, size.height*.72f))
        drawCircle(Blue, size.height*.10f, Offset(size.width*.78f, size.height*.72f))
    }
}

@Composable
private fun ClockCard(now: Long) {
    Box(Modifier.width(250.dp).height(68.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF061526).copy(alpha=.94f)).border(1.dp, Border, RoundedCornerShape(20.dp)).padding(horizontal = 22.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(formatClock(now), color = White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text(formatHeaderDate(now), color = Secondary, fontSize = 17.sp)
        }
    }
}

@Composable
private fun WeatherCard() {
    Row(Modifier.width(238.dp).height(68.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF061526).copy(alpha=.94f)).border(1.dp, Border, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("☀️", fontSize = 31.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Good Drive", color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Stay Safe", color = Cyan, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ReferenceCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = .92f)), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxSize().padding(20.dp), content = content)
    }
}

@Composable
private fun CurrentTripReference(modifier: Modifier, distance: Double, duration: String, tracking: Boolean, currentSpeed: Float, onStart: () -> Unit, onStop: () -> Unit) {
    ReferenceCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, null, tint = Secondary, modifier = Modifier.size(27.dp))
            Spacer(Modifier.width(10.dp))
            Text("CURRENT TRIP", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (tracking) Green else Secondary))
            Spacer(Modifier.width(8.dp))
            Text(if (tracking) "Tracking..." else "Ready", color = if (tracking) Green else Secondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp)); Divider(color = Border)
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TripData("DISTANCE", formatTripDistance(distance), "km", Icons.Default.LocationOn, Modifier.weight(1f))
            VerticalDivider()
            TripData("DURATION", duration, "h : min", Icons.Default.Timer, Modifier.weight(1f))
            VerticalDivider()
            TripData("AVG. SPEED", currentSpeed.roundToInt().toString(), "km/h", Icons.Default.Speed, Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp).clip(CircleShape).background(Red.copy(alpha=.92f)).border(3.dp, Red, CircleShape).clickable { if (tracking) onStop() else onStart() }, contentAlignment = Alignment.Center) {
                Icon(if (tracking) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(if (tracking) "STOP TRIP" else "START TRIP", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Save this trip", color = Secondary, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF0C2138)).border(1.dp, BorderBright, CircleShape), contentAlignment = Alignment.Center) {
                Text("•••", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TripData(label: String, value: String, unit: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Secondary, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(7.dp)); Text(label, color = Secondary, fontSize = 14.sp) }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) { Text(value, color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(4.dp)); Text(unit, color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp)) }
    }
}

@Composable
private fun VerticalDivider() { Box(Modifier.width(1.dp).height(65.dp).background(Border)) }

@Composable
private fun SpeedometerReference(modifier: Modifier, speed: Float) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Speedometer(speed)
    }
}

@Composable
private fun Speedometer(speedKmh: Float) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val d = minOf(maxWidth.value, maxHeight.value).dp
        Canvas(Modifier.size(d)) {
            val c = Offset(size.width/2f, size.height/2f)
            val r = size.minDimension * .405f
            drawCircle(Color(0xFF061524), r*1.10f, c)
            drawCircle(Color(0xFF0B2238), r*1.03f, c, style = Stroke(size.minDimension*.018f))
            drawArc(Brush.sweepGradient(listOf(Blue, Cyan, Blue, Red)), 135f, 270f, false, Offset(c.x-r,c.y-r), Size(r*2,r*2), style = Stroke(size.minDimension*.032f))
            for (i in 0..44) {
                val deg = 135.0 + i * (270.0/44.0)
                val a = Math.toRadians(deg)
                val outer = r*1.0f
                val inner = if (i%4==0) r*.885f else r*.925f
                val p1 = Offset(c.x + cos(a).toFloat()*inner, c.y + sin(a).toFloat()*inner)
                val p2 = Offset(c.x + cos(a).toFloat()*outer, c.y + sin(a).toFloat()*outer)
                val col = when { i >= 36 -> Red; i <= 12 -> Blue; else -> Color(0xFFB9C8DA) }
                drawLine(col, p1, p2, strokeWidth = if (i%4==0) 3.2f else 1.5f)
            }
            for (v in 0..11) {
                val value = v*20
                val deg = 135.0 + (value/220.0)*270.0
                val a = Math.toRadians(deg)
                val rr = r*.77f
                val p = Offset(c.x + cos(a).toFloat()*rr, c.y + sin(a).toFloat()*rr)
                drawCircle(Color.Transparent, 1f, p)
            }
            val needleDeg = 135.0 + speedKmh/220.0*270.0
            val na = Math.toRadians(needleDeg)
            val tip = Offset(c.x + cos(na).toFloat()*r*.72f, c.y + sin(na).toFloat()*r*.72f)
            val leftA = Math.toRadians(needleDeg+170)
            val rightA = Math.toRadians(needleDeg-170)
            val base = r*.045f
            val needle = Path().apply { moveTo(c.x + cos(leftA).toFloat()*base, c.y + sin(leftA).toFloat()*base); lineTo(tip.x, tip.y); lineTo(c.x + cos(rightA).toFloat()*base, c.y + sin(rightA).toFloat()*base); close() }
            drawPath(needle, Brush.linearGradient(listOf(White, Cyan)))
            drawCircle(Color(0xFF05101D), r*.23f, c)
            drawCircle(BorderBright, r*.23f, c, style = Stroke(size.minDimension*.009f))
            drawCircle(Cyan, size.minDimension*.018f, c)
        }
        val radius = d.value/2f
        for (value in 0..11) {
            val v = value*20
            val angle = Math.toRadians(135.0 + (v/220.0)*270.0)
            val x = (radius + cos(angle)*radius*.77).dp
            val y = (radius + sin(angle)*radius*.77).dp
            Text(v.toString(), color = if (v>=180) Red else White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.offset(x - radius.dp - 12.dp, y - radius.dp - 11.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("km/h", color = Secondary, fontSize = 16.sp)
            Text(speedKmh.roundToInt().toString(), color = White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            Text("km/h", color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FuelReference(modifier: Modifier, fuel: FuelLog?, onAddFuel: () -> Unit) {
    ReferenceCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF073D35)), contentAlignment = Alignment.Center) { Icon(Icons.Default.LocalGasStation, null, tint = Green, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.width(12.dp)); Text("FUEL STATUS", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Secondary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp)); Divider(color = Border); Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, BorderBright, RoundedCornerShape(7.dp)).padding(4.dp)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(if (fuel == null) .58f else .72f).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(Cyan, Green))))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("E", color = Secondary, fontSize = 13.sp); Text("F", color = Secondary, fontSize = 13.sp) }
            }
            Spacer(Modifier.width(28.dp))
            Column(horizontalAlignment = Alignment.End) { Text("${estimateRange(fuel)}", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("km", color = Secondary, fontSize = 14.sp); Text("Est. Range", color = Secondary, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(11.dp)); Divider(color = Border); Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (fuel == null) "No fuel records yet" else "Latest full-tank entry", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(if (fuel == null) "Add a full-tank entry to calculate mileage." else "${formatOneDecimal(fuel.litersFilled)} L logged", color = Secondary, fontSize = 14.sp)
            }
            Button(onClick = onAddFuel, colors = ButtonDefaults.buttonColors(containerColor = Blue), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("ADD FUEL", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ReferenceMetric(label: String, value: String, unit: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(accent.copy(alpha=.14f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.width(18.dp))
            Column { Text(label, color = Secondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Row(verticalAlignment = Alignment.Bottom) { Text(value, color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(7.dp)); Text(unit, color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp)) } }
        }
    }
}

@Composable
private fun RecentTripsReference(modifier: Modifier, trips: List<Trip>, onViewAll: () -> Unit) {
    ReferenceCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = Secondary, modifier = Modifier.size(27.dp)); Spacer(Modifier.width(10.dp)); Text("RECENT TRIPS", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("View All", color = Secondary, fontSize = 15.sp, modifier = Modifier.clickable { onViewAll() }); Icon(Icons.Default.ChevronRight, null, tint = Secondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp)); Divider(color = Border)
        if (trips.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { Text("No trips recorded yet", color = Secondary, fontSize = 15.sp) }
        } else {
            trips.forEachIndexed { index, trip ->
                if (index > 0) Divider(color = Border)
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDate(trip.startTimeMillis), color = White, fontSize = 14.sp, modifier = Modifier.width(145.dp))
                    Text(formatTimeRange(trip), color = Color(0xFF4B91D0), fontSize = 13.sp, modifier = Modifier.width(185.dp))
                    Text("${formatThreeDecimal(trip.distanceMeters/1000.0)} km", color = White, fontSize = 14.sp, modifier = Modifier.width(145.dp))
                    Text(formatDuration(trip.startTimeMillis, trip.endTimeMillis ?: System.currentTimeMillis()), color = White, fontSize = 14.sp, modifier = Modifier.width(125.dp))
                    Text("—", color = Secondary, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun InsightReference(modifier: Modifier, latest: Double?, average: Double?) {
    ReferenceCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.BarChart, null, tint = Secondary, modifier = Modifier.size(27.dp)); Spacer(Modifier.width(10.dp)); Text("MILEAGE INSIGHT", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Secondary) }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(17.dp)).background(Color(0xFF0B2035)).border(1.dp, Border, RoundedCornerShape(17.dp)).padding(16.dp)) {
            Column {
                Text(if (latest != null && average != null) "Your latest mileage is ${formatMileage(latest)} km/L" else "Add two full-tank fills", color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (latest != null && average != null) "Compared with your overall average" else "to unlock mileage comparison", color = Secondary, fontSize = 15.sp)
            }
            MiniBars(Modifier.align(Alignment.BottomEnd).width(75.dp).height(58.dp))
        }
    }
}

@Composable
private fun MiniBars(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width/5f
        listOf(.32f,.50f,.72f,1f).forEachIndexed { i, h ->
            val left = i*w*1.25f
            drawRoundRect(color = Color(0xFF3C79AE), topLeft = Offset(left, size.height*(1-h)), size = Size(w*.65f, size.height*h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f))
        }
    }
}

@Composable
private fun ReferenceBottomNavigation(modifier: Modifier, onFuel: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    Row(modifier.border(1.dp, Border).background(Color(0xFF030D19)), verticalAlignment = Alignment.CenterVertically) {
        BottomNavItem("Dashboard", Icons.Default.Speed, true, Modifier.weight(1.2f)) { }
        BottomNavItem("Fuel", Icons.Default.LocalGasStation, false, Modifier.weight(1f), onFuel)
        BottomNavDivider()
        BottomNavItem("History", Icons.Default.History, false, Modifier.weight(1f), onHistory)
        BottomNavDivider()
        BottomNavItem("Settings", Icons.Default.Settings, false, Modifier.weight(1f), onSettings)
        Box(Modifier.weight(1.1f).fillMaxHeight(), contentAlignment = Alignment.Center) { HyundaiMark() }
    }
}

@Composable
private fun BottomNavDivider() { Box(Modifier.width(1.dp).height(42.dp).background(Border)) }

@Composable
private fun BottomNavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight().padding(horizontal = 8.dp, vertical = 7.dp).clip(RoundedCornerShape(17.dp)).background(if (selected) Color(0xFF0B3B76) else Color.Transparent).border(if (selected) 1.dp else 0.dp, if (selected) Blue else Color.Transparent, RoundedCornerShape(17.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(icon, null, tint = if (selected) Cyan else Secondary, modifier = Modifier.size(28.dp)); Text(label, color = if (selected) White else Secondary, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
    }
}

@Composable
private fun HyundaiMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp, 34.dp).border(2.dp, Secondary, RoundedCornerShape(50))) { Text("H", color = Secondary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center)) }
        Spacer(Modifier.width(10.dp)); Column { Text("i20", color = Secondary, fontSize = 27.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic); Text("BETTER JOURNEYS", color = Secondary, fontSize = 11.sp, letterSpacing = 2.sp) }
    }
}

private fun estimateRange(fuel: FuelLog?): Int = if (fuel == null) 320 else (fuel.litersFilled * 18.0).roundToInt().coerceAtLeast(0)
private fun formatTimeRange(trip: Trip): String = "${SimpleDateFormat("hh:mm a", Locale.US).format(Date(trip.startTimeMillis))} – ${SimpleDateFormat("hh:mm a", Locale.US).format(Date(trip.endTimeMillis ?: System.currentTimeMillis()))}"

@Composable
private fun FuelScreen(modifier: Modifier, fuelLogs: List<FuelLog>, latestMileage: Double?, onAddFuel: () -> Unit) {
    LazyColumn(modifier, contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("FUEL", "Fill-ups, cost and mileage inputs") }
        item { Button(onClick = onAddFuel, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("ADD FUEL") } }
        items(fuelLogs, key = { it.id }) { FuelRow(it) }
        if (fuelLogs.isEmpty()) item { EmptyState("No fuel records yet", "Add your first full-tank fill-up to start calculating mileage.", "ADD FUEL", onAddFuel) }
    }
}

@Composable
private fun FuelRow(fuel: FuelLog) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF103A2F)), contentAlignment = Alignment.Center) { Icon(Icons.Default.LocalGasStation, null, tint = Green) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(formatDate(fuel.timestampMillis), color = White, fontWeight = FontWeight.SemiBold); Text(if (fuel.isFullTank) "Full tank" else "Partial fill", color = if (fuel.isFullTank) Green else Orange) }
            Column(horizontalAlignment = Alignment.End) { Text("${formatOneDecimal(fuel.litersFilled)} L", color = White, fontWeight = FontWeight.Bold); fuel.pricePerLiter?.let { Text("₹${formatOneDecimal(it)}/L", color = Secondary) } }
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, trips: List<Trip>, fuelLogs: List<FuelLog>) {
    LazyColumn(modifier, contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("TRIP HISTORY", "Your recorded drives") }
        items(trips, key = { it.id }) { TripRow(it) }
        if (trips.isEmpty()) item { EmptyState("No trips yet", "Your completed drives will appear here.", null) }
    }
}

@Composable
private fun TripRow(trip: Trip) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF123B4A)), contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsCar, null, tint = Cyan) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(formatDate(trip.startTimeMillis), color = White, fontWeight = FontWeight.SemiBold); Text(formatDuration(trip.startTimeMillis, trip.endTimeMillis ?: System.currentTimeMillis()), color = Secondary) }
            Column(horizontalAlignment = Alignment.End) { Text("${formatOneDecimal(trip.distanceMeters/1000.0)} km", color = White, fontWeight = FontWeight.Bold); Text(if (trip.isActive) "ONGOING" else "COMPLETED", color = if (trip.isActive) Green else Secondary) }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("SETTINGS", "Vehicle, tracking and app information")
        SettingsGroup("VEHICLE") { SettingsRow(Icons.Default.DirectionsCar, "Hyundai i20", "Petrol · Mileage tracker") }
        SettingsGroup("TRACKING") { SettingsRow(Icons.Default.LocationOn, "GPS distance tracking", "Uses position changes instead of GPS speed"); SettingsRow(Icons.Default.Timer, "Trip updates", "High-accuracy location every few seconds") }
        SettingsGroup("MILEAGE") { SettingsRow(Icons.Default.LocalGasStation, "Full-tank method", "Odometer difference is preferred when both fill-ups have readings"); SettingsRow(Icons.Default.CheckCircle, "Recommended logging", "Record each full-tank fill with exact litres and odometer") }
        SettingsGroup("ABOUT") { SettingsRow(Icons.Default.Info, "i20 Mileage", "Version 4.0 · Optimized for Nakamichi NAM5240") }
    }
}

@Composable private fun SettingsGroup(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, color = Secondary, fontWeight = FontWeight.Bold); Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) { content() } } }
@Composable private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF123B4A)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Cyan) }; Spacer(Modifier.width(12.dp)); Column { Text(title, color = White, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Secondary) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFuelDialog(onDismiss: () -> Unit, onSave: (Double, Double?, Double?, Boolean, String?) -> Unit) {
    var liters by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var odometer by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var fullTank by remember { mutableStateOf(true) }
    val valid = liters.toDoubleOrNull()?.let { it > 0 } == true
    AlertDialog(onDismissRequest = onDismiss, containerColor = Surface, titleContentColor = White, textContentColor = Secondary, title = { Text("ADD FUEL", fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Enter the exact pump and odometer values."); OutlinedTextField(liters, { liters = it }, label = { Text("Litres") }); OutlinedTextField(price, { price = it }, label = { Text("Price / litre") }); OutlinedTextField(odometer, { odometer = it }, label = { Text("Odometer km") }); OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(fullTank, { fullTank = it }); Text("Full tank") } } }, confirmButton = { Button(enabled = valid, onClick = { onSave(liters.toDouble(), price.toDoubleOrNull(), odometer.toDoubleOrNull(), fullTank, notes.ifBlank { null }) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("SAVE") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } })
}

@Composable private fun ScreenHeader(title: String, subtitle: String) { Column { Text(title, color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Secondary) } }
@Composable private fun EmptyState(title: String, subtitle: String, action: String?, onAction: (() -> Unit)? = null) { Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Secondary); if (action != null && onAction != null) Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text(action) } } } }

private fun formatOneDecimal(value: Double): String = String.format(Locale.US, "%,.1f", value)
private fun formatTripDistance(value: Double): String = String.format(Locale.US, "%.3f", value)
private fun formatMileage(value: Double?): String = value?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
private fun formatThreeDecimal(value: Double): String = String.format(Locale.US, "%.3f", value)
private fun formatClock(timeMillis: Long): String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timeMillis))
private fun formatHeaderDate(timeMillis: Long): String = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(timeMillis))
private fun formatDate(millis: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(millis))
private fun formatDuration(start: Long, end: Long): String { val total = ((end-start).coerceAtLeast(0L))/60000L; return String.format(Locale.US, "%02d:%02d", total/60, total%60) }
