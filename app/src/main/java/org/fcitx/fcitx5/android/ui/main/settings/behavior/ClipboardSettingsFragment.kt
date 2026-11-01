/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.Key
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.input.clipboard.ClipboardTriggerKeyConfig
import org.fcitx.fcitx5.android.ui.main.settings.FcitxKeyPreference
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import org.fcitx.fcitx5.android.utils.toast

class ClipboardSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().clipboard) {
    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        super.onPreferenceUiCreated(screen)
        val context = screen.context
        val shortcuts = PreferenceCategory(context).apply {
            title = context.getString(R.string.clipboard_keyboard_shortcuts)
        }
        screen.addPreference(shortcuts)

        val prefs = AppPrefs.getInstance().clipboard
        shortcuts.addPreference(FcitxKeyPreference(context).apply {
            key = prefs.pinShortcut.key
            title = context.getString(R.string.clipboard_pin_shortcut)
            setDefaultValue(prefs.pinShortcut.defaultValue)
            summaryProvider = FcitxKeyPreference.SimpleSummaryProvider
            setOnPreferenceChangeListener { _, newValue ->
                val newKey = newValue as? String ?: return@setOnPreferenceChangeListener false
                val deleteKey = prefs.deleteShortcut.getValue()
                if (sameShortcut(newKey, deleteKey) || conflictsWithNativeTrigger(newKey) ||
                    conflictsWithMenuNavigation(newKey)
                ) {
                    context.toast(R.string.clipboard_shortcut_conflict)
                    false
                } else {
                    true
                }
            }
        })
        shortcuts.addPreference(FcitxKeyPreference(context).apply {
            key = prefs.deleteShortcut.key
            title = context.getString(R.string.clipboard_delete_shortcut)
            setDefaultValue(prefs.deleteShortcut.defaultValue)
            summaryProvider = FcitxKeyPreference.SimpleSummaryProvider
            setOnPreferenceChangeListener { _, newValue ->
                val newKey = newValue as? String ?: return@setOnPreferenceChangeListener false
                val pinKey = prefs.pinShortcut.getValue()
                if (sameShortcut(newKey, pinKey) || conflictsWithNativeTrigger(newKey) ||
                    conflictsWithMenuNavigation(newKey)
                ) {
                    context.toast(R.string.clipboard_shortcut_conflict)
                    false
                } else {
                    true
                }
            }
        })
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

    private fun sameShortcut(left: String, right: String): Boolean {
        val leftKey = runCatching { Key.parse(left) }.getOrNull() ?: return false
        val rightKey = runCatching { Key.parse(right) }.getOrNull() ?: return false
        val mask = KeyState.SimpleMask.state.toInt()
        return leftKey.sym == rightKey.sym &&
            (leftKey.states and mask) == (rightKey.states and mask)
    }

    private fun conflictsWithNativeTrigger(shortcut: String): Boolean =
        ClipboardTriggerKeyConfig.keys().any { sameShortcut(shortcut, it.portableString) }

    private fun conflictsWithMenuNavigation(shortcut: String): Boolean {
        val key = runCatching { Key.parse(shortcut) }.getOrNull() ?: return false
        if ((key.states and KeyState.SimpleMask.state.toInt()) != 0) return false
        val reserved = buildSet {
            addAll(('1'..'9').map(Char::code))
            addAll(listOf('f', 'F', 'q', 'Q', ' ', '[', ']').map(Char::code))
            listOf(
                "Escape", "BackSpace", "Delete", "Return", "KP_Enter", "Up", "Down",
                "Left", "Right", "Page_Up", "Page_Down"
            ).mapNotNullTo(this) { name -> runCatching { Key.parse(name).sym }.getOrNull() }
        }
        return key.sym in reserved
    }
}
