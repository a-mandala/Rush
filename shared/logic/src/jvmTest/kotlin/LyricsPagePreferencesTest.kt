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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.shub39.rush.shared.logic.datastore.LyricsPagePreferencesImpl
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath

class LyricsPagePreferencesTest {
    @Test
    fun autoScrollSpeedSurvivesReopeningAndResetsToDefault() = runBlocking {
        val directory =
            Files.createTempDirectory(Path.of(System.getProperty("user.dir")), ".auto-scroll-test")
        val file = directory.resolve("lyrics.preferences_pb").toString().toPath()
        var job = SupervisorJob()
        fun open() =
            LyricsPagePreferencesImpl(
                PreferenceDataStoreFactory.createWithPath(
                    scope = CoroutineScope(Dispatchers.IO + job),
                    produceFile = { file },
                )
            )
        try {
            val preferences = open()
            assertEquals(1f, preferences.getPreferencesFlow().first().autoScrollSpeed)
            preferences.updateAutoScrollSpeed(1.5f)
            job.cancelAndJoin()
            job = SupervisorJob()
            val reopened = open()
            assertEquals(1.5f, reopened.getPreferencesFlow().first().autoScrollSpeed)
            reopened.reset()
            assertEquals(1f, reopened.getPreferencesFlow().first().autoScrollSpeed)
        } finally {
            job.cancelAndJoin()
            directory.toFile().deleteRecursively()
        }
    }
}
