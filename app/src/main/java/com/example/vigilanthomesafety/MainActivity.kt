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
import androidx.compose.ui.tooling.preview.Preview
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
    var waterLevel by remember { mutableStateOf("No Data") }
    var smoke by remember { mutableStateOf("No Data") }
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
            label = "Gas Levels:",
            value = gasLevel,
            valueColor = if (gasLevel == "High") Color.Red else Color(0xFF4CAF50)
        )
        MetricRow(
            label = "Water Level:",
            value = waterLevel,
            valueColor = if (waterLevel == "High") Color.Red else Color(0xFF4CAF50)
        )
        MetricRow(label = "Smoke Detected:", value = smoke)

        // Refresh Data Button
        Button(
            onClick = {
                coroutineScope.launch {
                    val data = fetchDataFromServer()
                    data?.let {
                        temperature = "${String.format(Locale.getDefault(), "%.1f", it.first)}°C"
                        humidity = "${String.format(Locale.getDefault(), "%.1f", it.second)}%"
                       // gasLevel = if (it.second > 30) "High" else "Safe"
                       // waterLevel = "Normal"
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

suspend fun fetchDataFromServer(): Triple<Double, Double, Double>? {
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
                val dhtData = jsonObject.getString("dht_data")
                val smokeData = jsonObject.getString("smoke_data")

                val temperatureRegex = Regex("([0-9.]+) F")
                val temperatureMatch = temperatureRegex.find(dhtData)
                val temperatureF = temperatureMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val temperatureC = (temperatureF - 32) * 5 / 9

                val humidityRegex = Regex("([0-9.]+)%")
                val humidityMatch = humidityRegex.find(dhtData)
                val humidity = humidityMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                val smokeRegex = Regex("([0-9.]+) ppm")
                val smokeMatch = smokeRegex.find(smokeData)
                val smoke = smokeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                Triple(temperatureC, humidity, smoke)
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
fun GreetingPreview() {
    MaterialTheme {
        MainContent()
    }
}
