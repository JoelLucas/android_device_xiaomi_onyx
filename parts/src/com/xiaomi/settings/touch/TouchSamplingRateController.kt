/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.ServiceManager
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import vendor.xiaomi.hw.touchfeature.ITouchFeature

/**
 * Applies the touch IC's report rate from the touch_sampling_rate setting, independent of
 * any game mode app. 0 = normal, 1 = 240Hz (game mode), 2 = 480Hz (game mode + super
 * report). Re-applied on boot, when the setting changes, when the screen turns on and when
 * the touchfeature HAL restarts, so a reset in the HAL or the driver can't leave it stale.
 */
object TouchSamplingRateController {

    private const val TAG = "TouchSamplingRateController"
    private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

    const val SETTING_KEY = "touch_sampling_rate"

    const val RATE_NORMAL = 0
    const val RATE_240 = 1
    const val RATE_480 = 2

    private const val TOUCH_ID_PRIMARY = 0
    private const val TOUCH_GAME_MODE = 0
    private const val TOUCH_SUPER_REPORT = 202
    private const val RETRY_DELAY_MS = 1000L

    @Volatile private var touchFeature: ITouchFeature? = null
    private lateinit var appContext: Context
    private val handler by lazy { Handler(HandlerThread(TAG).apply { start() }.looper) }

    private val deathRecipient =
        IBinder.DeathRecipient {
            if (DEBUG) Log.d(TAG, "serviceDied")
            touchFeature = null
            handler.post { update(appContext) }
        }

    fun init(context: Context) {
        appContext = context
        val resolver = context.contentResolver
        val observer =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = update(context)
            }
        resolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_KEY),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = update(context)
            },
            IntentFilter(Intent.ACTION_SCREEN_ON),
            null,
            handler,
        )
        handler.post { update(context) }
    }

    private fun update(context: Context, retry: Boolean = true) {
        val rate =
            Settings.System.getIntForUser(
                context.contentResolver,
                SETTING_KEY,
                RATE_NORMAL,
                UserHandle.USER_CURRENT,
            )
        val gameValue = if (rate >= RATE_240) 1 else 0
        val superValue = if (rate >= RATE_480) 1 else 0
        if (DEBUG) Log.d(TAG, "rate=$rate game=$gameValue super=$superValue")
        val touchFeature = getTouchFeature() ?: return
        runCatching {
            touchFeature.setModeValue(TOUCH_ID_PRIMARY, TOUCH_GAME_MODE, gameValue)
            touchFeature.setModeValue(TOUCH_ID_PRIMARY, TOUCH_SUPER_REPORT, superValue)
            touchFeature.getModeCurValue(TOUCH_ID_PRIMARY, TOUCH_SUPER_REPORT)
        }
            .onSuccess { current ->
                Log.i(TAG, "Sampling rate set to $rate, driver super reports $current")
                // A HAL that just came up can drop the first write, so check it took and
                // try once more.
                if (current != superValue && retry) {
                    handler.postDelayed({ update(context, retry = false) }, RETRY_DELAY_MS)
                }
            }
            .onFailure { e -> Log.e(TAG, "setModeValue failed!", e) }
    }

    @Synchronized
    private fun getTouchFeature(): ITouchFeature? =
        touchFeature
            ?: runCatching {
                val binder = Binder.allowBlocking(
                    ServiceManager.waitForDeclaredService("${ITouchFeature.DESCRIPTOR}/default")
                )
                ITouchFeature.Stub.asInterface(binder).apply {
                    asBinder().linkToDeath(deathRecipient, 0)
                }
            }
            .onSuccess { touchFeature = it }
            .onFailure { e -> Log.e(TAG, "getTouchFeature failed!", e) }
            .getOrNull()
}
