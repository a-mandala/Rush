/*
 * Copyright (C) 2026  Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.shub39.rush.shared.ui.lyrics

import com.shub39.rush.shared.core.dataclasses.SongMeta
import kotlin.test.Test
import kotlin.test.assertEquals

class PlainLyricsAutoScrollTest {
    @Test
    fun independentPaceUsesThreeSecondsPerLineAtConstantVelocity() {
        for (lines in listOf(10, 40, 120)) {
            val run = PlainLyricsScrollRun()
            run.start(lines * 30f, null, 0, lines)
            assertEquals(10f, run.frame(1000f, null, null, 1f))
            run.consumed(10f, true)
            assertEquals(10f, run.frame(1000f, null, null, 1f))
            assertEquals(2.5f, run.frame(1000f, null, null, 0.25f))
            assertEquals(40f, run.frame(1000f, null, null, 4f))
        }
    }

    @Test
    fun lineCountIsSafeAndDoesNotChangePlayerPace() {
        for (lines in listOf(0, -1, 1)) {
            assertEquals(0.01f, plainLyricsVelocity(30f, null, 0, 1f, lines))
        }
        assertEquals(0.005f, plainLyricsVelocity(1200f, 240_000, 0, 1f, 10))
    }

    @Test
    fun finalPixelFinishesAndStopAllowsRestart() {
        val run = PlainLyricsScrollRun()
        run.start(0.4f, null, 0, 60)
        assertEquals(1f, run.frame(16f, null, null, 1f))
        run.consumed(0.4f, false)
        kotlin.test.assertFalse(run.running)
        kotlin.test.assertTrue(run.restartFromTop(false))
        run.start(100f, null, 0, 60)
        run.stop()
        assertEquals(0f, run.frame(1000f, null, null, 1f))
    }

    @Test
    fun userSpeedScalesTheMeasuredPace() {
        val run = PlainLyricsScrollRun()
        run.start(1200f, 240_000, 120_000, 60)
        assertEquals(20f, run.frame(1000f, 240_000, PlaybackInfo(121_000, 1f), 2f))
        run.consumed(20f, true)
        assertEquals(1180f, run.remaining)
    }

    @Test
    fun playerPauseFreezesTheRun() {
        val run = PlainLyricsScrollRun()
        run.start(1200f, 240_000, 120_000, 60)
        assertEquals(0f, run.frame(1000f, 240_000, PlaybackInfo(120_000, 0f), 1f))
        assertEquals(1200f, run.remaining)
    }

    @Test
    fun backwardSeekRepacesRemainingDistance() {
        val run = PlainLyricsScrollRun()
        run.start(1180f, 240_000, 120_000, 60)
        assertEquals(1180f / 240_000 * 1000, run.frame(1000f, 240_000, PlaybackInfo(0, 1f), 1f))
    }

    @Test
    fun forwardSeekRepacesRemainingDistance() {
        val run = PlainLyricsScrollRun()
        run.start(1200f, 240_000, 0, 60)
        assertEquals(10f, run.frame(1000f, 240_000, PlaybackInfo(120_000, 1f), 1f))
    }

    @Test
    fun playbackSpeedMultipliesPaceWithoutFalseSeekOnSpeedChange() {
        val run = PlainLyricsScrollRun()
        run.start(2400f, 240_000, 0, 60)
        assertEquals(20f, run.frame(1000f, 240_000, PlaybackInfo(1000, 2f), 1f))
        run.consumed(20f, true)
        // Position advanced at the previous speed, despite the new speed being 0.5x.
        // Using the new speed for seek detection would incorrectly re-pace this frame.
        assertEquals(10f, run.frame(2000f, 240_000, PlaybackInfo(5000, 0.5f), 1f))
    }

    @Test
    fun catchUpIsCappedAndEndPositionUsesFullDuration() {
        val run = PlainLyricsScrollRun()
        run.start(1200f, 240_000, 239_999, 60)
        assertEquals(10f, run.frame(1000f, 240_000, null, 1f))
        run.start(1200f, 240_000, 240_000, 60)
        assertEquals(5f, run.frame(1000f, 240_000, null, 1f))
    }

    @Test
    fun restartFromTopRequiresStoppedRunAtEnd() {
        val run = PlainLyricsScrollRun()
        run.start(100f, null, 0, 60)
        kotlin.test.assertFalse(run.restartFromTop(false))
        kotlin.test.assertFalse(run.restartFromTop(true))
        run.stop()
        kotlin.test.assertFalse(run.restartFromTop(true))
        kotlin.test.assertTrue(run.restartFromTop(false))
    }

    @Test
    fun unknownDurationRetainsOriginalVelocityAfterPartwayPauseAndRestart() {
        for (duration in listOf(null, 0L, -1L)) {
            val run = PlainLyricsScrollRun()
            run.start(1800f, duration, 0, 60)
            assertEquals(10f, run.frame(1000f, duration, null, 1f))
            run.consumed(1620f, true)
            run.stop()
            assertEquals(0f, run.frame(1000f, duration, null, 1f))
            run.start(180f, duration, 0, 60)
            assertEquals(10f, run.frame(1000f, duration, null, 1f))
            // A seek or a new measured distance must also preserve an unknown-duration pace.
            assertEquals(10f, run.frame(1000f, duration, PlaybackInfo(90_000, 1f), 1f))
            run.start(360f, duration, 90_000, 60)
            assertEquals(10f, run.frame(1000f, duration, null, 1f))
        }
    }

    @Test
    fun unknownAndLateDurationRepaceAnIndependentRun() {
        val run = PlainLyricsScrollRun()
        run.start(1800f, null, 900_000, 60)
        assertEquals(10f, run.frame(1000f, null, null, 1f))
        assertEquals(5f, run.frame(1000f, 360_000, null, 1f))
        kotlin.test.assertTrue(run.running)
        for (duration in listOf(0L, -1L)) {
            assertEquals(
                null,
                plainLyricsDuration(
                    "Title",
                    "Artist",
                    SongMeta("Title", "Artist", duration = duration),
                ),
            )
        }
    }

    @Test
    fun measuredDistancesFinishAtSongEnd() {
        // Exercises pacing with supplied measured distances; layout measurement needs device
        // testing.
        for (distance in listOf(2400f - 600f, 2400f - 1000f, 120f)) {
            val velocity = plainLyricsVelocity(distance, 240_000, 0, 1f, 60)
            var remaining = distance
            repeat(239) { remaining -= plainLyricsFrameDistance(velocity, 1000f, remaining) }
            kotlin.test.assertTrue(remaining > 0f)
            assertEquals(0f, remaining - plainLyricsFrameDistance(velocity, 1100f, remaining))
        }
    }

    @Test
    fun midSongUsesRemainingTimeAndSpeedScalesIt() {
        assertEquals(0.01f, plainLyricsVelocity(1200f, 240_000, 120_000, 1f, 60))
        assertEquals(0.02f, plainLyricsVelocity(1200f, 240_000, 120_000, 2f, 60))
        assertEquals(0.005f, plainLyricsVelocity(1200f, 240_000, 120_000, 0.5f, 60))
    }

    @Test
    fun missingDurationAndBoundaryPositionsAreSafe() {
        for (duration in listOf(null, 0L, -1L)) {
            assertEquals(0.01f, plainLyricsVelocity(1800f, duration, 0, 1f, 60))
            assertEquals(0.01f, plainLyricsVelocity(1800f, duration, 170_000, 1f, 60))
        }
        assertEquals(0f, plainLyricsVelocity(0f, 1000, 1000, 1f, 60))
        assertEquals(0.1f, plainLyricsVelocity(100f, 1000, 2000, 1f, 60))
        assertEquals(0.1f, plainLyricsVelocity(100f, 1000, -1, 1f, 60))
        assertEquals(12f, plainLyricsFrameDistance(1f, 100f, 12f))
        assertEquals(0f, plainLyricsFrameDistance(1f, 100f, 0f))
        assertEquals(0f, plainLyricsFrameDistance(1f, -1f, 12f))
    }

    @Test
    fun speedIsBoundedAndInvalidValuesUseDefault() {
        assertEquals(0.25f, normalizeAutoScrollSpeed(-1f))
        assertEquals(4f, normalizeAutoScrollSpeed(5f))
        assertEquals(1f, normalizeAutoScrollSpeed(Float.NaN))
        assertEquals(1f, normalizeAutoScrollSpeed(Float.POSITIVE_INFINITY))
    }

    @Test
    fun multiArtistAndRemasteredTracksUseNowPlayingDuration() {
        val meta = SongMeta("Title - Remastered 2011", "Artist A", duration = 240_000)
        assertEquals(240_000L, plainLyricsDuration("Title", "Artist A & Artist B", meta))
        assertEquals(null, plainLyricsDuration("Title", "Unrelated", meta))
    }

    @Test
    fun onlyMatchingNowPlayingMetadataSuppliesDuration() {
        val meta = SongMeta("Title", "Artist", duration = 240_000)
        assertEquals(240_000L, plainLyricsDuration("title", "artist", meta))
        assertEquals(null, plainLyricsDuration("Other", "Artist", meta))
        assertEquals(null, plainLyricsDuration("Title", "Other", meta))
        assertEquals(null, plainLyricsDuration("Title", "Artist", null))
    }
}
