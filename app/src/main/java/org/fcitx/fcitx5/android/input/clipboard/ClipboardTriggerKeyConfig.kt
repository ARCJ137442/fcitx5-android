/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import android.view.KeyEvent
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.Key
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.core.RawConfig

/**
 * Runtime view of the native clipboard addon's key list.
 *
 * The addon config remains the source of truth; this cache only lets the IME
 * compare hardware events synchronously before forwarding them to Fcitx.
 */
object ClipboardTriggerKeyConfig {
    private const val AddonName = "clipboard"
    private const val OptionName = "TriggerKey"

    @Volatile
    private var configuredKeys = listOf(Key.parse("Control+semicolon"))

    fun keys(): List<Key> = configuredKeys

    suspend fun reload(fcitx: FcitxAPI) {
        update(fcitx.getAddonConfig(AddonName))
    }

    fun update(addon: String, raw: RawConfig) {
        if (addon == AddonName) update(raw)
    }

    fun matches(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        val sym = KeySym.fromKeyEvent(event)?.sym ?: return false
        val states = KeyStates.fromKeyEvent(event).states and KeyState.SimpleMask.state
        return configuredKeys.any { key ->
            key.sym == sym &&
                (key.states.toUInt() and KeyState.SimpleMask.state) == states
        }
    }

    private fun update(raw: RawConfig) {
        val config = raw.findByName("cfg") ?: raw
        val option = config.findByName(OptionName) ?: return
        val values = option.subItems?.map { it.value }
            ?: listOf(option.value).filter { it.isNotBlank() }
        configuredKeys = values.mapNotNull { value ->
            runCatching { Key.parse(value) }.getOrNull()
        }
    }
}
