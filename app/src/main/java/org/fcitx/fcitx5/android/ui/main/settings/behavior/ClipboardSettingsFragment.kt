/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim

class ClipboardSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().clipboard) {
    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        super.onPreferenceUiCreated(screen)
        val context = screen.context
        val shortcuts = PreferenceCategory(context).apply {
            title = context.getString(R.string.clipboard_keyboard_shortcuts)
        }
        screen.addPreference(shortcuts)

        shortcuts.addPreference(Preference(context).apply {
            title = context.getString(R.string.clipboard_native_addon_settings)
            summary = context.getString(R.string.clipboard_native_addon_settings_summary)
            setOnPreferenceClickListener {
                navigateWithAnim(
                    SettingsRoute.AddonConfig(
                        context.getString(R.string.clipboard_native_addon_settings),
                        "clipboard"
                    )
                )
                true
            }
        })
    }
}
