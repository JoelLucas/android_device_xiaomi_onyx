/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.os.postDelayed

class RefreshRateService : Service() {

    companion object {
        private const val TAG = "RefreshRateService"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
        private const val HIGH_REFRESH_RATE = 120f
        private const val NORMAL_REFRESH_RATE = 60f
        private const val KEY_MIN_REFRESH_RATE = "min_refresh_rate"
        private const val RESET_DELAY_MS = 100L
    }

    private val handler = Handler()

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (DEBUG) Log.d(TAG, "onReceive: ${intent.action}")
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    // Pulse to 120Hz briefly, then back to 60Hz
                    setRefreshRate(HIGH_REFRESH_RATE)
                    if (DEBUG) Log.d(TAG, "Screen ON, set refresh rate to 120Hz")
                    
                    handler.postDelayed({
                        setRefreshRate(NORMAL_REFRESH_RATE)
                        if (DEBUG) Log.d(TAG, "Reset refresh rate to 60Hz after pulse")
                    }, RESET_DELAY_MS)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) Log.d(TAG, "onCreate")
        val screenStateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, screenStateFilter)
        setRefreshRate(NORMAL_REFRESH_RATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG) Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy")
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(screenStateReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setRefreshRate(rate: Float) {
        if (DEBUG) Log.d(TAG, "setRefreshRate: ${rate}Hz")
        Settings.System.putFloat(
            contentResolver,
            KEY_MIN_REFRESH_RATE,
            rate
        )
    }
}
