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

    if (!isPreview) {
        LaunchedEffect(Unit) {
            while (true) {
                val data = fetchDataFromServer()
                if (data != null) {
                    // Convert Fahrenheit to Celsius for UI display
                    val tempCelsius = (data.temperature - 32) * 5 / 9
                    temperature = "${"%.1f".format(tempCelsius)} °C"
                    humidity = "${data.humidity}%"
                    gasLevel = if (data.coLevel > 50) "High" else "Safe"
                    waterStatus = if (data.waterLevel > 20) "Flood" else "Dry"

                    // Check the raw sensor value for smoke, but update the UI with a string
                    val isSmokeHigh = data.smokeLevel > 10
                    smokeLevel = if (isSmokeHigh) "High" else "Low"

                    // Example notification thresholds (using raw Fahrenheit data for temperature)
                    if (data.temperature > 90) {
                        sendNotification(context, "High Temperature Alert!", "Temperature is ${data.temperature} F")
                    }
                    if (data.humidity > 90) {
                        sendNotification(context, "High Humidity Alert!", "Humidity is ${data.humidity}%")
                    }
                    if (data.coLevel > 50) {
                        sendNotification(context, "CO Gas Alert!", "CO Level is HIGH!")
                    }
                    if (data.waterLevel > 20) {
                        sendNotification(context, "Flood Alert!", "Water level is rising!")
                    }
                    if (isSmokeHigh) {
                        sendNotification(context, "Smoke Alert!", "High smoke level detected!")
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
 * This implementation is modeled after your old commit.
 */
suspend fun fetchDataFromServer(): SensorData? {
    return withContext(Dispatchers.IO) {
        val urlString = "https://vigilanths.pagekite.me/data"
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
            val temperatureC = temperatureMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

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

            println("Raw CO Data: $coDataString")   // Debug log
            println("Parsed CO Level: $coValue")      // Debug log

            SensorData(temperatureC, humidity, waterLevel, smokeValue, coValue)
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
    val coLevel: Double
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
        modifier = Modifier.fillMaxWidth().height(200.dp),
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
