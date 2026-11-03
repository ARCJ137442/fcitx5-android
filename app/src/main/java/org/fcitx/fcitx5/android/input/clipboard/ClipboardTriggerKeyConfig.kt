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

    data class Bindings(
        val triggers: List<Key> = listOf(Key.parse("Control+semicolon")),
        val favoritesFilter: List<Key> = listOf(Key.parse("f")),
        val pinToggle: List<Key> = listOf(Key.parse("d")),
        val deleteEntry: List<Key> = listOf(Key.parse("x")),
        val closeMenu: List<Key> = listOf(Key.parse("q")),
        val clearHistory: List<Key> = listOf(Key.parse("BackSpace"), Key.parse("Delete")),
        val previousPage: List<Key> = listOf(Key.parse("bracketleft"), Key.parse("Page_Up")),
        val nextPage: List<Key> = listOf(Key.parse("bracketright"), Key.parse("Page_Down"))
    )

    @Volatile
    private var configured = Bindings()

    fun bindings(): Bindings = configured

    fun keys(): List<Key> = configured.triggers

    suspend fun reload(fcitx: FcitxAPI) {
        update(fcitx.getAddonConfig(AddonName))
    }

    fun update(addon: String, raw: RawConfig) {
        if (addon == AddonName) update(raw)
    }

    fun matches(event: KeyEvent): Boolean = matches(event, configured.triggers)

    fun matchesFavoritesFilter(event: KeyEvent): Boolean =
        matches(event, configured.favoritesFilter)

    fun matchesPinToggle(event: KeyEvent): Boolean = matches(event, configured.pinToggle)

    fun matchesDeleteEntry(event: KeyEvent): Boolean = matches(event, configured.deleteEntry)

    fun matchesCloseMenu(event: KeyEvent): Boolean = matches(event, configured.closeMenu)

    fun matchesClearHistory(event: KeyEvent): Boolean =
        matches(event, configured.clearHistory)

    fun matchesPreviousPage(event: KeyEvent): Boolean =
        matches(event, configured.previousPage)

    fun matchesNextPage(event: KeyEvent): Boolean = matches(event, configured.nextPage)

    fun localizedString(keys: List<Key>, fallback: String): String =
        keys.mapNotNull { it.localizedString.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString("/")
            .ifEmpty { fallback }

    private fun matches(event: KeyEvent, keys: List<Key>): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        val sym = KeySym.fromKeyEvent(event)?.sym ?: return false
        val states = KeyStates.fromKeyEvent(event).states and KeyState.SimpleMask.state
        return keys.any { key ->
            key.sym == sym &&
                (key.states.toUInt() and KeyState.SimpleMask.state) == states
        }
    }

    private fun update(raw: RawConfig) {
        val config = raw.findByName("cfg") ?: raw
        val defaults = Bindings()
        configured = Bindings(
            triggers = readKeys(config, "TriggerKey", defaults.triggers),
            favoritesFilter = readKeys(
                config, "FavoritesFilterKey", defaults.favoritesFilter
            ),
            pinToggle = readKeys(config, "PinToggleKey", defaults.pinToggle),
            deleteEntry = readKeys(config, "DeleteEntryKey", defaults.deleteEntry),
            closeMenu = readKeys(config, "CloseMenuKey", defaults.closeMenu),
            clearHistory = readKeys(config, "ClearHistoryKey", defaults.clearHistory),
            previousPage = readKeys(config, "PreviousPageKey", defaults.previousPage),
            nextPage = readKeys(config, "NextPageKey", defaults.nextPage)
        )
    }

    private fun readKeys(config: RawConfig, name: String, default: List<Key>): List<Key> {
        val option = config.findByName(name) ?: return default
        val values = option.subItems?.map { it.value }
            ?: listOf(option.value).filter { it.isNotBlank() }
        return values.mapNotNull { value ->
            runCatching { Key.parse(value) }.getOrNull()
        }.ifEmpty {
            default
        }
    }
}
