package com.example.driverassist.model

import com.google.firebase.firestore.PropertyName

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    @get:PropertyName("verifiedUser") @set:PropertyName("verifiedUser")
    var isVerifiedUser: Boolean = false,
    val subscriptionExpiryMillis: Long = 0L,
    val totalReports: Int = 0,
    val totalAdded: Int = 0,
    val totalVerifications: Int = 0
)
