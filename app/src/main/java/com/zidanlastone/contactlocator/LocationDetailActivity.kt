package com.zidanlastone.contactlocator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LocationDetailActivity : AppCompatActivity() {

    private var currentItem: LocationItem? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val editLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val name = data?.getStringExtra("name") ?: ""
            val address = data?.getStringExtra("address")
            val contact = data?.getStringExtra("contact")
            val imageUri = data?.getStringExtra("imageUri")
            val latitude = data?.getDoubleExtra("latitude", 0.0)
            val longitude = data?.getDoubleExtra("longitude", 0.0)

            currentItem?.let { item ->
                val updatedItem = item.copy(
                    name = name,
                    address = address,
                    contact = contact,
                    imageUri = imageUri,
                    latitude = if (latitude != 0.0) latitude else 0.0,
                    longitude = if (longitude != 0.0) longitude else 0.0
                )
                updateInFirestore(updatedItem)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_location_detail)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentItem = intent.getSerializableExtra("location_item") as? LocationItem
        displayItem(currentItem)
    }

    private fun displayItem(item: LocationItem?) {
        if (item != null) {
            val ivPicture = findViewById<ImageView>(R.id.ivDetailPicture)
            val tvId = findViewById<TextView>(R.id.tvDetailId)
            val tvName = findViewById<TextView>(R.id.tvDetailName)
            val tvAddress = findViewById<TextView>(R.id.tvDetailAddress)
            val tvContact = findViewById<TextView>(R.id.tvDetailContact)
            val tvLatitude = findViewById<TextView>(R.id.tvDetailLatitude)
            val tvLongitude = findViewById<TextView>(R.id.tvDetailLongitude)
            val btnOpenMaps = findViewById<Button>(R.id.btnDetailOpenMaps)
            val btnWhatsApp = findViewById<Button>(R.id.btnDetailWhatsApp)
            val btnEdit = findViewById<Button>(R.id.btnDetailEdit)

            if (item.imageUri != null) {
                ivPicture.visibility = View.VISIBLE
                ivPicture.load(item.imageUri)
            } else {
                ivPicture.visibility = View.GONE
            }

            tvId.text   = getString(R.string.label_id) + item.id
            tvName.text = getString(R.string.label_name) + item.name
            
            if (!item.address.isNullOrEmpty()) {
                tvAddress.visibility = View.VISIBLE
                tvAddress.text = getString(R.string.label_address) + item.address
            } else {
                tvAddress.visibility = View.GONE
            }
            
            if (!item.contact.isNullOrEmpty()) {
                tvContact.visibility = View.VISIBLE
                btnWhatsApp.visibility = View.VISIBLE
                tvContact.text = getString(R.string.label_contact) + item.contact
            } else {
                tvContact.visibility = View.GONE
                btnWhatsApp.visibility = View.GONE
            }
            
            if (item.latitude != null && item.longitude != null) {
                tvLatitude.visibility = View.VISIBLE
                tvLongitude.visibility = View.VISIBLE
                tvLatitude.text = getString(R.string.label_latitude) + item.latitude
                tvLongitude.text = getString(R.string.label_longitude) + item.longitude
                
                btnOpenMaps.setOnClickListener {
                    val mapUri = Uri.parse("geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}(${item.name})")
                    val intent = Intent(Intent.ACTION_VIEW, mapUri)
                    intent.setPackage("com.google.android.apps.maps")
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        startActivity(Intent(Intent.ACTION_VIEW, mapUri))
                    }
                }
            } else {
                tvLatitude.visibility = View.GONE
                tvLongitude.visibility = View.GONE
                btnOpenMaps.setOnClickListener {
                    Toast.makeText(this, "Location coordinates not available", Toast.LENGTH_SHORT).show()
                }
            }

            btnWhatsApp.setOnClickListener {
                var contact = item.contact?.replace("[^0-9]".toRegex(), "") ?: ""
                if (contact.isNotEmpty()) {
                    if (contact.startsWith("0")) {
                        contact = "62" + contact.substring(1)
                    } else if (!contact.startsWith("62")) {
                        contact = "62" + contact
                    }

                    val url = "https://api.whatsapp.com/send?phone=$contact"
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse(url)
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Invalid contact number", Toast.LENGTH_SHORT).show()
                }
            }

            btnEdit.setOnClickListener {
                val intent = Intent(this, AddLocationActivity::class.java)
                intent.putExtra("location_item", item)
                editLocationLauncher.launch(intent)
            }
        } else {
            finish()
        }
    }

    private fun updateInFirestore(item: LocationItem) {
        val userId = auth.currentUser?.uid ?: return
        val docId = item.firebaseKey ?: return

        Log.d("LocationDetail", "Attempting to update item. DocID: $docId, Name: ${item.name}, Address: ${item.address}")
        db.collection("users").document(userId)
            .collection("locations").document(docId)
            .set(item)
            .addOnSuccessListener {
                Log.d("LocationDetail", "Update successful for DocID: $docId")
                currentItem = item
                displayItem(item)
                Toast.makeText(this, "Updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("LocationDetail", "Update failed", e)
                Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}