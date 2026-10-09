/*
 * Copyright (C) 2025 AABrowser Contributors (https://github.com/kododake/AABrowser)
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
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.kododake.aabrowser.ui.compose.screens.settings.sections

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.ui.compose.components.ExpressiveSlider
import kotlin.math.roundToInt

private const val AUDIO_SYNC_STEP_MS = 10
private const val AUDIO_SYNC_FINE_STEP_MS = 10
private val AUDIO_SYNC_PRESETS_MS = listOf(0, 150, 250, 350, 500)

@Composable
fun AudioSyncSettingsComposable(
    context: Context,
    onAudioSyncChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    var delayMs by remember {
        mutableIntStateOf(BrowserPreferences.getAudioSyncDelayMs(context))
    }

    fun commit(value: Int) {
        val clamped = value.coerceIn(
            BrowserPreferences.MIN_AUDIO_SYNC_DELAY_MS,
            BrowserPreferences.MAX_AUDIO_SYNC_DELAY_MS
        )
        delayMs = clamped
        BrowserPreferences.setAudioSyncDelayMs(context, clamped)
        onAudioSyncChanged()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_audio_sync),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )

        val isDark = isSystemInDarkTheme()
        val cardColor = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cardColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_audio_sync_delay),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (delayMs == 0) {
                            stringResource(R.string.settings_audio_sync_off)
                        } else {
                            stringResource(R.string.settings_audio_sync_value, delayMs)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.settings_audio_sync_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                ExpressiveSlider(
                    value = delayMs.toFloat(),
                    onValueChange = { newFloat ->
                        delayMs = (newFloat / AUDIO_SYNC_STEP_MS).roundToInt() * AUDIO_SYNC_STEP_MS
                    },
                    onValueChangeFinished = { commit(delayMs) },
                    valueRange = BrowserPreferences.MIN_AUDIO_SYNC_DELAY_MS.toFloat()..BrowserPreferences.MAX_AUDIO_SYNC_DELAY_MS.toFloat()
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalButton(onClick = { commit(delayMs - AUDIO_SYNC_FINE_STEP_MS) }) {
                        Text(stringResource(R.string.settings_audio_sync_step_down, AUDIO_SYNC_FINE_STEP_MS))
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(onClick = { commit(delayMs + AUDIO_SYNC_FINE_STEP_MS) }) {
                        Text(stringResource(R.string.settings_audio_sync_step_up, AUDIO_SYNC_FINE_STEP_MS))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AUDIO_SYNC_PRESETS_MS.forEach { preset ->
                        val isSelected = delayMs == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { commit(preset) },
                            label = {
                                Text(
                                    if (preset == 0) {
                                        stringResource(R.string.settings_audio_sync_off)
                                    } else {
                                        stringResource(R.string.settings_audio_sync_value, preset)
                                    }
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}
