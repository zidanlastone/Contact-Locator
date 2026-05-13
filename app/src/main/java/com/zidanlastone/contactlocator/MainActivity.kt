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
import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject

class MainActivity : AppCompatActivity() {

    private val displayedList = mutableListOf<LocationItem>()
    private lateinit var adapter: LocationAdapter
    private lateinit var progressBar: android.widget.ProgressBar
    
    // Firestore reference
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var locationsCollection: CollectionReference
    
    private var lastVisible: DocumentSnapshot? = null
    private var isLoading = false
    private var isLastPage = false
    private val pageSize = 20L
    
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var currentQuery = ""

    private val addLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val name = data?.getStringExtra("name") ?: ""
            val address = data?.getStringExtra("address")
            val contact = data?.getStringExtra("contact")
            val localImageUriString = data?.getStringExtra("imageUri")
            val latitude = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
            val longitude = data?.getDoubleExtra("longitude", 0.0) ?: 0.0

            if (name.isNotEmpty()) {
                val newItem = LocationItem(
                    id = (System.currentTimeMillis() % 10000),
                    name = name,
                    nameLower = name.lowercase(),
                    address = address,
                    contact = contact,
                    latitude = latitude,
                    longitude = longitude
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
        progressBar = findViewById(R.id.progressBar)

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        adapter = LocationAdapter(displayedList)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && !isLastPage) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && totalItemCount >= pageSize
                    ) {
                        loadMoreData()
                    }
                }
            }
        })

        fabAdd.setOnClickListener {
            val intent = Intent(this, AddLocationActivity::class.java)
            addLocationLauncher.launch(intent)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    currentQuery = query
                    resetAndLoad()
                }
                searchHandler.postDelayed(searchRunnable!!, 500)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        loadInitialData()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }

    private fun resetAndLoad() {
        lastVisible = null
        isLastPage = false
        displayedList.clear()
        adapter.notifyDataSetChanged()
        loadInitialData()
    }

    private fun loadInitialData() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = android.view.View.VISIBLE
        
        val queryText = currentQuery.lowercase().trim()
        var query: Query = locationsCollection
        
        if (queryText.isNotEmpty()) {
            val isNumeric = queryText.toLongOrNull() != null
            if (isNumeric) {
                // If it's a number, try to match ID exactly
                query = query.whereEqualTo("id", queryText.toLong())
            } else {
                // Prefix search by lowercase name
                query = query.orderBy("nameLower")
                    .startAt(queryText)
                    .endAt(queryText + "\uf8ff")
            }
        } else {
            query = query.orderBy("name")
        }
        
        query.limit(pageSize).get()
            .addOnSuccessListener { documents ->
                isLoading = false
                progressBar.visibility = android.view.View.GONE
                if (documents.isEmpty) {
                    isLastPage = true
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }
                
                for (doc in documents) {
                    val item = doc.toObject(LocationItem::class.java)
                    item?.let { displayedList.add(it.copy(firebaseKey = doc.id)) }
                }
                
                if (documents.size() < pageSize) {
                    isLastPage = true
                } else {
                    lastVisible = documents.documents[documents.size() - 1]
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                isLoading = false
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadMoreData() {
        if (isLoading || isLastPage || lastVisible == null) return
        isLoading = true
        progressBar.visibility = android.view.View.VISIBLE
        
        val queryText = currentQuery.lowercase().trim()
        var query: Query = locationsCollection
        
        if (queryText.isNotEmpty()) {
            val isNumeric = queryText.toLongOrNull() != null
            if (isNumeric) {
                // Paginate exact ID match? (usually only 1 result, but still)
                query = query.whereEqualTo("id", queryText.toLong())
            } else {
                query = query.orderBy("nameLower")
                    .startAt(queryText)
                    .endAt(queryText + "\uf8ff")
            }
        } else {
            query = query.orderBy("name")
        }
        
        query.startAfter(lastVisible!!).limit(pageSize).get()
            .addOnSuccessListener { documents ->
                isLoading = false
                progressBar.visibility = android.view.View.GONE
                if (documents.isEmpty) {
                    isLastPage = true
                    return@addOnSuccessListener
                }
                
                for (doc in documents) {
                    val item = doc.toObject(LocationItem::class.java)
                    item?.let { displayedList.add(it.copy(firebaseKey = doc.id)) }
                }
                
                if (documents.size() < pageSize) {
                    isLastPage = true
                } else {
                    lastVisible = documents.documents[documents.size() - 1]
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                isLoading = false
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveToFirestore(item: LocationItem) {
        Log.d("MainActivity", "Attempting to save item to Firestore. Name: ${item.name}, ID: ${item.id}, Address: ${item.address}")
        locationsCollection.add(item)
            .addOnSuccessListener { docRef ->
                Log.d("MainActivity", "Successfully saved item. ID: ${docRef.id}")
                Toast.makeText(this, "Data synced with Firestore", Toast.LENGTH_SHORT).show()
                resetAndLoad() // Refresh list after adding
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Failed to save item: ${e.message}", e)
                Toast.makeText(this, "Firestore Sync Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}