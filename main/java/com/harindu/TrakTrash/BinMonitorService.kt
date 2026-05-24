package com.harindu.TrakTrash

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

/**
 * This service can be started/stopped from your main activity as needed.
 */
class BinMonitorService : Service() {

    private lateinit var database: FirebaseDatabase
    private lateinit var notificationHelper: NotificationHelper
    private var binListener: ValueEventListener? = null
    private var binReference: DatabaseReference? = null

    companion object {
        private const val TAG = "BinMonitorService"
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        const val EXTRA_BIN_ID = "BIN_ID"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BinMonitorService created")

        database = Firebase.database
        notificationHelper = NotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_MONITORING -> {
                val binId = intent.getStringExtra(EXTRA_BIN_ID)
                if (binId != null) {
                    startMonitoring(binId)
                } else {
                    Log.e(TAG, "No bin ID provided for monitoring")
                    stopSelf()
                }
            }
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
                stopSelf()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
                stopSelf()
            }
        }

        // Return START_STICKY so the service restarts if killed by the system
        return START_STICKY
    }

    private fun startMonitoring(binId: String) {
        Log.d(TAG, "Starting monitoring for bin: $binId")

        stopMonitoring()

        // Only monitor real bins (Bin 01)
        if (binId == "Bin 01") {
            binReference = database.getReference("gps")
            binListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        handleBinDataUpdate(binId, snapshot)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Firebase listener cancelled", error.toException())
                }
            }

            binReference?.addValueEventListener(binListener!!)
            Log.d(TAG, "Started Firebase listener for $binId")
        } else {
            Log.d(TAG, "Skipping monitoring for mock bin: $binId")
            stopSelf()
        }
    }

    private fun handleBinDataUpdate(binId: String, snapshot: DataSnapshot) {
        val distanceCM = snapshot.child("distanceCM").value?.toString()?.toDoubleOrNull()

        Log.d(TAG, "Received bin data update - distanceCM: $distanceCM")

        // Calculate fill level using the same logic as DashboardActivity
        val maxDistance = 48.0
        val minDistance = 20.0
        val fillLevel = if (distanceCM != null) {
            when {
                distanceCM <= minDistance -> 100
                distanceCM >= maxDistance -> 0
                else -> {
                    val distanceRange = maxDistance - minDistance
                    val currentDistance = distanceCM - minDistance
                    val calculatedLevel = 100 - (currentDistance * 100 / distanceRange)
                    calculatedLevel.toInt().coerceIn(0, 100)
                }
            }
        } else {
            0
        }

        Log.d(TAG, "Calculated fill level: $fillLevel% for bin: $binId")

        // Check for alerts
        notificationHelper.checkAndTriggerAlert(binId, fillLevel)
    }

    private fun stopMonitoring() {
        Log.d(TAG, "Stopping bin monitoring")

        binListener?.let { listener ->
            binReference?.removeEventListener(listener)
            binListener = null
            binReference = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BinMonitorService destroyed")
        stopMonitoring()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

/**
 * Helper functions to start/stop the monitoring service from your activities
 */
object BinMonitorServiceHelper {

    fun startMonitoring(context: android.content.Context, binId: String) {
        val intent = Intent(context, BinMonitorService::class.java).apply {
            action = BinMonitorService.ACTION_START_MONITORING
            putExtra(BinMonitorService.EXTRA_BIN_ID, binId)
        }
        context.startService(intent)
        Log.d("BinMonitorServiceHelper", "Started monitoring service for bin: $binId")
    }

    fun stopMonitoring(context: android.content.Context) {
        val intent = Intent(context, BinMonitorService::class.java).apply {
            action = BinMonitorService.ACTION_STOP_MONITORING
        }
        context.startService(intent)
        Log.d("BinMonitorServiceHelper", "Stopped monitoring service")
    }
}