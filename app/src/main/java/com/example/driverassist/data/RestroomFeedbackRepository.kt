package com.example.driverassist.data

import com.example.driverassist.model.RestroomAggregate
import com.example.driverassist.model.RestroomReportInput
import com.example.driverassist.model.mergeRestroomAggregate
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RestroomFeedbackRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val bathroomsCollection = firestore.collection("bathrooms")
    private val customBathroomsCollection = firestore.collection("custom_restrooms")

    suspend fun fetchAggregate(id: String): RestroomAggregate? {
        val snapshot = bathroomsCollection.document(id).get().await()
        return if (snapshot.exists()) snapshot.toObject(RestroomAggregate::class.java) else null
    }

    suspend fun saveCustomRestroom(name: String, category: String, note: String, latLng: LatLng): String {
        val docRef = customBathroomsCollection.document()
        val custom = com.example.driverassist.model.CustomRestroom(
            id = docRef.id,
            name = name,
            category = category,
            note = note,
            latitude = latLng.latitude,
            longitude = latLng.longitude
        )
        docRef.set(custom).await()
        return docRef.id
    }

    suspend fun fetchCustomRestrooms(): List<com.example.driverassist.model.CustomRestroom> {
        val snapshot = customBathroomsCollection.whereEqualTo("deleted", false).get().await()
        return snapshot.toObjects(com.example.driverassist.model.CustomRestroom::class.java)
    }

    suspend fun deleteCustomRestroom(id: String) {
        customBathroomsCollection.document(id).update("deleted", true).await()
    }

    suspend fun updateCustomRestroomCategory(id: String, category: String) {
        customBathroomsCollection.document(id).update("category", category).await()
    }

    suspend fun fetchIncorrectRestroomIds(): List<String> {
        val snapshot = bathroomsCollection.whereEqualTo("markedIncorrect", true).get().await()
        return snapshot.documents.map { it.id }
    }

    suspend fun fetchCategoryOverrides(): Map<String, String> {
        val snapshot = bathroomsCollection.whereNotEqualTo("suggestedCategory", "").get().await()
        return snapshot.documents.associate { it.id to (it.getString("suggestedCategory") ?: "") }
    }

    suspend fun submitReport(id: String, name: String, report: RestroomReportInput): RestroomAggregate {
        val documentRef = bathroomsCollection.document(id)

        return firestore.runTransaction { transaction ->
            val existing = transaction.get(documentRef).toObject(RestroomAggregate::class.java)
            val updated = mergeRestroomAggregate(
                existing = existing,
                placeId = id,
                placeName = name,
                report = report
            )
            transaction.set(documentRef, updated)
            updated
        }.await()
    }

    fun documentIdForPlace(place: Place): String =
        place.id ?: place.location?.toDocumentId() ?: place.displayName ?: "unknown_restroom"
}

private fun LatLng.toDocumentId(): String =
    "${latitude.toFirestoreCoordinate()}_${longitude.toFirestoreCoordinate()}"

private fun Double.toFirestoreCoordinate(): String =
    toString().replace('-', 'n').replace('.', '_')

