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
package com.shub39.rush.shared.ui.lyrics.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.shub39.rush.shared.ui.RushPreviewWrapper
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import rush.shared.ui.generated.resources.*

@Composable
fun PlainLyricsAutoScrollControls(
    playing: Boolean,
    speed: Float,
    onToggle: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedLabel = stringResource(Res.string.auto_scroll_speed)
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                painterResource(if (playing) Res.drawable.pause else Res.drawable.play),
                contentDescription =
                    stringResource(
                        if (playing) Res.string.pause_auto_scroll else Res.string.start_auto_scroll
                    ),
            )
        }
        Slider(
            value = speed,
            onValueChange = onSpeedChange,
            valueRange = 0.5f..2f,
            steps = 5,
            modifier = Modifier.weight(1f).semantics { contentDescription = speedLabel },
        )
        Text(
            text = "${kotlin.math.round(speed * 100) / 100}×",
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Preview
@PreviewWrapper(RushPreviewWrapper::class)
@Composable
private fun PlainLyricsAutoScrollControlsPreview() {
    PlainLyricsAutoScrollControls(playing = false, speed = 1f, onToggle = {}, onSpeedChange = {})
}
