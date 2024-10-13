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
    // Define state variables to hold temperature and humidity data
    var temperature by remember { mutableStateOf("No Data") }
    var humidity by remember { mutableStateOf("No Data") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp), // Add padding around the whole layout
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App title text
        Text(
            text = "Vigilant Home Safety",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6200EA), // Purple color
            modifier = Modifier.padding(bottom = 24.dp), // Space below the title
            textAlign = TextAlign.Center
        )

        // Button to fetch data
        Button(
            onClick = {
                coroutineScope.launch {
                    val data = fetchDataFromServer()
                    data?.let {
                        temperature = "Temperature: ${it.first}°C"
                        humidity = "Humidity: ${it.second}%"
                    }
                }
            },
            shape = RoundedCornerShape(50), // Rounded corners for the button
            modifier = Modifier
                .padding(16.dp)
                .width(200.dp) // Fixed button width for consistency
        ) {
            Text(text = "Fetch Data", fontSize = 18.sp)
        }

        // Display temperature and humidity data
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = temperature,
            fontSize = 22.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp)
        )
        Text(
            text = humidity,
            fontSize = 22.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp)
        )
    }
}

// Function to fetch data from the Flask server
suspend fun fetchDataFromServer(): Pair<Double, Double>? {
    return withContext(Dispatchers.IO) {
        try {
            // Replace with your Flask server's IP address
            val urlString = "http://192.168.1.208:5000/data"
            val url = URL(urlString)
            val urlConnection = url.openConnection() as HttpURLConnection
            try {
                urlConnection.requestMethod = "GET"
                val inputStream = urlConnection.inputStream
                val result = inputStream.bufferedReader().use { it.readText() }

                // Parse the JSON data
                val jsonObject = JSONObject(result)
                val temperature = jsonObject.getDouble("temperature")
                val humidity = jsonObject.getDouble("humidity")

                // Return temperature and humidity as a Pair
                Pair(temperature, humidity)
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
