package com.example.driverassist.model

import com.google.android.gms.maps.model.LatLng

// Defines route geometry and info.
data class RouteDetails(
    val points: List<LatLng>,
    val distanceText: String,
    val durationText: String
)

// Stores the aggregate community state for a restroom.
data class RestroomAggregate(
    val placeId: String = "",
    val placeName: String = "",
    val ratingCount: Int = 0,
    val cleanlinessSum: Int = 0,
    val avgCleanliness: Double = 0.0,
    val dirtyLikelihood: Double = 0.0,
    val dirtyReports: Int = 0,
    val closedReports: Int = 0,
    val dirtyUntilEpochMillis: Long = 0L,
    val closedUntilEpochMillis: Long = 0L,
    val lastUpdatedEpochMillis: Long = 0L,
    val isMarkedIncorrect: Boolean = false,
    val note: String = "",
    val suggestedCategory: String = ""
)

// Represents one community update submission.
data class RestroomReportInput(
    val cleanlinessRating: Int? = null,
    val markedDirty: Boolean = false,
    val markedClosed: Boolean = false,
    val markedIncorrect: Boolean = false,
    val note: String? = null,
    val suggestedCategory: String? = null
)

private const val DEFAULT_DIRTY_ALERT_DURATION_MILLIS = 3 * 60 * 60 * 1000L
private const val DEFAULT_CLOSED_ALERT_DURATION_MILLIS = 2 * 60 * 60 * 1000L

// Merges a new report into the aggregate restroom document.
fun mergeRestroomAggregate(
    existing: RestroomAggregate?,
    placeId: String,
    placeName: String,
    report: RestroomReportInput,
    nowMillis: Long = System.currentTimeMillis(),
    dirtyAlertDurationMillis: Long = DEFAULT_DIRTY_ALERT_DURATION_MILLIS,
    closedAlertDurationMillis: Long = DEFAULT_CLOSED_ALERT_DURATION_MILLIS
): RestroomAggregate {
    val current = existing ?: RestroomAggregate(placeId = placeId, placeName = placeName)
    val normalizedRating = report.cleanlinessRating?.coerceIn(1, 5)

    val nextRatingCount = current.ratingCount + if (normalizedRating != null) 1 else 0
    val nextCleanlinessSum = current.cleanlinessSum + (normalizedRating ?: 0)
    val nextAverage = if (nextRatingCount > 0) nextCleanlinessSum.toDouble() / nextRatingCount else current.avgCleanliness
    val nextLikelihood = if (nextRatingCount > 0) ((5.0 - nextAverage) / 4.0).coerceIn(0.0, 1.0) else current.dirtyLikelihood

    val nextDirtyUntil = if (report.markedDirty) {
        maxOf(current.dirtyUntilEpochMillis, nowMillis + dirtyAlertDurationMillis)
    } else current.dirtyUntilEpochMillis

    val nextClosedUntil = if (report.markedClosed) {
        maxOf(current.closedUntilEpochMillis, nowMillis + closedAlertDurationMillis)
    } else current.closedUntilEpochMillis

    return current.copy(
        placeId = placeId,
        placeName = if (placeName.isBlank()) current.placeName else placeName,
        ratingCount = nextRatingCount,
        cleanlinessSum = nextCleanlinessSum,
        avgCleanliness = nextAverage,
        dirtyLikelihood = nextLikelihood,
        dirtyReports = current.dirtyReports + if (report.markedDirty) 1 else 0,
        closedReports = current.closedReports + if (report.markedClosed) 1 else 0,
        dirtyUntilEpochMillis = nextDirtyUntil,
        closedUntilEpochMillis = nextClosedUntil,
        lastUpdatedEpochMillis = nowMillis,
        isMarkedIncorrect = current.isMarkedIncorrect || report.markedIncorrect,
        note = report.note ?: current.note,
        suggestedCategory = report.suggestedCategory ?: current.suggestedCategory
    )
}

// Returns true when a temporary dirty alert is still active.
fun RestroomAggregate.isDirtyNow(nowMillis: Long = System.currentTimeMillis()): Boolean =
    dirtyUntilEpochMillis > nowMillis

// Returns true when a temporary closed alert is still active.
fun RestroomAggregate.isClosedNow(nowMillis: Long = System.currentTimeMillis()): Boolean =
    closedUntilEpochMillis > nowMillis

// Converts the historical dirty likelihood to a percentage for UI display.
fun RestroomAggregate.dirtyLikelihoodPercent(): Int =
    (dirtyLikelihood * 100).toInt().coerceIn(0, 100)

// Represents a restroom added manually by a user.
data class CustomRestroom(
    val id: String = "",
    val name: String = "",
    val category: String = "Public Restroom",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isDeleted: Boolean = false,
    val note: String = "",
    val addedByUserId: String = "anonymous",
    val timestamp: Long = System.currentTimeMillis()
)

