package com.harindu.TrakTrash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class AuthActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var authButton: Button
    private lateinit var registerTextView: TextView
    private lateinit var googleSignInButton: Button
    private lateinit var loginProgressBar: ProgressBar

    companion object {
        private const val TAG = "AuthActivity"
    }

    // API for Google Sign-In
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            Log.d(TAG, "Google sign-in successful: ${account.email}")
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed: ${e.statusCode}")
            handleGoogleSignInError(e.statusCode)
            showLoading(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // Add a listener to the back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Firebase
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        auth = Firebase.auth

        // Configure Google Sign In
        configureGoogleSignIn()

        initializeViews()

        // Set up click listeners
        setupClickListeners()

        Log.d(TAG, "AuthActivity initialized successfully")
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun initializeViews() {
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        authButton = findViewById(R.id.authButton)
        registerTextView = findViewById(R.id.registerTextView)
        googleSignInButton = findViewById(R.id.googleSignInButton)
        loginProgressBar = findViewById(R.id.loginProgressBar)
    }

    private fun setupClickListeners() {
        googleSignInButton.setOnClickListener { googleSignIn() }
        authButton.setOnClickListener { emailPasswordSignIn() }
        registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is already signed in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User already signed in: ${currentUser.email}")
            navigateToBinList()
        }
    }

    private fun googleSignIn() {
        Log.d(TAG, "Starting Google Sign-In with account picker")
        showLoading(true)

        // Force sign out to ensure account picker is always shown
        googleSignInClient.signOut().addOnCompleteListener { signOutTask ->
            Log.d(TAG, "Google sign out completed: ${signOutTask.isSuccessful}")

            // Also revoke access to force account selection
            googleSignInClient.revokeAccess().addOnCompleteListener { revokeTask ->
                Log.d(TAG, "Google access revoked: ${revokeTask.isSuccessful}")

                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }
    }

    private fun emailPasswordSignIn() {
        if (!isValidInput()) return

        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        showLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Email sign-in successful")
                    navigateToBinList()
                } else {
                    Log.w(TAG, "Email sign-in failed", task.exception)
                    val errorMessage = task.exception?.message ?: "Authentication failed"
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                    showLoading(false)
                }
            }
    }

    private fun isValidInput(skipPasswordCheck: Boolean = false): Boolean {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        return when {
            email.isEmpty() -> {
                Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show()
                false
            }
            !skipPasswordCheck && password.isEmpty() -> {
                Toast.makeText(this, "Password is required", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun navigateToBinList() {
        startActivity(Intent(this, BinListActivity::class.java))
        finish()
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        Log.d(TAG, "Authenticating with Firebase using Google credentials")

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Firebase authentication successful")
                    val user = auth.currentUser
                    Toast.makeText(this, "Welcome, ${user?.displayName ?: user?.email}", Toast.LENGTH_SHORT).show()
                    navigateToBinList()
                } else {
                    Log.e(TAG, "Firebase authentication failed", task.exception)
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    showLoading(false)
                }
            }
    }

    private fun handleGoogleSignInError(statusCode: Int) {
        val errorMessage = when (statusCode) {
            10 -> "Configuration error. Please contact support."
            12500 -> "Sign-in cancelled"
            7 -> "Network error. Please check your connection."
            8 -> "Internal error. Please try again."
            16 -> "Sign-in currently in progress"
            else -> "Google Sign-In failed (Error: $statusCode)"
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            loginProgressBar.visibility = View.VISIBLE
            googleSignInButton.visibility = View.GONE
            authButton.visibility = View.GONE
        } else {
            loginProgressBar.visibility = View.GONE
            googleSignInButton.visibility = View.VISIBLE
            authButton.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // googleSignInClient.signOut()
    }
}