/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touch

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.xiaomi.settings.R

/**
 * Quick Settings tile that cycles the standalone touch sampling rate:
 * Normal -> 240Hz -> 480Hz. TouchSamplingRateController observes the setting and applies it
 * to the touch IC, so the tile only has to store the choice.
 */
class TouchSamplingRateTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val next = (getRate() + 1) % 3
        Settings.System.putInt(contentResolver, TouchSamplingRateController.SETTING_KEY, next)
        updateTile()
    }

    private fun getRate(): Int =
        Settings.System.getInt(
            contentResolver,
            TouchSamplingRateController.SETTING_KEY,
            TouchSamplingRateController.RATE_NORMAL,
        )

    private fun updateTile() {
        val rate = getRate()
        qsTile?.apply {
            state =
                if (rate == TouchSamplingRateController.RATE_NORMAL) Tile.STATE_INACTIVE
                else Tile.STATE_ACTIVE
            label = getString(R.string.touch_sampling_rate_tile_label)
            subtitle =
                getString(
                    when (rate) {
                        TouchSamplingRateController.RATE_240 -> R.string.touch_sampling_rate_240
                        TouchSamplingRateController.RATE_480 -> R.string.touch_sampling_rate_480
                        else -> R.string.touch_sampling_rate_normal
                    }
                )
            updateTile()
        }
    }
}
