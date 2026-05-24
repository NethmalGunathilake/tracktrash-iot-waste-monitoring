package com.harindu.TrakTrash

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class NotificationHelper(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "NotificationHelper"
        private const val CHANNEL_ID = "smart_bin_alerts"
        private const val CHANNEL_NAME = "Smart Bin Alerts"
        private const val CHANNEL_DESCRIPTION = "Notifications for bin fill level alerts"
        private const val PREFS_NAME = "smart_bin_prefs"
        private const val KEY_LAST_ALERT_LEVEL = "last_alert_level_"
        private const val KEY_LAST_ALERT_TIME = "last_alert_time_"

        // Notification IDs
        private const val NOTIFICATION_ID_BASE = 1000

        // Alert thresholds
        private const val ALERT_50_PERCENT = 50
        private const val ALERT_75_PERCENT = 75
        private const val ALERT_85_PERCENT = 85
        private const val ALERT_90_PERCENT = 90
        private const val ALERT_95_PERCENT = 95

        // Minimum time between same-level alerts (in milliseconds)
        private const val MIN_ALERT_INTERVAL = 30 * 60 * 1000L // 30 minutes
    }

    init {
        createNotificationChannel()
    }

    /**
     * Creates the notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = ContextCompat.getColor(context, android.R.color.holo_red_light)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Main method to check fill level and trigger appropriate notifications
     */
    fun checkAndTriggerAlert(binId: String, fillLevel: Int) {
        Log.d(TAG, "Checking alert for bin: $binId, fill level: $fillLevel%")

        if (fillLevel < ALERT_50_PERCENT) {
            // Clear any previous alert level tracking when below threshold
            clearLastAlertLevel(binId)
            return
        }

        val currentTime = System.currentTimeMillis()
        val lastAlertLevel = getLastAlertLevel(binId)
        val lastAlertTime = getLastAlertTime(binId)

        // Determine which alert level we should trigger
        val alertLevel = when {
            fillLevel >= ALERT_95_PERCENT -> ALERT_95_PERCENT
            fillLevel >= ALERT_90_PERCENT -> ALERT_90_PERCENT
            fillLevel >= ALERT_85_PERCENT -> ALERT_85_PERCENT
            fillLevel >= ALERT_75_PERCENT -> ALERT_75_PERCENT
            fillLevel >= ALERT_50_PERCENT -> ALERT_50_PERCENT
            else -> return
        }

        // Check if we should send this alert
        val shouldSendAlert = when {
            // First time alert for this bin
            lastAlertLevel == -1 -> true

            // Fill level increased to next threshold
            alertLevel > lastAlertLevel -> true

            // Same level but enough time has passed (for persistent high levels)
            alertLevel == lastAlertLevel &&
                    alertLevel >= ALERT_90_PERCENT &&
                    (currentTime - lastAlertTime) >= MIN_ALERT_INTERVAL -> true

            else -> false
        }

        if (shouldSendAlert) {
            triggerNotification(binId, fillLevel, alertLevel)
            saveLastAlert(binId, alertLevel, currentTime)
        } else {
            Log.d(TAG, "Alert suppressed - Level: $alertLevel, Last: $lastAlertLevel, Time diff: ${currentTime - lastAlertTime}ms")
        }
    }

    /**
     * Triggers the actual notification based on fill level
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun triggerNotification(binId: String, fillLevel: Int, alertLevel: Int) {
        val (title, message, priority, urgent) = getAlertContent(binId, fillLevel, alertLevel)

        Log.d(TAG, "Triggering notification - Title: $title, Message: $message")

        // Create intent to open the app when notification is tapped
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("BIN_ID", binId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            binId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        // Add different styling based on urgency
        when {
            urgent -> {
                notificationBuilder
                    .setColor(ContextCompat.getColor(context, R.color.holo_red_dark))
                    .setLights(ContextCompat.getColor(context, R.color.holo_red_light), 1000, 1000)
                    .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            }
            fillLevel >= ALERT_75_PERCENT -> {
                notificationBuilder
                    .setColor(ContextCompat.getColor(context, R.color.holo_orange_dark))
                    .setVibrate(longArrayOf(0, 300, 200, 300))
            }
            else -> {
                notificationBuilder
                    .setColor(ContextCompat.getColor(context, R.color.holo_blue_dark))
                    .setVibrate(longArrayOf(0, 250))
            }
        }

        // Show notification
        val notificationId = NOTIFICATION_ID_BASE + binId.hashCode()
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "Notification sent with ID: $notificationId")
    }

    /**
     * Gets the appropriate alert content based on fill level
     */
    private fun getAlertContent(binId: String, fillLevel: Int, alertLevel: Int): AlertContent {
        return when {
            fillLevel >= ALERT_95_PERCENT -> AlertContent(
                title = "🔴 URGENT: $binId Critical!",
                message = "$binId is $fillLevel% full and critically needs immediate collection! Please dispatch collection team urgently.",
                priority = NotificationCompat.PRIORITY_MAX,
                urgent = true
            )

            fillLevel >= ALERT_90_PERCENT -> AlertContent(
                title = "🚨 $binId Nearly Full!",
                message = "$binId is $fillLevel% full. Send someone to collect the bin immediately to prevent overflow.",
                priority = NotificationCompat.PRIORITY_HIGH,
                urgent = true
            )

            fillLevel >= ALERT_85_PERCENT -> AlertContent(
                title = "⚠️ $binId High Fill Level",
                message = "$binId is $fillLevel% full. Collection should be scheduled soon to prevent issues.",
                priority = NotificationCompat.PRIORITY_HIGH,
                urgent = false
            )

            fillLevel >= ALERT_75_PERCENT -> AlertContent(
                title = "📊 $binId Alert",
                message = "$binId is $fillLevel% full. Please plan for collection within the next few hours.",
                priority = NotificationCompat.PRIORITY_DEFAULT,
                urgent = false
            )

            fillLevel >= ALERT_50_PERCENT -> AlertContent(
                title = "ℹ️ $binId Status Update",
                message = "$binId is $fillLevel% full. Monitor for collection planning.",
                priority = NotificationCompat.PRIORITY_DEFAULT,
                urgent = false
            )

            else -> AlertContent(
                title = "$binId Status",
                message = "$binId is $fillLevel% full.",
                priority = NotificationCompat.PRIORITY_DEFAULT,
                urgent = false
            )
        }
    }

    /**
     * Saves the last alert information to shared preferences
     */
    private fun saveLastAlert(binId: String, alertLevel: Int, time: Long) {
        sharedPreferences.edit()
            .putInt(KEY_LAST_ALERT_LEVEL + binId, alertLevel)
            .putLong(KEY_LAST_ALERT_TIME + binId, time)
            .apply()
    }

    /**
     * Gets the last alert level for a specific bin
     */
    private fun getLastAlertLevel(binId: String): Int {
        return sharedPreferences.getInt(KEY_LAST_ALERT_LEVEL + binId, -1)
    }

    /**
     * Gets the last alert time for a specific bin
     */
    private fun getLastAlertTime(binId: String): Long {
        return sharedPreferences.getLong(KEY_LAST_ALERT_TIME + binId, 0L)
    }

    /**
     * Clears the last alert level (when bin goes below threshold)
     */
    private fun clearLastAlertLevel(binId: String) {
        sharedPreferences.edit()
            .remove(KEY_LAST_ALERT_LEVEL + binId)
            .remove(KEY_LAST_ALERT_TIME + binId)
            .apply()
    }

    /**
     * Data class to hold alert content
     */
    private data class AlertContent(
        val title: String,
        val message: String,
        val priority: Int,
        val urgent: Boolean
    )

    /**
     * Method to test notifications
     */
    fun testNotification(binId: String) {
        triggerNotification(binId, 92, ALERT_90_PERCENT)
    }
}