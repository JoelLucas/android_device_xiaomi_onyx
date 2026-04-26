/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touch

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.Log

class TouchReportRateService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val settingObserver =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                if (DEBUG) Log.d(TAG, "SettingObserver: onChange")
                applyReportRate(this@TouchReportRateService)
            }
        }

    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (DEBUG) Log.d(TAG, "onReceive: ${intent.action}")
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    if (DEBUG) Log.d(TAG, "Screen on, restoring touch report rate")
                    applyReportRate(this@TouchReportRateService)
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) Log.d(TAG, "onCreate")
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_KEY),
            false,
            settingObserver,
            UserHandle.USER_CURRENT,
        )
        registerReceiver(screenStateReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        applyReportRate(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG) Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy")
        contentResolver.unregisterContentObserver(settingObserver)
        unregisterReceiver(screenStateReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "TouchReportRateService"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        const val SETTING_KEY = "touch_high_sampling_rate"
        private const val DEFAULT_VALUE = 0
        private const val TOUCH_ID = 0

        // Mode numbers matching the kernel driver sequence from the logs
        private const val MODE_GAME_MODE = 0           // DATA_MODE_0: main game mode switch — triggers
                                                       // cmd_update_work which enables 500Hz+ IC scan rate
        private const val MODE_HOT_AREA = 220          // DATA_MODE_50: touch exclusion hot area
        private const val MODE_REPORT_RATE = 1011      // DATA_MODE_64: display rate notification
        private const val MODE_REPORT_RATE_COMPANION = 1012 // DATA_MODE_65: companion parameter
        private const val MODE_HIGH_SENSITIVITY = 201  // DATA_MODE_43: high sensitivity
        private const val MODE_IDLE_HIGH_BASE = 204    // DATA_MODE_46: idle high baseline enable
        private const val MODE_REPORT_RATE_SEL = 205   // DATA_MODE_47: rate selector (kernel requires
                                                       // game mode ON to honour this; val!=1 → sends 8 to IC)
        private const val MODE_THP_FEATURE = 1084      // DATA_MODE_137: THP feature flag

        private const val RATE_NORMAL = 120
        private const val RATE_HIGH = 240
        // Fixed companion value for this panel (120Hz base = 119)
        private const val RATE_COMPANION = 119

        fun isReportRateWritable(): Boolean = TouchFeatureWrapper.isServiceAvailable()

        fun applyReportRate(context: Context) {
            val value =
                Settings.System.getIntForUser(
                    context.contentResolver,
                    SETTING_KEY,
                    DEFAULT_VALUE,
                    UserHandle.USER_CURRENT,
                )
            if (DEBUG) Log.d(TAG, "applyReportRate: $value")

            if (value == 1) {
                // Clear hot area before enabling game mode (matches observed log order)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_HOT_AREA, 0)
                // Game mode ON — this is what actually enables 500Hz+ IC scanning via
                // cmd_update_work → nvt_enable_game_mode. Without it, MODE_REPORT_RATE_SEL
                // is ignored by the kernel and clamped to val=1.
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_GAME_MODE, 1)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_REPORT_RATE, RATE_HIGH)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_REPORT_RATE_COMPANION, RATE_COMPANION)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_HIGH_SENSITIVITY, 1)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_IDLE_HIGH_BASE, 1)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_THP_FEATURE, 1)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_REPORT_RATE_SEL, 3)
            } else {
                // Restore in reverse: tear down selectors/flags first, then disable game mode
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_REPORT_RATE_SEL, 0)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_THP_FEATURE, 0)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_IDLE_HIGH_BASE, 0)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_HIGH_SENSITIVITY, 0)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_GAME_MODE, 0)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_REPORT_RATE, RATE_NORMAL)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_REPORT_RATE_COMPANION, RATE_COMPANION)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_HOT_AREA, 0)
            }
        }

        fun startService(context: Context) {
            context.startServiceAsUser(
                Intent(context, TouchReportRateService::class.java),
                UserHandle.CURRENT,
            )
        }
    }
}
