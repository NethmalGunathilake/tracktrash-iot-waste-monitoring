package com.harindu.TrakTrash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton  // Changed from Button to ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import android.graphics.Color

data class Bin(val id: String, val fillLevel: Int)

class BinAdapter(
    private val bins: List<Bin>,
    private val onItemClick: (Bin) -> Unit
) : RecyclerView.Adapter<BinAdapter.BinViewHolder>() {

    class BinViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val binIdTextView: TextView = view.findViewById(R.id.binIdTextView)
        val fillLevelTextView: TextView = view.findViewById(R.id.fillLevelTextView)
        val fillLevelProgressContainer: androidx.cardview.widget.CardView = view.findViewById(R.id.fillLevelProgressContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BinViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bin, parent, false)
        return BinViewHolder(view)
    }

    override fun onBindViewHolder(holder: BinViewHolder, position: Int) {
        val bin = bins[position]
        holder.binIdTextView.text = bin.id
        holder.fillLevelTextView.text = "${bin.fillLevel}%"

        // Set colors based on fill level with better contrast
        when {
            bin.fillLevel >= 85 -> {
                // High fill - Red background with white text
                holder.fillLevelProgressContainer.setCardBackgroundColor(Color.parseColor("#F44336"))
                holder.fillLevelTextView.setTextColor(Color.WHITE)
            }
            bin.fillLevel >= 50 -> {
                // Medium fill - Orange background with white text
                holder.fillLevelProgressContainer.setCardBackgroundColor(Color.parseColor("#FF9800"))
                holder.fillLevelTextView.setTextColor(Color.WHITE)
            }
            bin.fillLevel >= 25 -> {
                // Low-Medium fill - Yellow background with dark text
                holder.fillLevelProgressContainer.setCardBackgroundColor(Color.parseColor("#FFC107"))
                holder.fillLevelTextView.setTextColor(Color.parseColor("#212121"))
            }
            else -> {
                // Very low fill - Green background with white text
                holder.fillLevelProgressContainer.setCardBackgroundColor(Color.parseColor("#4CAF50"))
                holder.fillLevelTextView.setTextColor(Color.WHITE)
            }
        }

        holder.itemView.setOnClickListener { onItemClick(bin) }
    }

    override fun getItemCount() = bins.size
}

class BinListActivity : AppCompatActivity() {

    private lateinit var db: FirebaseDatabase
    private lateinit var binsRecyclerView: RecyclerView
    private lateinit var adapter: BinAdapter
    private val bins = mutableListOf<Bin>()
    private lateinit var backButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bin_list)

        db = Firebase.database
        binsRecyclerView = findViewById(R.id.binsRecyclerView)
        binsRecyclerView.layoutManager = LinearLayoutManager(this)

        adapter = BinAdapter(bins) { bin ->
            val intent = Intent(this, DashboardActivity::class.java)
            intent.putExtra("BIN_ID", bin.id)
            startActivity(intent)
        }
        binsRecyclerView.adapter = adapter

        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }

        fetchBinData()
    }

    private fun fetchBinData() {
        val binRef = db.getReference("gps")
        binRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val distanceCM = snapshot.child("distanceCM").value?.toString()?.toDoubleOrNull()
                    val maxDistance = 48.0
                    val minDistance = 20.0
                    val fillLevel = if (distanceCM != null) {
                        when {
                            distanceCM <= minDistance -> 100
                            distanceCM >= maxDistance -> 0
                            else -> {
                                val distanceRange = maxDistance - minDistance
                                val calculatedLevel = 100 - ((distanceCM - minDistance) * 100 / distanceRange)
                                calculatedLevel.toInt().coerceIn(0, 100)
                            }
                        }
                    } else {
                        0 // Default to 0% if data is not available
                    }

                    bins.clear()
                    bins.add(Bin("Bin 01", fillLevel))
                    bins.add(Bin("Bin 02", 0)) // Mock data
                    bins.add(Bin("Bin 03", 0)) // Mock data
                    adapter.notifyDataSetChanged()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w("BinListActivity", "Failed to read value.", error.toException())
            }
        })
    }
}