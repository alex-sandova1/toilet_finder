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
        if (!Places.isInitialized() && !apiKey.isNullOrBlank()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        } else if (apiKey.isNullOrBlank()) {
            Log.e("MainPage", "Maps API key missing in manifest.")
        }

        // Initialize Maps renderer asynchronously (non-blocking) to avoid Places API interference
        lifecycleScope.launch {
            runCatching {
                MapsInitializer.initialize(
                    applicationContext,
                    MapsInitializer.Renderer.LEGACY
                ) { renderer ->
                    Log.i("MainPage", "Maps renderer initialized: $renderer")
                }
            }.onFailure {
                Log.w("MainPage", "Maps renderer init failed: ${it.message}")
            }
        }

        setContent {
            DriverAssistTheme {
                MapScreen()
            }
        }
    }
}
