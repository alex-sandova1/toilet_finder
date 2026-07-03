package com.example.driverassist.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestroomAggregateTest {

    @Test
    fun mergeRestroomAggregate_addsRatingAndComputesLikelihood() {
        val updated = mergeRestroomAggregate(
            existing = null,
            placeId = "place-1",
            placeName = "Restroom One",
            report = RestroomReportInput(cleanlinessRating = 1),
            nowMillis = 1_000L
        )

        assertEquals("place-1", updated.placeId)
        assertEquals(1, updated.ratingCount)
        assertEquals(1, updated.cleanlinessSum)
        assertEquals(1.0, updated.avgCleanliness, 0.0001)
        assertEquals(1.0, updated.dirtyLikelihood, 0.0001)
        assertEquals(1_000L, updated.lastUpdatedEpochMillis)
    }

    @Test
    fun mergeRestroomAggregate_preservesAverageWhenOnlyDirtyReportIsSent() {
        val existing = RestroomAggregate(
            placeId = "place-2",
            placeName = "Restroom Two",
            ratingCount = 2,
            cleanlinessSum = 8,
            avgCleanliness = 4.0,
            dirtyLikelihood = 0.25
        )

        val updated = mergeRestroomAggregate(
            existing = existing,
            placeId = "place-2",
            placeName = "Restroom Two",
            report = RestroomReportInput(markedDirty = true),
            nowMillis = 10_000L,
            dirtyAlertDurationMillis = 500L
        )

        assertEquals(2, updated.ratingCount)
        assertEquals(8, updated.cleanlinessSum)
        assertEquals(4.0, updated.avgCleanliness, 0.0001)
        assertEquals(0.25, updated.dirtyLikelihood, 0.0001)
        assertEquals(1, updated.dirtyReports)
        assertTrue(updated.isDirtyNow(10_100L))
        assertFalse(updated.isDirtyNow(10_501L))
    }

    @Test
    fun mergeRestroomAggregate_extendsClosedAlertWithoutShorteningExistingTimer() {
        val existing = RestroomAggregate(
            placeId = "place-3",
            placeName = "Restroom Three",
            closedUntilEpochMillis = 20_000L
        )

        val updated = mergeRestroomAggregate(
            existing = existing,
            placeId = "place-3",
            placeName = "Restroom Three",
            report = RestroomReportInput(markedClosed = true),
            nowMillis = 15_000L,
            closedAlertDurationMillis = 2_000L
        )

        assertEquals(1, updated.closedReports)
        assertEquals(20_000L, updated.closedUntilEpochMillis)
        assertTrue(updated.isClosedNow(19_999L))
        assertFalse(updated.isClosedNow(20_000L))
    }
}

