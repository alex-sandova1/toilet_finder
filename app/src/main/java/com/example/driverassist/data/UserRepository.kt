package com.example.driverassist.data

import com.example.driverassist.model.UserProfile
import com.google.firebase.firestore.FieldValue
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

    suspend fun incrementUserStats(uid: String, reports: Int = 0, added: Int = 0, verifications: Int = 0) {
        val updates = mutableMapOf<String, Any>()
        if (reports > 0) updates["totalReports"] = FieldValue.increment(reports.toLong())
        if (added > 0) updates["totalAdded"] = FieldValue.increment(added.toLong())
        if (verifications > 0) updates["totalVerifications"] = FieldValue.increment(verifications.toLong())

        if (updates.isNotEmpty()) {
            usersCollection.document(uid).update(updates).await()
        }
    }
}
