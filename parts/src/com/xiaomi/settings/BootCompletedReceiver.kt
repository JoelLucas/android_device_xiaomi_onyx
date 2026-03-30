/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xiaomi.settings.touch.TouchReportRateService

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (DEBUG) Log.d(TAG, "Received boot completed intent: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> startTouchService(context)
        }
    }

    private fun startTouchService(context: Context) {
        // Touch
        TouchReportRateService.startService(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }
}
