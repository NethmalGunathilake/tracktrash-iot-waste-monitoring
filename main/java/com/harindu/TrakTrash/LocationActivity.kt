package com.harindu.TrakTrash

import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class LocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var db: FirebaseDatabase
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var locationStatusMessageTextView: TextView
    private lateinit var locationProgressBar: ProgressBar
    private lateinit var backButton: ImageButton
    private lateinit var directionsButton: Button
    private lateinit var refreshLocationButton: Button

    // Info card elements
    private lateinit var distanceLayout: LinearLayout
    private lateinit var coordinatesLayout: LinearLayout
    private lateinit var distanceText: TextView
    private lateinit var coordinatesText: TextView

    private var binLatitude: Double? = null
    private var binLongitude: Double? = null
    private var userLocation: Location? = null

    companion object {
        private const val TAG = "LocationActivity"
        private const val DEFAULT_ZOOM = 15f
        private val DEFAULT_LOCATION = LatLng(6.9271, 79.8612)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewCompat.getWindowInsetsController(window.decorView)?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
        setContentView(R.layout.activity_location)
        val mainLocationLayout = findViewById<View>(R.id.main_location_layout)
        ViewCompat.setOnApplyWindowInsetsListener(mainLocationLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = systemBars.left, top = systemBars.top, right = systemBars.right, bottom = systemBars.bottom)
            insets
        }
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        initializeViews()
        setupClickListeners()

        db = Firebase.database
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun initializeViews() {
        locationStatusMessageTextView = findViewById(R.id.locationStatusMessageTextView)
        locationProgressBar = findViewById(R.id.locationProgressBar)
        backButton = findViewById(R.id.backButton)
        directionsButton = findViewById(R.id.directionsButton)
        refreshLocationButton = findViewById(R.id.refreshLocationButton)

        // Info card elements
        distanceLayout = findViewById(R.id.distanceLayout)
        coordinatesLayout = findViewById(R.id.coordinatesLayout)
        distanceText = findViewById(R.id.distanceText)
        coordinatesText = findViewById(R.id.coordinatesText)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
        directionsButton.setOnClickListener { openGoogleMapsDirections() }
        refreshLocationButton.setOnClickListener { fetchBinLocation() }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        fetchBinLocation()
    }

    private fun fetchBinLocation() {
        showLoadingState("Fetching bin location...")
        val binRef = db.getReference("gps")
        binRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val latitudeString = snapshot.child("latitude").getValue(String::class.java)
                    val longitudeString = snapshot.child("longitude").getValue(String::class.java)
                    val latitude = latitudeString?.toDoubleOrNull()
                    val longitude = longitudeString?.toDoubleOrNull()

                    if (latitude != null && longitude != null) {
                        binLatitude = latitude
                        binLongitude = longitude
                        updateMapWithLocation(LatLng(latitude, longitude))
                        showBinLocationInfo(latitude, longitude)
                        hideLoadingOverlay()
                    } else {
                        showNoDataState("Location data (latitude/longitude) not found or invalid in Realtime Database.")
                    }
                } else {
                    showNoDataState("Bin data node 'gps' not found in Realtime Database.")
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error fetching location data", error.toException())
                showErrorState("Error fetching location: ${error.message}")
            }
        })
    }

    private fun updateMapWithLocation(location: LatLng) {
        googleMap.clear()
        googleMap.addMarker(MarkerOptions().position(location).title("Smart Bin Location"))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, DEFAULT_ZOOM))
    }

    private fun showBinLocationInfo(latitude: Double, longitude: Double) {
        // Show coordinates
        coordinatesText.text = "Coordinates: ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
        coordinatesLayout.visibility = View.VISIBLE

        // Show directions button
        directionsButton.visibility = View.VISIBLE

        // Try to get user location for distance calculation
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    userLocation = location
                    val distance = calculateDistance(location.latitude, location.longitude, latitude, longitude)
                    distanceText.text = "Distance: ${String.format("%.1f", distance)} km"
                    distanceLayout.visibility = View.VISIBLE
                } else {
                    distanceText.text = "Distance: Unable to get current location"
                    distanceLayout.visibility = View.VISIBLE
                }
            }.addOnFailureListener {
                distanceText.text = "Distance: Location permission required"
                distanceLayout.visibility = View.VISIBLE
            }
        } catch (e: SecurityException) {
            distanceText.text = "Distance: Location permission required"
            distanceLayout.visibility = View.VISIBLE
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return (results[0] / 1000.0)
    }

    private fun openGoogleMapsDirections() {
        val latitude = binLatitude
        val longitude = binLongitude

        if (latitude != null && longitude != null) {
            val uri = Uri.parse("google.navigation:q=$latitude,$longitude")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
            mapIntent.setPackage("com.google.android.apps.maps")

            try {
                startActivity(mapIntent)
            } catch (e: Exception) {
                // Fallback to web version if Google Maps app is not installed
                val webUri = Uri.parse("https://maps.google.com/?q=$latitude,$longitude")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                startActivity(webIntent)
            }
        }
    }

    private fun showLoadingState(message: String) {
        locationStatusMessageTextView.text = message
        findViewById<LinearLayout>(R.id.loadingOverlay).visibility = View.VISIBLE
    }

    private fun showErrorState(message: String) {
        locationStatusMessageTextView.text = "Error: $message"
        locationStatusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
        findViewById<LinearLayout>(R.id.loadingOverlay).visibility = View.VISIBLE
        locationProgressBar.visibility = View.GONE
    }

    private fun showNoDataState(message: String) {
        locationStatusMessageTextView.text = message
        locationStatusMessageTextView.setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
        findViewById<LinearLayout>(R.id.loadingOverlay).visibility = View.VISIBLE
        locationProgressBar.visibility = View.GONE
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM))
    }

    private fun hideLoadingOverlay() {
        findViewById<LinearLayout>(R.id.loadingOverlay).visibility = View.GONE
    }
}