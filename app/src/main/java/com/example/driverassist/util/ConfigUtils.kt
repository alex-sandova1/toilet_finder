package com.example.driverassist.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

// Retrieves the API key from Manifest.
@Suppress("DEPRECATION")
fun resolveMapsApiKey(context: Context): String? {
    val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
    } else {
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    }
    return applicationInfo.metaData?.getString("com.google.android.geo.API_KEY")?.takeIf { it.isNotBlank() }
}
