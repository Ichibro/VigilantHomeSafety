package com.example.vigilanthomesafety

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val CHANNEL_ID = "ALERT_CHANNEL"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel(this)

        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

fun createNotificationChannel(context: Context) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val name = "Vigilant Home Safety Alerts"
        val descriptionText = "Notifications for critical sensor alerts"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun sendNotification(context: Context, title: String, message: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    } else {
        Log.e("Notification", "Permission not granted for notifications.")
    }
}

@Composable
fun MainContent(modifier: Modifier = Modifier, isPreview: Boolean = false) {
    val context = LocalContext.current

    var temperature by remember { mutableStateOf("No Data") }
    var humidity by remember { mutableStateOf("No Data") }
    var gasLevel by remember { mutableStateOf("No Data") }
    var waterStatus by remember { mutableStateOf("No Data") }
    var smokeLevel by remember { mutableStateOf("No Data") }
    var motionStatus by remember { mutableStateOf("No Data") } // Optional: display motion status

    if (!isPreview) {
        LaunchedEffect(Unit) {
            // Notification interval of 2 minutes in milliseconds
            val notificationInterval = 120_000L

            // Initialize last notification times for each metric
            var lastTempNotified = 0L
            var lastHumidityNotified = 0L
            var lastCONotified = 0L
            var lastSmokeNotified = 0L
            var lastMotionNotified = 0L

            while (true) {
                val data = fetchDataFromServer()
                if (data != null) {
                    // Convert Fahrenheit to Celsius for UI display
                    val tempCelsius = (data.temperature - 32) * 5 / 9
                    temperature = "${"%.1f".format(tempCelsius)} °C"
                    humidity = "${data.humidity}%"
                    gasLevel = if (data.coLevel > 50) "High" else "Safe"
                    waterStatus = if (data.waterLevel > 20) "Flood" else "Dry"

                    // Update smoke and motion status strings
                    val isSmokeHigh = data.smokeLevel > 10
                    smokeLevel = if (isSmokeHigh) "High" else "Low"
                    motionStatus = if (data.motionDetected) "Detected" else "None"

                    // Get current time
                    val currentTime = System.currentTimeMillis()

                    // Send notifications if conditions are met and enough time has elapsed
                    if (data.temperature > 90 && (currentTime - lastTempNotified >= notificationInterval)) {
                        sendNotification(context, "High Temperature Alert!", "Temperature is ${data.temperature} F")
                        lastTempNotified = currentTime
                    }
                    if (data.humidity > 90 && (currentTime - lastHumidityNotified >= notificationInterval)) {
                        sendNotification(context, "High Humidity Alert!", "Humidity is ${data.humidity}%")
                        lastHumidityNotified = currentTime
                    }
                    if (data.coLevel > 50 && (currentTime - lastCONotified >= notificationInterval)) {
                        sendNotification(context, "CO Gas Alert!", "CO Level is HIGH!")
                        lastCONotified = currentTime
                    }
                    if (isSmokeHigh && (currentTime - lastSmokeNotified >= notificationInterval)) {
                        sendNotification(context, "Smoke Alert!", "High smoke level detected!")
                        lastSmokeNotified = currentTime
                    }
                    if (data.motionDetected && (currentTime - lastMotionNotified >= notificationInterval)) {
                        sendNotification(context, "Motion Alert!", "Motion detected!")
                        lastMotionNotified = currentTime
                    }
                } else {
                    Log.e("MainContent", "Failed to fetch sensor data")
                }
                delay(1000)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Live Camera Feed",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        if (!isPreview) {
            VideoPlayer(streamUrl = "rtsp://100.68.176.2:8554/cam")
        } else {
            Text(text = "📷 Video Preview Disabled", fontSize = 16.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sensor Metrics",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        MetricRow(label = "Temperature:", value = temperature)
        MetricRow(label = "Humidity:", value = humidity)
        MetricRow(label = "Gas Levels (CO):", value = gasLevel)
        MetricRow(label = "Water Status:", value = waterStatus)
        MetricRow(label = "Smoke Level:", value = smokeLevel)
        MetricRow(label = "Motion:", value = motionStatus) // Optional UI display for motion
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 16.sp, color = Color.Black)
        Text(text = value, fontSize = 16.sp, color = Color.Black)
    }
}

/**
 * A suspend function that fetches sensor data from the server.
 */
suspend fun fetchDataFromServer(): SensorData? {
    return withContext(Dispatchers.IO) {
        val urlString = "https://vhsm.pagekite.me/data"
        val urlConnection = URL(urlString).openConnection() as HttpURLConnection
        try {
            urlConnection.requestMethod = "GET"
            urlConnection.connectTimeout = 5000
            urlConnection.readTimeout = 5000
            urlConnection.connect()

            val result = urlConnection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(result)

            // Parse dht_data (expected format: "78.80 F, 37.00%")
            val dhtData = jsonObject.optString("dht_data", "")
            val temperatureRegex = Regex("([0-9.]+) ?F")
            val temperatureMatch = temperatureRegex.find(dhtData)
            val temperatureF = temperatureMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

            val humidityRegex = Regex("([0-9.]+)%")
            val humidityMatch = humidityRegex.find(dhtData)
            val humidity = humidityMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

            // Parse water_data (expected format: "94.66%")
            val waterDataString = jsonObject.optString("water_data", "0.00%")
            val waterLevel = waterDataString.replace("%", "").trim().toDoubleOrNull() ?: 0.0

            // Parse smoke_data (expected format: "0.00 ppm")
            val smokeData = jsonObject.optString("smoke_data", "0.00 ppm")
            val smokeRegex = Regex("([0-9.]+) ppm")
            val smokeMatch = smokeRegex.find(smokeData)
            val smokeValue = smokeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

            // Parse co_data (expected format: "0.00 ppm")
            val coDataString = jsonObject.optString("co_data", "0.00 ppm")
            val coRegex = Regex("([0-9.]+) ppm")
            val coMatch = coRegex.find(coDataString)
            val coValue = coMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

            // Parse motion_data (expected to be "MOTION DETECTED!" when motion is detected)
            val motionData = jsonObject.optString("motion_data", "")
            val motionDetected = motionData.trim().equals("MOTION DETECTED!", ignoreCase = true)

            SensorData(temperatureF, humidity, waterLevel, smokeValue, coValue, motionDetected)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            urlConnection.disconnect()
        }
    }
}

data class SensorData(
    val temperature: Double,
    val humidity: Double,
    val waterLevel: Double,
    val smokeLevel: Double,
    val coLevel: Double,
    val motionDetected: Boolean
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(streamUrl: String) {
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMimeType(MimeTypes.VIDEO_H264)
            .build()

        val mediaSource = RtspMediaSource.Factory().createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()

        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        factory = { ctx -> PlayerView(ctx).apply { this.player = player } }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewUI() {
    MaterialTheme {
        MainContent(isPreview = true)
    }
}
