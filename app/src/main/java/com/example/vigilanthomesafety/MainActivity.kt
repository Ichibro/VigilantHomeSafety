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
private const val NOTIFICATION_INTERVAL = 60_000L

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

fun sendNotification(context: Context, title: String, message: String, type: String) {
    val sharedPreferences = context.getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)
    val lastNotifiedTime = sharedPreferences.getLong(type, 0L)
    val currentTime = System.currentTimeMillis()

    if (currentTime - lastNotifiedTime >= NOTIFICATION_INTERVAL) {
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

            sharedPreferences.edit().putLong(type, currentTime).apply()
        } else {
            Log.e("Notification", "Permission not granted for notifications.")
        }
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
    var motionStatus by remember { mutableStateOf("No Data") }

    if (!isPreview) {
        LaunchedEffect(Unit) {
            while (true) {
                val data = fetchDataFromServer()
                if (data != null) {
                    // Convert Fahrenheit to Celsius for display
                    val tempCelsius = (data.temperature - 32) * 5 / 9
                    temperature = "${"%.1f".format(tempCelsius)} °C"
                    humidity = "${data.humidity}%"
                    gasLevel = if (data.coLevel > 50) "High" else "Safe"
                    waterStatus = if (data.waterLevel > 20) "Flood" else "Dry"
                    smokeLevel = if (data.smokeLevel > 10) "High" else "Low"
                    motionStatus = if (data.motionDetected) "Detected" else "None"

                    if (data.temperature > 90) {
                        sendNotification(context, "High Temperature Alert!", "Temperature is ${data.temperature} F", "temperature")
                    }
                    if (data.humidity > 90) {
                        sendNotification(context, "High Humidity Alert!", "Humidity is ${data.humidity}%", "humidity")
                    }
                    if (data.coLevel > 50) {
                        sendNotification(context, "CO Gas Alert!", "CO Level is HIGH!", "coLevel")
                    }
                    if (data.smokeLevel > 10) {
                        sendNotification(context, "Smoke Alert!", "High smoke level detected!", "smokeLevel")
                    }
                    // New alert for flood water status
                    if (data.waterLevel > 20) {
                        sendNotification(context, "Flood Alert!", "Water level is high - potential flood detected!", "waterStatus")
                    }
                    if (data.motionDetected) {
                        sendNotification(context, "Motion Alert!", "Motion detected!", "motion")
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

        if (!isPreview) {
            VideoPlayer(streamUrl = "rtsp://20.84.56.25:8554/pi0001")
            Spacer(modifier = Modifier.height(10.dp))
            VideoPlayer(streamUrl = "rtsp://20.84.56.25:8554/pi0002")
        } else {
            Text(text = "📷 Video Preview Disabled", fontSize = 16.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Sensor Metrics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)

        MetricRow("Temperature:", temperature)
        MetricRow("Humidity:", humidity)
        MetricRow("Gas Levels (CO):", gasLevel)
        MetricRow("Water Status:", waterStatus)
        MetricRow("Smoke Level:", smokeLevel)
        MetricRow("Motion:", motionStatus)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(streamUrl: String) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMimeType(MimeTypes.VIDEO_H264)
            .build()
        val mediaSource = RtspMediaSource.Factory().createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()

        onDispose { player.release() }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        factory = { PlayerView(it).apply { this.player = player } }
    )
}

/**
 * A suspend function that fetches sensor data from the server.
 * It now uses the original JSON parsing logic, which extracts values from
 * string data with units (e.g., "78.80 F, 37.00%") using regular expressions.
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

            SensorData(temperatureF, humidity, coValue, smokeValue, motionDetected, waterLevel)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            urlConnection.disconnect()
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 16.sp, color = Color.Black)
        Text(text = value, fontSize = 16.sp, color = Color.Black)
    }
}

data class SensorData(
    val temperature: Double,
    val humidity: Double,
    val coLevel: Double,
    val smokeLevel: Double,
    val motionDetected: Boolean,
    val waterLevel: Double
)

@Preview(showBackground = true)
@Composable
fun PreviewUI() {
    MaterialTheme {
        MainContent(isPreview = true)
    }
}
