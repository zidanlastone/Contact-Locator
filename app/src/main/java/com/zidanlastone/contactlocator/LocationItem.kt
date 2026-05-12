package com.zidanlastone.contactlocator

import com.google.firebase.firestore.Exclude
import java.io.Serializable

data class LocationItem(
    val id: Long = 0,
    val name: String = "",
    val address: String? = null,
    val contact: String? = null,
    val imageUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @get:Exclude
    val firebaseKey: String? = null
) : Serializable