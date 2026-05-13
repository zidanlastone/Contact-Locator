package com.zidanlastone.contactlocator

import com.google.firebase.firestore.Exclude
import java.io.Serializable

data class LocationItem(
    val id: Long = 0,
    val name: String = "",
    val nameLower: String = "", // Added for case-insensitive search
    val address: String? = null,
    val contact: String? = null,
    val imageUri: String? = null,
    val latitude: Double? = 0.0,
    val longitude: Double? = 0.0,
    @get:Exclude
    val firebaseKey: String? = null
) : Serializable