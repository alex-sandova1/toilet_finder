package com.example.driverassist

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.driverassist.ui.map.MapScreen
import com.example.driverassist.ui.theme.DriverAssistTheme
import com.example.driverassist.util.resolveMapsApiKey
import com.google.android.libraries.places.api.Places

// Entry point Activity for DriverAssist.
class MainPage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initializes the Places SDK.
        val apiKey = resolveMapsApiKey(this)
        if (!Places.isInitialized() && !apiKey.isNullOrBlank()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        } else if (apiKey.isNullOrBlank()) {
            Log.e("MainPage", "Maps API key missing in manifest.")
        }

        setContent {
            DriverAssistTheme {
                MapScreen()
            }
        }
    }
}
