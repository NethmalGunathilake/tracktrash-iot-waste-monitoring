package com.harindu.TrakTrash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var notificationHelper: NotificationHelper //Notification helper

    private var currentBinId: String? = null
    private var lastFillLevel: Int = -1 // Track last fill level to avoid duplicate alerts


    private lateinit var backButton: ImageButton
    private lateinit var userIdTextView: TextView
    private lateinit var statusCard: CardView
    private lateinit var statusMessageTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var binVisualizationCard: CardView
    private lateinit var dataCardsContainer: View
    private lateinit var fillLevelCard: CardView
    private lateinit var fillLevelTextView: TextView
    private lateinit var weightCard: CardView
    private lateinit var weightTextView: TextView
    private lateinit var lastUpdatedCard: CardView
    private lateinit var lastUpdatedTextView: TextView
    private lateinit var logoutButton: Button
    private lateinit var viewHistoryButton: Button
    private lateinit var checkLocationButton: Button
    private lateinit var testNotificationButton: Button
    private lateinit var binFillView: BinFillView

    companion object {
        private const val TAG = "DashboardActivity"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewCompat.getWindowInsetsController(window.decorView)?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }

        setContentView(R.layout.activity_dashboard)

        val mainDashboardLayout = findViewById<View>(R.id.main_dashboard_layout)
        ViewCompat.setOnApplyWindowInsetsListener(mainDashboardLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left, top = systemBars.top, right = systemBars.right, bottom = systemBars.bottom
            )
            insets
        }

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        auth = Firebase.auth
        db = Firebase.database

        // Initialize notification helper
        notificationHelper = NotificationHelper(this)

        // Get the bin ID passed from the BinListActivity
        currentBinId = intent.getStringExtra("BIN_ID")
        Log.d(TAG, "Received BIN_ID: $currentBinId")

        // Initialize UI elements
        initializeUI()
        setupClickListeners()

        // Request notification permission
        requestNotificationPermission()

        setupFirebaseAuthAndFetchData()
    }


    private fun initializeUI() {
        backButton = findViewById(R.id.backButton)
        userIdTextView = findViewById(R.id.userIdTextView)
        statusCard = findViewById(R.id.statusCard)
        statusMessageTextView = findViewById(R.id.statusMessageTextView)
        progressBar = findViewById(R.id.progressBar)
        binVisualizationCard = findViewById(R.id.binVisualizationCard)
        dataCardsContainer = findViewById(R.id.dataCardsContainer)
        fillLevelCard = findViewById(R.id.fillLevelCard)
        fillLevelTextView = findViewById(R.id.fillLevelTextView)
        weightCard = findViewById(R.id.weightCard)
        weightTextView = findViewById(R.id.weightTextView)
        lastUpdatedCard = findViewById(R.id.lastUpdatedCard)
        lastUpdatedTextView = findViewById(R.id.lastUpdatedTextView)
        logoutButton = findViewById(R.id.logoutButton)
        viewHistoryButton = findViewById(R.id.viewHistoryButton)
        checkLocationButton = findViewById(R.id.checkLocationButton)
        binFillView = findViewById(R.id.binFillView)

        // Test notification button ( add this to layout if needed)
        // testNotificationButton = findViewById(R.id.testNotificationButton)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            val intent = Intent(this, BinListActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        logoutButton.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        viewHistoryButton.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            intent.putExtra("BIN_ID", currentBinId)
            Log.d(TAG, "Launching HistoryActivity with BIN_ID: $currentBinId")
            startActivity(intent)
        }

        checkLocationButton.setOnClickListener {
            val intent = Intent(this, LocationActivity::class.java)
            startActivity(intent)
        }

    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Notification permission granted")
                } else {
                    Log.w(TAG, "Notification permission denied")
                }
            }
        }
    }

    private fun setupFirebaseAuthAndFetchData() {
        showLoadingState("Authenticating...")
        val currentUser = auth.currentUser
        if (currentUser != null) {
            userIdTextView.text = "Smart Bin User"
            currentBinId?.let { fetchBinData(it) }
        } else {
            auth.signInAnonymously().addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.uid?.let { uid ->
                        userIdTextView.text = "Guest User"
                        currentBinId?.let { fetchBinData(it) }
                    }
                }
            }
        }
    }

    private fun fetchBinData(binId: String) {
        showLoadingState("Loading bin data...")

        if (binId == "Bin 01") { // Check if it's the real bin
            val binRef = db.getReference("gps")
            binRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        updateUIWithBinData(snapshot)
                    } else {
                        showNoDataState("No data found for '$binId' node.")
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Failed to read value.", error.toException())
                    showErrorState("Failed to fetch bin data: ${error.message}")
                }
            })
        } else {
            showNoDataState("This is a mock bin. No live data available.")
        }
    }

    private fun updateUIWithBinData(snapshot: DataSnapshot) {
        hideAllStates()

        binVisualizationCard.visibility = View.VISIBLE
        dataCardsContainer.visibility = View.VISIBLE

        val distanceCM = snapshot.child("distanceCM").value?.toString()?.toDoubleOrNull()
        val weight = snapshot.child("weightKG").value?.toString()?.toDoubleOrNull()

        Log.d(TAG, "Parsing Data - distanceCM: $distanceCM, weight: $weight")

        saveHistoricalData(distanceCM, weight)

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
            null
        }

        val lastUpdatedDate = Date()

        fillLevelTextView.text = fillLevel?.let { "$it%" } ?: "N/A"
        fillLevel?.let {
            binFillView.setFillLevel(it)

            //  Check for alerts when fill level changes
            if (it != lastFillLevel) {
                currentBinId?.let { binId ->
                    Log.d(TAG, "Fill level changed from $lastFillLevel% to $it% for $binId")
                    notificationHelper.checkAndTriggerAlert(binId, it)
                }
                lastFillLevel = it
            }
        } ?: binFillView.setFillLevel(0)

        weightTextView.text = weight?.let { "%.2f kg".format(it) } ?: "N/A"
        lastUpdatedTextView.text = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(lastUpdatedDate)

        // Update status card with alert information
        fillLevel?.let { updateStatusCardWithAlert(it) }
    }

    /**
     * Update status card to show current alert level
     */
    private fun updateStatusCardWithAlert(fillLevel: Int) {
        val alertMessage = when {
            fillLevel >= 95 -> {
                statusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                "🔴 CRITICAL: Bin needs immediate collection!"
            }
            fillLevel >= 90 -> {
                statusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                "🚨 URGENT: Bin is nearly full!"
            }
            fillLevel >= 85 -> {
                statusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
                "⚠️ HIGH: Collection needed soon"
            }
            fillLevel >= 75 -> {
                statusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
                "📊 MEDIUM: Plan collection"
            }
            fillLevel >= 50 -> {
                statusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_blue_dark, theme))
                "ℹ️ MONITOR: Fill level increasing"
            }
            else -> {
                statusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
                "✅ NORMAL: Bin status good"
            }
        }

        if (fillLevel >= 50) {
            statusMessageTextView.text = "$alertMessage ($fillLevel% full)"
            statusCard.visibility = View.VISIBLE
        } else {
            statusCard.visibility = View.GONE
        }
    }

    private fun saveHistoricalData(distanceCM: Double?, weight: Double?) {
        Log.d(TAG, "=== SAVING HISTORICAL DATA ===")
        Log.d(TAG, "Current Bin ID: $currentBinId")
        Log.d(TAG, "Distance CM: $distanceCM")
        Log.d(TAG, "Weight KG: $weight")

        val timestamp = System.currentTimeMillis()
        val historyData = mapOf(
            "distanceCM" to distanceCM,
            "weightKG" to weight,
            "timestamp" to timestamp
        )

        Log.d(TAG, "Timestamp: $timestamp")
        Log.d(TAG, "History data to save: $historyData")

        currentBinId?.let { binId ->
            val path = "history/$binId/$timestamp"
            Log.d(TAG, "Saving to Firebase path: $path")

            db.getReference(path).setValue(historyData)
                .addOnSuccessListener {
                    Log.d(TAG, "Historical data saved successfully to $path")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to save historical data to $path", exception)
                }
        } ?: Log.e(TAG, "Current Bin ID is null, cannot save historical data")
    }

    private fun showLoadingState(message: String) {
        statusMessageTextView.text = message
        statusCard.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        binVisualizationCard.visibility = View.GONE
        dataCardsContainer.visibility = View.GONE
        binFillView.setFillLevel(0)
    }

    private fun showErrorState(message: String) {
        statusMessageTextView.text = "Error: $message"
        statusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
        statusCard.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        binVisualizationCard.visibility = View.GONE
        dataCardsContainer.visibility = View.GONE
        binFillView.setFillLevel(0)
    }

    private fun showNoDataState(message: String) {
        statusMessageTextView.text = message
        statusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
        statusCard.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        binVisualizationCard.visibility = View.GONE
        dataCardsContainer.visibility = View.GONE
        binFillView.setFillLevel(0)
    }

    private fun hideAllStates() {
        progressBar.visibility = View.GONE
        statusMessageTextView.setTextColor(resources.getColor(android.R.color.darker_gray, theme))
    }

    override fun onBackPressed() {
        val intent = Intent(this, BinListActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
        super.onBackPressed()
    }
}