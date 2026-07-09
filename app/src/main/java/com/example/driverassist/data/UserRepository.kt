package com.example.driverassist.data

import com.example.driverassist.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val usersCollection = firestore.collection("users")

    suspend fun fetchUserProfile(uid: String): UserProfile? {
        val snapshot = usersCollection.document(uid).get().await()
        return if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
    }

    suspend fun createUserProfile(uid: String, displayName: String, email: String) {
        val profile = UserProfile(
            uid = uid,
            displayName = displayName,
            email = email,
            isVerifiedUser = false // Default to free
        )
        usersCollection.document(uid).set(profile).await()
    }
}
