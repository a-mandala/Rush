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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shub39.rush.shared.core.enums.Sources
import com.shub39.rush.shared.ui.copyToClipboard
import com.shub39.rush.shared.ui.fadeTopToBottom
import com.shub39.rush.shared.ui.lyrics.LyricsPageAction
import com.shub39.rush.shared.ui.lyrics.LyricsPageState
import com.shub39.rush.shared.ui.lyrics.LyricsState
import com.shub39.rush.shared.ui.lyrics.PlainLyricsScrollRun
import com.shub39.rush.shared.ui.lyrics.PlaybackInfo
import com.shub39.rush.shared.ui.lyrics.TextPrefs
import com.shub39.rush.shared.ui.lyrics.normalizeAutoScrollSpeed
import com.shub39.rush.shared.ui.lyrics.plainLyricsDuration
import com.shub39.rush.shared.ui.lyrics.updateSelectedLines
import com.shub39.rush.shared.ui.toAlignment
import com.shub39.rush.shared.ui.toArrangement
import com.shub39.rush.shared.ui.toTextAlignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import rush.shared.ui.generated.resources.*

@Composable
fun PlainLyrics(
    state: LyricsPageState,
    lazyListState: LazyListState,
    cardContent: Color,
    action: (LyricsPageAction) -> Unit,
    onOpenSearchSheet: () -> Unit,
    modifier: Modifier = Modifier,
    playbackInfo: () -> PlaybackInfo = { PlaybackInfo() },
    controlsAlpha: Float = 1f,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val song = (state.lyricsState as? LyricsState.Loaded)?.song ?: return
    val items = if (state.source == Sources.LRCLIB) song.lyrics else song.geniusLyrics
    val nonEmptyItems = remember(items) { items?.filter { it.value.isNotBlank() } ?: emptyList() }
    val shouldAutoFetch =
        state.source == Sources.GENIUS &&
            nonEmptyItems.isEmpty() &&
            !state.fetchingGenius.first &&
            state.fetchingGenius.second == null

    LaunchedEffect(song.id, state.source, shouldAutoFetch) {
        if (shouldAutoFetch) {
            action(LyricsPageAction.OnFetchGeniusLyrics(song.id, song.sourceUrl))
        }
    }

    var autoScrolling by remember(song.id, state.source, nonEmptyItems) { mutableStateOf(false) }
    val speed = normalizeAutoScrollSpeed(state.prefs.autoScrollSpeed)
    val currentSpeed by rememberUpdatedState(speed)
    val duration =
        remember(song.title, song.artists, state.rush.nowPlaying) {
            plainLyricsDuration(song.title, song.artists, state.rush.nowPlaying)
        }
    val matches =
        remember(song.title, song.artists, state.rush.nowPlaying) {
            plainLyricsDuration(
                song.title,
                song.artists,
                state.rush.nowPlaying?.copy(duration = 1),
            ) != null
        }
    val currentDuration by rememberUpdatedState(duration)
    val currentPlayback by rememberUpdatedState(playbackInfo)
    val followsPlayer by rememberUpdatedState(matches)
    val scrollRun = remember(song.id, state.source) { PlainLyricsScrollRun() }
    val nowPlaying = state.rush.nowPlaying
    // Track changes stop an existing run, including when Rush is disabled.
    LaunchedEffect(nowPlaying?.title, nowPlaying?.artist) { autoScrolling = false }
    var viewportRevision by remember(lazyListState) { mutableStateOf(0) }
    LaunchedEffect(lazyListState) {
        var measuredSize = lazyListState.layoutInfo.viewportSize
        snapshotFlow { lazyListState.layoutInfo.viewportSize }
            .distinctUntilChanged()
            .collectLatest { size ->
                if (size != measuredSize) {
                    // Keep the active frame loop moving through album-art collapse. Only
                    // remeasure after layout has settled, rather than on every animation frame.
                    delay(200)
                    measuredSize = size
                    viewportRevision++
                }
            }
    }
    LaunchedEffect(
        autoScrolling,
        song.id,
        state.source,
        nonEmptyItems,
        viewportRevision,
        state.textPrefs,
        state.romanizationEnabled,
        song.romanizedLyrics,
        song.romanizedGeniusLyrics,
    ) {
        if (autoScrolling) {
            val index = lazyListState.firstVisibleItemIndex
            val offset = lazyListState.firstVisibleItemScrollOffset
            // Measure actual scroll range, including padding and the actions row. These
            // synchronous remeasures complete before the next rendered frame.
            var remaining = 0f
            lazyListState.scroll {
                while (lazyListState.canScrollForward) {
                    val moved =
                        scrollBy(
                            lazyListState.layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
                        )
                    if (moved <= 0f) break
                    remaining += moved
                }
            }
            lazyListState.scrollToItem(index, offset)
            scrollRun.start(
                remaining,
                currentDuration,
                if (followsPlayer) currentPlayback().position else 0L,
                nonEmptyItems.size,
            )
            lazyListState.scroll {
                var previousFrame = withFrameNanos { it }
                while (autoScrolling && scrollRun.running && lazyListState.canScrollForward) {
                    val frame = withFrameNanos { it }
                    val elapsedMillis = (frame - previousFrame) / 1_000_000f
                    previousFrame = frame
                    val moved =
                        scrollBy(
                            scrollRun.frame(
                                elapsedMillis,
                                currentDuration,
                                if (followsPlayer) currentPlayback() else null,
                                currentSpeed,
                            )
                        )
                    scrollRun.consumed(moved, lazyListState.canScrollForward)
                }
            }
            scrollRun.stop()
            autoScrolling = false
        } else {
            scrollRun.stop()
        }
    }

    val userScrollConnection =
        remember(song.id, state.source, nonEmptyItems) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput) autoScrolling = false
                    return Offset.Zero
                }
            }
        }

    Column(modifier = modifier) {
        if (nonEmptyItems.isNotEmpty()) {
            CompositionLocalProvider(LocalContentColor provides cardContent) {
                PlainLyricsAutoScrollControls(
                    modifier = Modifier.alpha(controlsAlpha),
                    playing = autoScrolling,
                    speed = speed,
                    onToggle = {
                        if (
                            !autoScrolling &&
                                scrollRun.restartFromTop(lazyListState.canScrollForward)
                        ) {
                            scope.launch {
                                lazyListState.scrollToItem(0)
                                autoScrolling = true
                            }
                        } else {
                            autoScrolling = !autoScrolling
                        }
                    },
                    onSpeedChange = { action(LyricsPageAction.OnAutoScrollSpeedChange(it)) },
                )
            }
        }
        LazyColumn(
            modifier =
                Modifier.weight(1f)
                    .fadeTopToBottom()
                    .nestedScroll(userScrollConnection)
                    .pointerInput(song.id, state.source, nonEmptyItems) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            autoScrolling = false
                        }
                    },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 64.dp),
            verticalArrangement =
                Arrangement.spacedBy(
                    with(LocalDensity.current) { state.textPrefs.lineHeight.sp.toDp() / 2 }
                ),
            state = lazyListState,
        ) {
            // plain lyrics with logic
            if (nonEmptyItems.isNotEmpty()) {
                items(items = nonEmptyItems, key = { it.key }, contentType = { "lyric" }) {
                    val isSelected = state.selectedLines.contains(it.key)
                    val containerColor by
                        animateColorAsState(
                            targetValue =
                                when (!isSelected) {
                                    true -> Color.Transparent
                                    else -> cardContent.copy(alpha = 0.3f)
                                },
                            label = "container",
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        )

                    PlainLyric(
                        entry = it.toPair(),
                        romanizedText =
                            if (state.romanizationEnabled)
                                if (state.source == Sources.GENIUS)
                                    state.lyricsState.song.romanizedGeniusLyrics[it.key]
                                else state.lyricsState.song.romanizedLyrics[it.key]
                            else null,
                        containerColor = containerColor,
                        textPrefs = state.textPrefs,
                        cardContent = cardContent,
                        onClick = {
                            action(
                                LyricsPageAction.OnChangeSelectedLines(
                                    updateSelectedLines(
                                        state.selectedLines,
                                        it.key,
                                        it.value,
                                        state.maxLines,
                                    )
                                )
                            )

                            if (!isSelected) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                    )
                }
            } else {
                when (state.source) {
                    Sources.GENIUS -> {
                        item(key = "genius_empty", contentType = "empty_state") {
                            AnimatedContent(targetState = state.fetchingGenius) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (it.first) {
                                        CircularProgressIndicator(
                                            color = cardContent,
                                            modifier = Modifier.padding(16.dp),
                                        )

                                        Text(stringResource(Res.string.loading_genius))
                                    } else if (it.second != null) {
                                        Icon(
                                            painter = painterResource(Res.drawable.warning),
                                            contentDescription = null,
                                            modifier = Modifier.size(100.dp),
                                        )

                                        Text(stringResource(it.second!!.errorCode))

                                        TextButton(
                                            onClick = {
                                                action(
                                                    LyricsPageAction.OnFetchGeniusLyrics(
                                                        song.id,
                                                        song.sourceUrl,
                                                    )
                                                )
                                            },
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    contentColor = cardContent,
                                                    containerColor = Color.Transparent,
                                                ),
                                            modifier = Modifier.padding(vertical = 4.dp),
                                        ) {
                                            Text(stringResource(Res.string.retry))
                                        }

                                        it.second?.debugMessage?.let { debug ->
                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        clipboard.copyToClipboard(debug)
                                                    }
                                                },
                                                colors =
                                                    ButtonDefaults.buttonColors(
                                                        contentColor = cardContent,
                                                        containerColor = Color.Transparent,
                                                    ),
                                                modifier = Modifier.padding(vertical = 4.dp),
                                            ) {
                                                Text(stringResource(Res.string.copy_error))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Sources.LRCLIB -> {
                        item(key = "lrclib_empty", contentType = "empty_state") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(modifier = Modifier.padding(10.dp))

                                Icon(
                                    painter = painterResource(Res.drawable.warning),
                                    contentDescription = null,
                                    modifier = Modifier.size(100.dp),
                                )

                                Spacer(modifier = Modifier.padding(10.dp))

                                Text(text = stringResource(Res.string.no_lyrics))
                            }
                        }
                    }
                }
            }

            // Bottom Actions Row
            item(key = "bottom_actions", contentType = "actions") {
                Row(
                    modifier = Modifier.padding(vertical = 100.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(
                        onClick = { scope.launch { lazyListState.scrollToItem(0) } },
                        enabled =
                            if (state.source == Sources.LRCLIB) song.lyrics.isNotEmpty()
                            else !song.geniusLyrics.isNullOrEmpty(),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_warm_up),
                            contentDescription = "Move To Top",
                        )
                    }

                    Button(
                        onClick = { uriHandler.openUri(song.sourceUrl) },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = cardContent.copy(alpha = 0.3f),
                                contentColor = cardContent,
                            ),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.genius),
                            contentDescription = "Genius Source",
                        )
                    }

                    IconButton(onClick = onOpenSearchSheet) {
                        Icon(
                            painter = painterResource(Res.drawable.search),
                            contentDescription = "Search",
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlainLyric(
    textPrefs: TextPrefs,
    entry: Pair<Int, String>,
    romanizedText: String?,
    onClick: () -> Unit,
    containerColor: Color,
    cardContent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = textPrefs.lyricsAlignment.toArrangement(),
    ) {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            colors =
                CardDefaults.cardColors(containerColor = containerColor, contentColor = cardContent),
        ) {
            Column(
                horizontalAlignment = textPrefs.lyricsAlignment.toAlignment(),
                modifier = Modifier.padding(6.dp),
            ) {
                Text(
                    text = entry.second,
                    fontSize = textPrefs.fontSize.sp,
                    letterSpacing = textPrefs.letterSpacing.sp,
                    lineHeight = textPrefs.lineHeight.sp,
                    textAlign = textPrefs.lyricsAlignment.toTextAlignment(),
                    fontWeight = FontWeight.Bold,
                )
                if (!romanizedText.isNullOrBlank()) {
                    Text(
                        text = romanizedText,
                        fontSize = (textPrefs.fontSize * 0.75f).sp,
                        letterSpacing = (textPrefs.letterSpacing * 0.75f).sp,
                        lineHeight = (textPrefs.lineHeight * 0.75f).sp,
                        textAlign = textPrefs.lyricsAlignment.toTextAlignment(),
                        fontWeight = FontWeight.Normal,
                        color = cardContent.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
