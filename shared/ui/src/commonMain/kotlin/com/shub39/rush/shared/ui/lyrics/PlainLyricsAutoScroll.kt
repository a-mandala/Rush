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

import com.shub39.rush.shared.core.dataclasses.SearchResult
import com.shub39.rush.shared.core.dataclasses.SongMeta
import com.shub39.rush.shared.core.matching.SongMatcher

/**
 * Whether plain lyrics offer auto-scroll: not without lyrics, and not when fit to screen shows all
 * of them, as there is nothing to scroll then. Fitted lyrics that overflow at the minimum size
 * scroll like any others.
 */
fun plainLyricsAutoScrollAvailable(
    hasLyrics: Boolean,
    fitToScreen: Boolean,
    fitsEntirely: Boolean,
): Boolean = hasLyrics && !(fitToScreen && fitsEntirely)

fun normalizeAutoScrollSpeed(speed: Float): Float =
    if (speed.isFinite()) speed.coerceIn(0.5f, 2f) else 1f

/** A fixed pixel velocity avoids pulses at boundaries between differently sized lyrics. */
fun plainLyricsVelocity(
    distance: Float,
    durationMillis: Long?,
    positionMillis: Long,
    speed: Float,
): Float {
    val duration = durationMillis?.takeIf { it > 0 } ?: 180_000L
    val position =
        if (durationMillis != null && durationMillis > 0 && positionMillis in 0 until duration)
            positionMillis
        else 0L
    // Limit catch-up to twice the normal pace; stale end positions use the full duration.
    val remaining = (duration - position).coerceAtLeast(duration / 2).coerceAtLeast(1)
    return distance.coerceAtLeast(0f) / remaining * normalizeAutoScrollSpeed(speed)
}

/** Clamp the final frame to the measured end, even after a delayed frame. */
fun plainLyricsFrameDistance(velocity: Float, elapsedMillis: Float, remaining: Float): Float =
    (velocity * elapsedMillis.coerceAtLeast(0f)).coerceIn(0f, remaining.coerceAtLeast(0f))

/** Media duration is in milliseconds; use the same matching policy as rush mode. */
fun plainLyricsDuration(title: String, artists: String, nowPlaying: SongMeta?): Long? =
    nowPlaying
        ?.takeIf {
            SongMatcher.score(it, SearchResult(0, title, artists, null, "", "")) >=
                SongMatcher.MIN_SCORE
        }
        ?.duration
        ?.takeIf { it > 0 }

/** Owns pacing and completion decisions independently of Compose. */
class PlainLyricsScrollRun {
    var remaining = 0f
        private set

    var running = false
        private set

    private var velocity = 0f
    private var hasVelocity = false
    private var duration: Long? = null
    private var previousPosition = 0L
    private var previousSpeed = 0f

    fun start(distance: Float, durationMillis: Long?, startingPosition: Long) {
        remaining = distance.coerceAtLeast(0f)
        running = remaining > 0f
        duration = durationMillis
        previousPosition = startingPosition
        // Unknown-duration restarts retain the original pace across pauses and remeasurement.
        if (!hasVelocity || durationMillis != null && durationMillis > 0) {
            velocity = plainLyricsVelocity(remaining, duration, startingPosition, 1f)
            hasVelocity = remaining > 0f
        }
        previousSpeed = 0f
    }

    fun frame(
        elapsed: Float,
        durationMillis: Long?,
        playback: PlaybackInfo?,
        currentSpeed: Float,
    ): Float {
        val position = playback?.position ?: 0L
        if (
            duration != durationMillis ||
                (playback != null &&
                    kotlin.math.abs(position - previousPosition - elapsed * previousSpeed) > 1500f)
        ) {
            duration = durationMillis
            if (durationMillis != null && durationMillis > 0) {
                velocity = plainLyricsVelocity(remaining, duration, position, 1f)
            }
        }
        previousPosition = position
        previousSpeed = playback?.speed ?: 1f
        if (!running || playback?.speed == 0f) return 0f
        // LazyList rounds pixel consumption; finish the final pixel before declaring completion.
        if (remaining <= 1f && elapsed > 0f) return 1f
        return plainLyricsFrameDistance(
            velocity * normalizeAutoScrollSpeed(currentSpeed) * (playback?.speed ?: 1f),
            elapsed,
            remaining,
        )
    }

    fun consumed(distance: Float, canScrollForward: Boolean) {
        remaining = (remaining - distance).coerceAtLeast(0f)
        if (!canScrollForward) stop()
    }

    fun stop() {
        running = false
    }

    fun restartFromTop(canScrollForward: Boolean) = !running && !canScrollForward
}
