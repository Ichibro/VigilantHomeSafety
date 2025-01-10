package com.example.vigilanthomesafety

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
                    MainContent(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainContent(modifier: Modifier = Modifier) {
    var temperature by remember { mutableStateOf("No Data") }
    var humidity by remember { mutableStateOf("No Data") }
    var smoke by remember { mutableStateOf("No Data") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Vigilant Home Safety",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6200EA),
            modifier = Modifier.padding(bottom = 24.dp),
            textAlign = TextAlign.Center
        )

        Button(
            onClick = {
                coroutineScope.launch {
                    val data = fetchDataFromServer()
                    data?.let {
                        temperature = "Temperature: ${it.first}°C"
                        humidity = "Humidity: ${it.second}%"
                        smoke = "Smoke: ${it.third} ppm"
                    }
                }
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .padding(16.dp)
                .width(200.dp)
        ) {
            Text(text = "Fetch Data", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(text = temperature, fontSize = 22.sp, color = Color.Black, modifier = Modifier.padding(8.dp))
        Text(text = humidity, fontSize = 22.sp, color = Color.Black, modifier = Modifier.padding(8.dp))
        Text(text = smoke, fontSize = 22.sp, color = Color.Black, modifier = Modifier.padding(8.dp))
    }
}



// Function to fetch data from the Flask server
suspend fun fetchDataFromServer(): Triple<Double, Double, Double>? {
    return withContext(Dispatchers.IO) {
        try {
            val urlString = "https://cced-2601-589-498d-ae30-00-bd64.ngrok-free.app/data" // Use your ngrok link
            val url = URL(urlString)
            val urlConnection = url.openConnection() as HttpURLConnection
            try {
                urlConnection.requestMethod = "GET"
                val inputStream = urlConnection.inputStream
                val result = inputStream.bufferedReader().use { it.readText() }

                // Parse the JSON response
                val jsonObject = JSONObject(result)
                val dhtData = jsonObject.getString("dht_data") // Example: "Temperature: 75.20 F, Humidity: 39.00 %"
                val smokeData = jsonObject.getString("smoke_data") // Example: "Smoke: 0.00 ppm"

                // Extract temperature and humidity from dht_data
                val temperatureRegex = Regex("Temperature: ([0-9.]+) F")
                val humidityRegex = Regex("Humidity: ([0-9.]+) %")
                val temperatureMatch = temperatureRegex.find(dhtData)
                val humidityMatch = humidityRegex.find(dhtData)

                val temperature = temperatureMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val humidity = humidityMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                // Extract smoke value from smoke_data
                val smokeRegex = Regex("Smoke: ([0-9.]+) ppm")
                val smokeMatch = smokeRegex.find(smokeData)
                val smoke = smokeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                // Return the extracted values as a Triple
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
fun GreetingPreview() {
    VigilantHomeSafetyTheme {
        MainContent()
    }
}
