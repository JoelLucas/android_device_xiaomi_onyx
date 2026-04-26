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

class TouchGameModeService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val settingObserver =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                if (DEBUG) Log.d(TAG, "SettingObserver: onChange")
                applyGameMode(this@TouchGameModeService)
            }
        }

    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (DEBUG) Log.d(TAG, "onReceive: ${intent.action}")
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    if (DEBUG) Log.d(TAG, "Screen on, restoring game mode")
                    applyGameMode(this@TouchGameModeService)
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
        applyGameMode(this)
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
        private const val TAG = "TouchGameModeService"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        const val SETTING_KEY = "touch_game_mode"
        private const val DEFAULT_VALUE = 0
        private const val TOUCH_ID = 0

        // Mode numbers from the kernel driver (xiaomi_touch_type_common.h)
        private const val MODE_GAME_MODE = 0          // DATA_MODE_0: main game mode switch
        private const val MODE_HOT_AREA = 220         // DATA_MODE_50: touch exclusion hot area
        private const val MODE_HIGH_SENSITIVITY = 201 // DATA_MODE_43: high sensitivity
        private const val MODE_IDLE_HIGH_BASE = 204   // DATA_MODE_46: idle high baseline enable
        private const val MODE_REPORT_RATE_SEL = 205  // DATA_MODE_47: report rate selector
        private const val MODE_THP_FEATURE = 1084     // DATA_MODE_137: THP feature flag

        // Report rate selector values for MODE_REPORT_RATE_SEL (205):
        // kernel converts: game mode ON + val != 1 → sends 8 to IC
        // game mode ON + val == 1 → sends 16 to IC
        private const val REPORT_RATE_SEL_GAME = 3
        private const val REPORT_RATE_SEL_NORMAL = 0

        fun applyGameMode(context: Context) {
            val enabled =
                Settings.System.getIntForUser(
                    context.contentResolver,
                    SETTING_KEY,
                    DEFAULT_VALUE,
                    UserHandle.USER_CURRENT,
                ) == 1

            if (DEBUG) Log.d(TAG, "applyGameMode: enabled=$enabled")

            if (enabled) {
                // Clear hot area exclusion zones before enabling game mode
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_HOT_AREA, 0)
                // Enable main game mode switch — triggers cmd_update_work in kernel
                // which flushes all DTS-configured game mode parameters to the IC
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_GAME_MODE, 1)
                // Additional THP-layer configuration matching the activation sequence
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_HIGH_SENSITIVITY, 1)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_THP_FEATURE, 1)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_IDLE_HIGH_BASE, 1)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_REPORT_RATE_SEL, REPORT_RATE_SEL_GAME)
            } else {
                // Reverse order: restore report rate and sensitivity first,
                // then disable the main game mode switch last
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_REPORT_RATE_SEL, REPORT_RATE_SEL_NORMAL)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_IDLE_HIGH_BASE, 0)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_THP_FEATURE, 0)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_HIGH_SENSITIVITY, 0)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_GAME_MODE, 0)
                TouchFeatureWrapper.setModeValue(TOUCH_ID, MODE_HOT_AREA, 0)
            }
        }

        fun startService(context: Context) {
            context.startServiceAsUser(
                Intent(context, TouchGameModeService::class.java),
                UserHandle.CURRENT,
            )
        }
    }
}
