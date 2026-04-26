/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touch

import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.xiaomi.settings.R

class TouchSettingsFragment :
    SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        private const val TAG = "TouchSettingsFragment"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }

    private val switchBar by lazy {
        findPreference<MainSwitchPreference>(TouchReportRateService.SETTING_KEY)!!
    }

    private val gameModeSwitch by lazy {
        findPreference<SwitchPreference>(TouchGameModeService.SETTING_KEY)!!
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (DEBUG) Log.d(TAG, "onCreatePreferences")
        setPreferencesFromResource(R.xml.settings_touch, rootKey)

        val isServiceAvailable = TouchFeatureWrapper.isServiceAvailable()

        val reportRateEnabled =
            Settings.System.getIntForUser(
                requireContext().contentResolver,
                TouchReportRateService.SETTING_KEY,
                0,
                UserHandle.USER_CURRENT,
            ) == 1

        switchBar.isChecked = reportRateEnabled
        switchBar.isEnabled = isServiceAvailable
        switchBar.onPreferenceChangeListener = this

        val gameModeEnabled =
            Settings.System.getIntForUser(
                requireContext().contentResolver,
                TouchGameModeService.SETTING_KEY,
                0,
                UserHandle.USER_CURRENT,
            ) == 1

        gameModeSwitch.isChecked = gameModeEnabled
        gameModeSwitch.isEnabled = isServiceAvailable
        gameModeSwitch.onPreferenceChangeListener = this
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val isChecked = newValue as Boolean
        if (DEBUG) Log.d(TAG, "onPreferenceChange: ${preference.key} = $isChecked")
        when (preference) {
            switchBar ->
                Settings.System.putIntForUser(
                    requireContext().contentResolver,
                    TouchReportRateService.SETTING_KEY,
                    if (isChecked) 1 else 0,
                    UserHandle.USER_CURRENT,
                )
            gameModeSwitch ->
                Settings.System.putIntForUser(
                    requireContext().contentResolver,
                    TouchGameModeService.SETTING_KEY,
                    if (isChecked) 1 else 0,
                    UserHandle.USER_CURRENT,
                )
        }
        return true
    }
}
