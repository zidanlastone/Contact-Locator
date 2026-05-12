package com.zidanlastone.contactlocator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddLocationActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var photoUri: Uri? = null
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var existingItem: LocationItem? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { uri ->
                findViewById<ImageView>(R.id.ivPicture).load(uri)
            }
        } else {
            Toast.makeText(this, "Camera cancelled or failed", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (cameraGranted) {
            openCamera()
        }
        
        if (locationGranted) {
            getLastLocation()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("photoUri", photoUri)
        latitude?.let { outState.putDouble("latitude", it) }
        longitude?.let { outState.putDouble("longitude", it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (savedInstanceState != null) {
            photoUri = savedInstanceState.getParcelable("photoUri")
            val lat = savedInstanceState.getDouble("latitude", 0.0)
            val lon = savedInstanceState.getDouble("longitude", 0.0)
            if (lat != 0.0) latitude = lat
            if (lon != 0.0) longitude = lon
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_add_location)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        existingItem = intent.getSerializableExtra("location_item") as? LocationItem

        val etName = findViewById<EditText>(R.id.etName)
        val etAddress = findViewById<EditText>(R.id.etAddress)
        val etContact = findViewById<EditText>(R.id.etContact)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTakePicture = findViewById<Button>(R.id.btnTakePicture)
        val btnGetLocation = findViewById<Button>(R.id.btnGetLocation)

        // If editing, populate fields
        existingItem?.let { item ->
            etName.setText(item.name)
            etAddress.setText(item.address)
            etContact.setText(item.contact)
            item.imageUri?.let { uriStr ->
                photoUri = Uri.parse(uriStr)
                findViewById<ImageView>(R.id.ivPicture).load(photoUri)
            }
            latitude = item.latitude
            longitude = item.longitude
            btnSave.text = "Update Item"
        }

        // If we have a photoUri, load it (in case of orientation change)
        photoUri?.let { findViewById<ImageView>(R.id.ivPicture).load(it) }
        latitude?.let { findViewById<TextView>(R.id.tvLatitude).text = "Latitude: $it" }
        longitude?.let { findViewById<TextView>(R.id.tvLongitude).text = "Longitude: $it" }

        btnTakePicture.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        btnGetLocation.setOnClickListener {
            checkLocationPermissionAndGet()
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString()
            val address = etAddress.text.toString()
            val contact = etContact.text.toString()

            if (name.isNotEmpty()) {
                val resultIntent = Intent()
                resultIntent.putExtra("name", name)
                if (address.isNotEmpty()) {
                    resultIntent.putExtra("address", address)
                }
                if (contact.isNotEmpty()) {
                    resultIntent.putExtra("contact", contact)
                }
                resultIntent.putExtra("imageUri", photoUri?.toString())
                resultIntent.putExtra("latitude", latitude ?: 0.0)
                resultIntent.putExtra("longitude", longitude ?: 0.0)
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun checkLocationPermissionAndGet() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getLastLocation()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun openCamera() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: Exception) {
            Toast.makeText(this, "Error creating file: ${ex.message}", Toast.LENGTH_SHORT).show()
            null
        }
        
        photoFile?.let { file ->
            photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            takePictureLauncher.launch(photoUri!!)
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File(storageDir, "JPEG_${timeStamp}.jpg")
    }

    private fun getLastLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    findViewById<TextView>(R.id.tvLatitude).text = "Latitude: $latitude"
                    findViewById<TextView>(R.id.tvLongitude).text = "Longitude: $longitude"
                } else {
                    Toast.makeText(this, "Unable to get location. Make sure GPS is on.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}