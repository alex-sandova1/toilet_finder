package com.example.driverassist.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.driverassist.R

object NotificationHelper {
    private const val CHANNEL_ID = "restroom_alerts"
    private const val CHANNEL_NAME = "Restroom Proximity Alerts"
    private const val NOTIFICATION_ID = 1001

    fun showProximityAlert(context: Context, timeAway: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when you are far from a restroom"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Generic icon for now
            .setContentTitle("Restroom Warning")
            .setContentText("No restrooms within 5 minutes. The nearest one is $timeAway away.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
