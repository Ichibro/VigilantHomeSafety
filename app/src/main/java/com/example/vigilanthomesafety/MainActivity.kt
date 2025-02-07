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
    var motionStatus by remember { mutableStateOf("No Motion") }

    if (!isPreview) {
        LaunchedEffect(Unit) {
            while (true) {
                fetchSensorData(
                    onDataReceived = { data ->
                        temperature = "${data.temperature}°C"
                        humidity = "${data.humidity}%"
                        gasLevel = if (data.coLevel > 50) "High" else "Safe"
                        waterStatus = if (data.waterLevel > 20) "Flood" else "Dry"
                        smokeLevel = if (data.smokeLevel > 10) "High" else "Low"
                        motionStatus = if (data.motion) "MOTION DETECTED!" else "No Motion Detected!"

                        if (data.temperature > 50) sendNotification(context, "High Temperature Alert!", "Temperature is ${data.temperature}°C")
                        if (data.humidity > 90) sendNotification(context, "High Humidity Alert!", "Humidity is ${data.humidity}%")
                        if (data.coLevel > 50) sendNotification(context, "CO Gas Alert!", "CO Level is HIGH!")
                        if (data.waterLevel > 20) sendNotification(context, "Flood Alert!", "Water level is rising!")
                        if (data.smokeLevel > 10) sendNotification(context, "Smoke Alert!", "High smoke level detected!")
                    },
                    onError = {}
                )
                delay(1000)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Live Camera Feed", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        if (!isPreview) {
            VideoPlayer(streamUrl = "rtsp://100.68.176.2:8554/cam")
        } else {
            Text(text = "📷 Video Preview Disabled", fontSize = 16.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Sensor Metrics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)

        MetricRow(label = "Temperature:", value = temperature)
        MetricRow(label = "Humidity:", value = humidity)
        MetricRow(label = "Gas Levels (CO):", value = gasLevel)
        MetricRow(label = "Water Status:", value = waterStatus)
        MetricRow(label = "Smoke Level:", value = smokeLevel)
        MetricRow(label = "Motion Detection:", value = motionStatus)
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

fun fetchSensorData(onDataReceived: (SensorData) -> Unit, onError: () -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val data = SensorData(
                temperature = 25.0,
                humidity = 50.0,
                waterLevel = 10.0,
                smokeLevel = 5.0,
                coLevel = 20.0,
                motion = false
            )
            withContext(Dispatchers.Main) {
                onDataReceived(data)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError()
            }
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
