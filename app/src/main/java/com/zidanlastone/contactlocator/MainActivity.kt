package com.zidanlastone.contactlocator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    private val fullLocationList = mutableListOf<LocationItem>()
    private val displayedList = mutableListOf<LocationItem>()
    private lateinit var adapter: LocationAdapter
    
    // Firestore reference
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var locationsCollection: CollectionReference
    private var snapshotListener: ListenerRegistration? = null

    private val addLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val name = data?.getStringExtra("name") ?: ""
            val address = data?.getStringExtra("address")
            val contact = data?.getStringExtra("contact")
            val localImageUriString = data?.getStringExtra("imageUri")
            val latitude = data?.getDoubleExtra("latitude", 0.0)
            val longitude = data?.getDoubleExtra("longitude", 0.0)

            if (name.isNotEmpty()) {
                val newItem = LocationItem(
                    id = fullLocationList.size + 1,
                    name = name,
                    address = address,
                    contact = contact,
                    latitude = if (latitude != 0.0) latitude else null,
                    longitude = if (longitude != 0.0) longitude else null
                )
                
                if (localImageUriString != null) {
                    saveToFirestore(newItem.copy(imageUri = localImageUriString))
                } else {
                    saveToFirestore(newItem)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        locationsCollection = db.collection("users").document(currentUser.uid).collection("locations")

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val btnLogout = findViewById<android.widget.Button>(R.id.btnLogout)

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        adapter = LocationAdapter(displayedList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fabAdd.setOnClickListener {
            val intent = Intent(this, AddLocationActivity::class.java)
            addLocationLauncher.launch(intent)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        loadFromFirestore()
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
    }

    private fun loadFromFirestore() {
        snapshotListener = locationsCollection.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Toast.makeText(this, "Firestore Error: ${e.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null) {
                fullLocationList.clear()
                for (doc in snapshot.documents) {
                    val item = doc.toObject(LocationItem::class.java)
                    if (item != null) {
                        fullLocationList.add(item.copy(firebaseKey = doc.id))
                    }
                }
                filter(findViewById<EditText>(R.id.etSearch).text.toString())
            }
        }
    }

    private fun saveToFirestore(item: LocationItem) {
        Log.d("MainActivity", "Attempting to save item to Firestore: ${item.name}")
        locationsCollection.add(item)
            .addOnSuccessListener { docRef ->
                Log.d("MainActivity", "Successfully saved item. ID: ${docRef.id}")
                Toast.makeText(this, "Data synced with Firestore", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Failed to save item: ${e.message}", e)
                Toast.makeText(this, "Firestore Sync Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filter(query: String) {
        val filteredList = if (query.isEmpty()) {
            fullLocationList
        } else {
            fullLocationList.filter {
                it.name.contains(query, ignoreCase = true) || 
                it.id.toString().contains(query)
            }
        }
        displayedList.clear()
        displayedList.addAll(filteredList)
        adapter.notifyDataSetChanged()
    }
}