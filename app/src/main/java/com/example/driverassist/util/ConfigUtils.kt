package com.example.driverassist.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import java.security.MessageDigest

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

/**
 * Utility to print the SHA-1 fingerprint to logcat.
 */
@Suppress("DEPRECATION")
fun printSigningFingerprint(context: Context) {
    try {
        val packageName = context.packageName
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val packageInfo = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            packageInfo.signingInfo?.signingCertificateHistory
        } else {
            val packageInfo = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            packageInfo.signatures
        }

        signatures?.forEach { signature ->
            val md = MessageDigest.getInstance("SHA-1")
            md.update(signature.toByteArray())
            val digest = md.digest()
            val hexString = digest.joinToString(":") { "%02X".format(it) }
            Log.d("SigningFingerprint", "SHA-1: $hexString")
        }
    } catch (e: Exception) {
        Log.e("SigningFingerprint", "Error getting signing fingerprint", e)
    }
}
