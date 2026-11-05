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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FitAutoScrollInterplayTest {
    @Test
    fun largerThanTheMinimumAlwaysFitsEntirely() {
        val minimumChecks = mutableListOf<Float>()
        val result =
            fitFontSize(min = 14f, max = 20f, step = 1f) {
                minimumChecks += it
                it <= 18f
            }

        assertEquals(FittedFontSize(18f, fitsEntirely = true), result)
        assertFalse(14f in minimumChecks)
    }

    @Test
    fun theMinimumFitsEntirelyOnlyIfItWasChecked() {
        assertEquals(
            FittedFontSize(14f, fitsEntirely = true),
            fitFontSize(min = 14f, max = 20f, step = 1f) { it <= 14f },
        )
        assertEquals(
            FittedFontSize(14f, fitsEntirely = false),
            fitFontSize(min = 14f, max = 20f, step = 1f) { false },
        )
    }

    @Test
    fun hiddenOnlyWhenFittedLyricsFitEntirely() {
        assertFalse(
            plainLyricsAutoScrollAvailable(
                hasLyrics = true,
                fitToScreen = true,
                fitsEntirely = true,
            )
        )
        assertTrue(
            plainLyricsAutoScrollAvailable(
                hasLyrics = true,
                fitToScreen = true,
                fitsEntirely = false,
            )
        )
        assertTrue(
            plainLyricsAutoScrollAvailable(
                hasLyrics = true,
                fitToScreen = false,
                fitsEntirely = false,
            )
        )
    }

    @Test
    fun hiddenWithoutLyrics() {
        assertFalse(
            plainLyricsAutoScrollAvailable(
                hasLyrics = false,
                fitToScreen = false,
                fitsEntirely = false,
            )
        )
    }
}
