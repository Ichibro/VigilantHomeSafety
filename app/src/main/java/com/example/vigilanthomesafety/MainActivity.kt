package com.example.vigilanthomesafety

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
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
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
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainContent(modifier: Modifier = Modifier) {
    var temperature by remember { mutableStateOf("No Data") }
    var humidity by remember { mutableStateOf("No Data") }
    var gasLevel by remember { mutableStateOf("No Data") }
    var waterStatus by remember { mutableStateOf("No Data") }
    var smokeLevel by remember { mutableStateOf("No Data") }
    var motionStatus by remember { mutableStateOf("No Motion") }

    LaunchedEffect(Unit) {
        while (true) {
            fetchSensorData(
                onDataReceived = { data ->
                    temperature = "${String.format(Locale.getDefault(), "%.1f", data.temperature)}°C"
                    humidity = "${String.format(Locale.getDefault(), "%.1f", data.humidity)}%"
                    gasLevel = if (data.coLevel > 50) "High" else "Safe"
                    waterStatus = if (data.waterLevel > 20) "Flood" else "Dry"
                    smokeLevel = if (data.smokeLevel > 10) "High" else "Low"
                    motionStatus = if (data.motion) "MOTION DETECTED!" else "No Motion Detected!"
                },
                onError = {
                    temperature = "Error"
                    humidity = "Error"
                    gasLevel = "Error"
                    waterStatus = "Error"
                    smokeLevel = "Error"
                    motionStatus = "Error"
                }
            )
            delay(1000)
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
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 🎥 Updated Video Player with RTSP stream
        VideoPlayer(streamUrl = "rtsp://100.68.176.2:8554/cam")

        Text(
            text = "Sensor Metrics",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        MetricRow(label = "Temperature:", value = temperature)
        MetricRow(label = "Humidity:", value = humidity)
        MetricRow(
            label = "Gas Levels (CO):",
            value = gasLevel,
            valueColor = if (gasLevel == "High") Color.Red else Color(0xFF4CAF50)
        )
        MetricRow(
            label = "Water Status:",
            value = waterStatus,
            valueColor = if (waterStatus == "Flood") Color.Red else Color(0xFF4CAF50)
        )
        MetricRow(
            label = "Smoke Level:",
            value = smokeLevel,
            valueColor = if (smokeLevel == "High") Color.Red else Color(0xFF4CAF50)
        )
        MetricRow(
            label = "Motion Detection:",
            value = motionStatus,
            valueColor = if (motionStatus == "MOTION DETECTED!") Color.Red else Color(0xFF4CAF50)
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(streamUrl: String) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(RtspMediaSource.Factory()) // Ensure RTSP source factory
            .build()
    }

    DisposableEffect(Unit) {
        Log.d("RTSP_DEBUG", "Setting up ExoPlayer with: $streamUrl")

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMimeType(MimeTypes.VIDEO_H264) // Ensure H.264 compatibility
            .build()

        val mediaSource = RtspMediaSource.Factory()
            .createMediaSource(mediaItem)

        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()

        onDispose {
            Log.d("RTSP_DEBUG", "Releasing ExoPlayer")
            player.release()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.useController = true
                this.player = player
            }
        }
    )
}

@Composable
fun MetricRow(label: String, value: String, valueColor: Color = Color.Black) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color.Black
        )
        Text(
            text = value,
            fontSize = 16.sp,
            color = valueColor
        )
    }
}

fun fetchSensorData(onDataReceived: (SensorData) -> Unit, onError: () -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val data = fetchDataFromServer()
        withContext(Dispatchers.Main) {
            if (data != null) {
                onDataReceived(data)
            } else {
                onError()
            }
        }
    }
}

suspend fun fetchDataFromServer(): SensorData? {
    return withContext(Dispatchers.IO) {
        try {
            val urlString = "https://vigilanths.pagekite.me/data"
            val url = URL(urlString)
            val urlConnection = url.openConnection() as HttpURLConnection
            try {
                urlConnection.requestMethod = "GET"
                val inputStream = urlConnection.inputStream
                val result = inputStream.bufferedReader().use { it.readText() }

                val jsonObject = JSONObject(result)
                val dhtData = jsonObject.optString("dht_data", "")
                val smokeData = jsonObject.optString("smoke_data", "")
                val coDataString = jsonObject.optString("co_data", "0.00 ppm")
                val waterDataString = jsonObject.optString("water_data", "0.00%")
                val motionData = jsonObject.optString("motion_data", "No Motion Detected").trim()

                // Extract temperature and humidity from "77.00 F, 38.00%" format
                val dhtParts = dhtData.split(",") // Split by comma
                val temperatureF = dhtParts.getOrNull(0)?.replace(" F", "")?.trim()?.toDoubleOrNull() ?: 0.0
                val humidity = dhtParts.getOrNull(1)?.replace("%", "")?.trim()?.toDoubleOrNull() ?: 0.0
                val temperatureC = (temperatureF - 32) * 5 / 9  // Convert to Celsius

                val waterLevel = waterDataString.replace("%", "").toDoubleOrNull() ?: 0.0
                val coValue = coDataString.replace(" ppm", "").toDoubleOrNull() ?: 0.0
                val smokeValue = smokeData.replace(" ppm", "").toDoubleOrNull() ?: 0.0

                val motionDetected = motionData.equals("MOTION DETECTED!", ignoreCase = true)

                SensorData(temperatureC, humidity, waterLevel, smokeValue, coValue, motionDetected)
            } finally {
                urlConnection.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}


data class SensorData(
    val temperature: Double,
    val humidity: Double,
    val waterLevel: Double,
    val smokeLevel: Double,
    val coLevel: Double,
    val motion: Boolean
)

@Preview(showBackground = true)
@Composable
fun PreviewUI() {
    MaterialTheme {
        MainContent()
    }
}
