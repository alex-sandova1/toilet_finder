package com.example.driverassist.model

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val isVerifiedUser: Boolean = false,
    val subscriptionExpiryMillis: Long = 0L
)
