package com.example.vigilanthomesafety

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigilanthomesafety.ui.theme.VigilantHomeSafetyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VigilantHomeSafetyTheme {
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
    var gasLevel by remember { mutableStateOf("No Data") }
    var waterLevel by remember { mutableStateOf("No Data") }
    var smoke by remember { mutableStateOf("No Data") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
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
        MetricRow(label = "Gas Levels:", value = gasLevel)
        MetricRow(label = "Water Level:", value = waterLevel)
        MetricRow(label = "Smoke Detected:", value = smoke)

        // Refresh Data Button
        Button(
            onClick = {
                coroutineScope.launch {
                    val data = fetchDataFromServer()
                    data?.let {
                        temperature = "${it.first}°C"
                        gasLevel = if (it.second < 50) "Safe" else "High"
                        waterLevel = "Normal" // Replace with actual water level logic
                        smoke = if (it.third < 10) "No" else "Yes"
                    }
                }
            },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Blue // Replace with your desired background color
            ),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = "Refresh Data", fontSize = 18.sp)
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color.Black
        )
        Text(
            text = value,
            fontSize = 16.sp,
            color = Color(0xFF4CAF50) // Green for positive states
        )
    }
}

// Function to fetch data from the server
suspend fun fetchDataFromServer(): Triple<Double, Double, Double>? {
    return withContext(Dispatchers.IO) {
        try {
            val urlString = "https://cced-2601-589-498d-ae30-00-bd64.ngrok-free.app/data" // Replace with your server link
            val url = URL(urlString)
            val urlConnection = url.openConnection() as HttpURLConnection
            try {
                urlConnection.requestMethod = "GET"
                val inputStream = urlConnection.inputStream
                val result = inputStream.bufferedReader().use { it.readText() }

                // Parse JSON response
                val jsonObject = JSONObject(result)
                val dhtData = jsonObject.getString("dht_data")
                val smokeData = jsonObject.getString("smoke_data")

                // Extract temperature and humidity
                val temperatureRegex = Regex("Temperature: ([0-9.]+) F")
                val humidityRegex = Regex("Humidity: ([0-9.]+) %")
                val temperatureMatch = temperatureRegex.find(dhtData)
                val humidityMatch = humidityRegex.find(dhtData)

                val temperature = temperatureMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val humidity = humidityMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                // Extract smoke data
                val smokeRegex = Regex("Smoke: ([0-9.]+) ppm")
                val smokeMatch = smokeRegex.find(smokeData)
                val smoke = smokeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                Triple(temperature, humidity, smoke)
            } finally {
                urlConnection.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewMainContent() {
    VigilantHomeSafetyTheme {
        MainContent()
    }
}
