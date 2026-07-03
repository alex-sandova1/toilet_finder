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

    suspend fun fetchAggregate(place: Place): RestroomAggregate? {
        val snapshot = bathroomsCollection.document(documentIdForPlace(place)).get().await()
        return if (snapshot.exists()) snapshot.toObject(RestroomAggregate::class.java) else null
    }

    suspend fun submitReport(place: Place, report: RestroomReportInput): RestroomAggregate {
        val documentId = documentIdForPlace(place)
        val documentRef = bathroomsCollection.document(documentId)
        val placeName = place.displayName ?: "Restroom"

        return firestore.runTransaction { transaction ->
            val existing = transaction.get(documentRef).toObject(RestroomAggregate::class.java)
            val updated = mergeRestroomAggregate(
                existing = existing,
                placeId = documentId,
                placeName = placeName,
                report = report
            )
            transaction.set(documentRef, updated)
            updated
        }.await()
    }

    private fun documentIdForPlace(place: Place): String =
        place.id ?: place.location?.toDocumentId() ?: place.displayName ?: "unknown_restroom"
}

private fun LatLng.toDocumentId(): String =
    "${latitude.toFirestoreCoordinate()}_${longitude.toFirestoreCoordinate()}"

private fun Double.toFirestoreCoordinate(): String =
    toString().replace('-', 'n').replace('.', '_')

