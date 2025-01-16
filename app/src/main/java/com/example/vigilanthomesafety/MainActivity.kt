package com.example.vigilanthomesafety
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview

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
    var gasLevel by remember { mutableStateOf("No Data") } // Represents CO sensor data
    var waterStatus by remember { mutableStateOf("No Data") }
    var smokeLevel by remember { mutableStateOf("No Data") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Live Camera Feed Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Black,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "Camera Feed",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        // Sensor Metrics Section
        Text(
            text = "Sensor Metrics",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Metrics Rows
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

        // Refresh Data Button
        Button(
            onClick = {
                coroutineScope.launch {
                    val data = fetchDataFromServer()
                    if (data != null) {
                        temperature = "${String.format(Locale.getDefault(), "%.1f", data.temperature)}°C"
                        humidity = "${String.format(Locale.getDefault(), "%.1f", data.humidity)}%"
                        gasLevel = if (data.coLevel > 50) "High" else "Safe" // Threshold for CO
                        waterStatus = if (data.waterLevel > 20) "Flood" else "Dry"
                        smokeLevel = if (data.smokeLevel > 10) "High" else "Low"
                    } else {
                        temperature = "Error"
                        humidity = "Error"
                        gasLevel = "Error"
                        waterStatus = "Error"
                        smokeLevel = "Error"
                    }
                }
            },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Blue
            ),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = "Refresh Data", fontSize = 18.sp)
        }
    }
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
                val coDataString = jsonObject.optString("co_data", "0.00 ppm") // CO data
                val waterDataString = jsonObject.optString("water_data", "0.00%")
                val waterLevel = waterDataString.replace("%", "").toDoubleOrNull() ?: 0.0

                // Extract CO level from `co_data`
                val coRegex = Regex("([0-9.]+) ppm")
                val coMatch = coRegex.find(coDataString)
                val coValue = coMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                println("Raw CO Data: $coDataString") // Debug log
                println("Parsed CO Level: $coValue")  // Debug log

                val smokeRegex = Regex("([0-9.]+) ppm")
                val smokeMatch = smokeRegex.find(smokeData)
                val smokeValue = smokeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                val temperatureRegex = Regex("([0-9.]+) F")
                val temperatureMatch = temperatureRegex.find(dhtData)
                val temperatureF = temperatureMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val temperatureC = (temperatureF - 32) * 5 / 9

                val humidityRegex = Regex("([0-9.]+)%")
                val humidityMatch = humidityRegex.find(dhtData)
                val humidity = humidityMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                SensorData(temperatureC, humidity, waterLevel, smokeValue, coValue)
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
    val coLevel: Double
)


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MaterialTheme {
        MainContent()
    }
}
