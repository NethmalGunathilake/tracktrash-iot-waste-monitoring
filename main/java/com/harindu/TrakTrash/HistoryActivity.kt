package com.harindu.TrakTrash

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
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
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase

    // To store the bin ID
    private var currentBinId: String? = null

    // UI elements
    private lateinit var avgFillRateTextView: TextView
    private lateinit var avgWeightTextView: TextView
    private lateinit var dataSinceTextView: TextView
    private lateinit var historyStatusMessageTextView: TextView
    private lateinit var historyProgressBar: ProgressBar
    private lateinit var backButton: ImageButton
    private lateinit var statusCard: CardView
    private lateinit var chartContainer: FrameLayout
    private lateinit var binIdSubtitle: TextView

    // Chart
    private lateinit var lineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge display setup
        ViewCompat.getWindowInsetsController(window.decorView)?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }

        setContentView(R.layout.activity_history)

        val mainHistoryLayout = findViewById<View>(R.id.main_history_layout)
        ViewCompat.setOnApplyWindowInsetsListener(mainHistoryLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }

        // Initialize Firebase
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        auth = Firebase.auth
        db = Firebase.database

        // Get the bin ID from intent
        currentBinId = intent.getStringExtra("BIN_ID")
        Log.d(TAG, "Received BIN_ID in HistoryActivity: $currentBinId")

        // Initialize UI elements
        initializeViews()
        setupChart()

        // Set up back button click listener
        backButton.setOnClickListener {
            finish()
        }

        // Update subtitle with bin ID
        val binId = currentBinId ?: "Bin 01"
        binIdSubtitle.text = "$binId Analytics and trends"

        fetchHistoryData()
    }

    private fun initializeViews() {
        avgFillRateTextView = findViewById(R.id.avgFillRateTextView)
        avgWeightTextView = findViewById(R.id.avgWeightTextView)
        dataSinceTextView = findViewById(R.id.dataSinceTextView)
        historyStatusMessageTextView = findViewById(R.id.historyStatusMessageTextView)
        historyProgressBar = findViewById(R.id.historyProgressBar)
        backButton = findViewById(R.id.backButton)
        statusCard = findViewById(R.id.statusCard)
        chartContainer = findViewById(R.id.chartContainer)
        binIdSubtitle = findViewById(R.id.binIdSubtitle)
    }

    private fun setupChart() {
        lineChart = LineChart(this)
        lineChart.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        // Chart styling
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.setDrawGridBackground(false)
        lineChart.legend.isEnabled = false

        // X-axis styling
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawAxisLine(true)
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = Color.parseColor("#E0E0E0")
        xAxis.textColor = Color.parseColor("#757575")
        xAxis.textSize = 10f

        // Y-axis styling
        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawAxisLine(true)
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#E0E0E0")
        leftAxis.textColor = Color.parseColor("#757575")
        leftAxis.textSize = 10f
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 100f

        lineChart.axisRight.isEnabled = false

        chartContainer.addView(lineChart)
    }

    private fun fetchHistoryData() {
        Log.d(TAG, "=== FETCHING HISTORICAL DATA ===")
        showLoadingState("Fetching historical data...")

        val binId = currentBinId ?: "Bin 01"
        Log.d(TAG, "Using Bin ID: $binId")

        val historyPath = "history/$binId"
        Log.d(TAG, "Looking for history at Firebase path: $historyPath")

        val historyRef = db.getReference(historyPath)

        historyRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "=== FIREBASE RESPONSE ===")
                Log.d(TAG, "Snapshot exists: ${snapshot.exists()}")
                Log.d(TAG, "Children count: ${snapshot.childrenCount}")

                if (!snapshot.exists() || snapshot.childrenCount.toInt() == 0) {
                    Log.w(TAG, "❌ No historical data found")
                    showNoDataState("No historical data found for this bin.")
                    return
                }

                val fillLevels = mutableListOf<Double>()
                val weights = mutableListOf<Double>()
                val chartEntries = mutableListOf<Entry>()
                var firstTimestamp: Long? = null
                var processedCount = 0

                // Create a map to store daily data
                val dailyData = mutableMapOf<String, MutableList<Double>>()

                for (childSnapshot in snapshot.children) {
                    val distanceCM = childSnapshot.child("distanceCM").value?.toString()?.toDoubleOrNull()
                    val weight = childSnapshot.child("weightKG").value?.toString()?.toDoubleOrNull()
                    val timestamp = childSnapshot.child("timestamp").value as? Long

                    Log.d(TAG, "Processing entry - Distance: $distanceCM, Weight: $weight, Timestamp: $timestamp")

                    if (distanceCM != null && timestamp != null) {
                        val maxDistance = 48.0
                        val minDistance = 20.0
                        val fillLevel = when {
                            distanceCM <= minDistance -> 100.0
                            distanceCM >= maxDistance -> 0.0
                            else -> {
                                val distanceRange = maxDistance - minDistance
                                100 - ((distanceCM - minDistance) * 100 / distanceRange)
                            }
                        }
                        fillLevels.add(fillLevel)

                        // Group by day for chart
                        val dayKey = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
                        if (!dailyData.containsKey(dayKey)) {
                            dailyData[dayKey] = mutableListOf()
                        }
                        dailyData[dayKey]?.add(fillLevel)

                        Log.d(TAG, "Added fill level: $fillLevel% for day: $dayKey")
                    }

                    if (weight != null) {
                        weights.add(weight)
                        Log.d(TAG, "Added weight: $weight kg")
                    }

                    if (firstTimestamp == null && timestamp != null) {
                        firstTimestamp = timestamp
                        Log.d(TAG, "Set first timestamp: $firstTimestamp")
                    }

                    processedCount++
                }

                // Create chart entries from daily averages
                val sortedDays = dailyData.keys.sorted()
                for ((index, day) in sortedDays.withIndex()) {
                    val dayFillLevels = dailyData[day] ?: continue
                    val avgFillLevel = dayFillLevels.average()
                    chartEntries.add(Entry(index.toFloat(), avgFillLevel.toFloat()))
                }

                Log.d(TAG, "=== PROCESSING RESULTS ===")
                Log.d(TAG, "Processed entries: $processedCount")
                Log.d(TAG, "Fill levels collected: ${fillLevels.size}")
                Log.d(TAG, "Chart entries: ${chartEntries.size}")

                // Update statistics
                if (fillLevels.isNotEmpty()) {
                    val averageFillLevel = fillLevels.average()
                    avgFillRateTextView.text = "%.1f%%".format(averageFillLevel)
                    Log.d(TAG, "Set average fill level: %.1f%%".format(averageFillLevel))
                } else {
                    avgFillRateTextView.text = "N/A"
                    Log.w(TAG, "No fill levels to calculate average")
                }

                if (weights.isNotEmpty()) {
                    val averageWeight = weights.average()
                    avgWeightTextView.text = "%.2f kg".format(averageWeight)
                    Log.d(TAG, "Set average weight: %.2f kg".format(averageWeight))
                } else {
                    avgWeightTextView.text = "N/A"
                    Log.w(TAG, "No weights to calculate average")
                }

                dataSinceTextView.text = firstTimestamp?.let {
                    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
                    Log.d(TAG, "Set date since: $dateStr")
                    dateStr
                } ?: run {
                    Log.w(TAG, "No timestamp found")
                    "N/A"
                }

                // Update chart
                updateChart(chartEntries, sortedDays)

                hideAllStates()
                Log.d(TAG, "Historical data display completed")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase error: ${error.message}", error.toException())
                showErrorState("Error fetching history: ${error.message}")
            }
        })
    }

    private fun updateChart(entries: List<Entry>, dayLabels: List<String>) {
        if (entries.isEmpty()) {
            Log.w(TAG, "No chart data available")
            return
        }

        val dataSet = LineDataSet(entries, "Fill Level")

        // Line styling
        dataSet.color = Color.parseColor("#4CAF50")
        dataSet.setCircleColor(Color.parseColor("#4CAF50"))
        dataSet.lineWidth = 3f
        dataSet.circleRadius = 6f
        dataSet.setDrawCircleHole(true)
        dataSet.circleHoleRadius = 3f
        dataSet.circleHoleColor = Color.WHITE
        dataSet.valueTextSize = 9f
        dataSet.valueTextColor = Color.parseColor("#757575")
        dataSet.setDrawFilled(true)
        dataSet.fillAlpha = 30
        dataSet.fillColor = Color.parseColor("#4CAF50")

        val lineData = LineData(dataSet)
        lineChart.data = lineData

        // Custom X-axis formatter
        lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < dayLabels.size) {
                    dayLabels[index]
                } else {
                    ""
                }
            }
        }

        // Refresh chart
        lineChart.invalidate()
        lineChart.animateX(1000)

        Log.d(TAG, "Chart updated with ${entries.size} data points")
    }

    private fun showLoadingState(message: String) {
        statusCard.visibility = View.VISIBLE
        historyStatusMessageTextView.text = message
        historyProgressBar.visibility = View.VISIBLE
        avgFillRateTextView.text = "Loading..."
        avgWeightTextView.text = "Loading..."
        dataSinceTextView.text = "Loading..."
    }

    private fun showErrorState(message: String) {
        statusCard.visibility = View.VISIBLE
        historyStatusMessageTextView.text = "Error: $message"
        historyStatusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
        historyProgressBar.visibility = View.GONE
        avgFillRateTextView.text = "Error"
        avgWeightTextView.text = "Error"
        dataSinceTextView.text = "Error"
    }

    private fun showNoDataState(message: String) {
        statusCard.visibility = View.VISIBLE
        historyStatusMessageTextView.text = message
        historyStatusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
        historyProgressBar.visibility = View.GONE
        avgFillRateTextView.text = "N/A"
        avgWeightTextView.text = "N/A"
        dataSinceTextView.text = "N/A"
    }

    private fun hideAllStates() {
        statusCard.visibility = View.GONE
        historyStatusMessageTextView.setTextColor(resources.getColor(android.R.color.darker_gray, theme))
    }

    companion object {
        private const val TAG = "HistoryActivity"
    }
}