package com.example.driverassist

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.driverassist.ui.map.MapScreen
import com.example.driverassist.ui.theme.DriverAssistTheme
import com.example.driverassist.util.printSigningFingerprint
import com.example.driverassist.util.resolveMapsApiKey
import com.google.android.gms.maps.MapsInitializer
import com.google.android.libraries.places.api.Places
import kotlinx.coroutines.launch

// Entry point Activity for DriverAssist.
class MainPage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Log the SHA-1 fingerprint to verify against Google Cloud Console restrictions
        printSigningFingerprint(this)

        // Initializes the Places SDK.
        val apiKey = resolveMapsApiKey(this)
        if (!apiKey.isNullOrBlank()) {
            try {
                if (!Places.isInitialized()) {
                    Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
                    Log.i("MainPage", "Places SDK initialized successfully.")
                }
            } catch (e: Exception) {
                Log.e("MainPage", "Places SDK initialization failed: ${e.message}", e)
                // We don't crash the app here, but some features may be unavailable
            }
        } else {
            Log.e("MainPage", "Maps API key missing in manifest. Places SDK NOT initialized.")
        }

        // Initialize Maps renderer to the latest version
        MapsInitializer.initialize(
            applicationContext,
            MapsInitializer.Renderer.LATEST
        ) { renderer ->
            when (renderer) {
                MapsInitializer.Renderer.LATEST -> Log.i("MainPage", "The latest version of the renderer is used.")
                MapsInitializer.Renderer.LEGACY -> Log.i("MainPage", "The legacy version of the renderer is used.")
            }
        }

        setContent {
            DriverAssistTheme {
                MapScreen()
            }
        }
    }
}
