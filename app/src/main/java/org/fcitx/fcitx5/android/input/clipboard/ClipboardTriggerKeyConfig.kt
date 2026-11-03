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
import timber.log.Timber

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
        val favoritesTriggers: List<Key> = listOf(Key.parse("Alt+Shift+v")),
        val showShortcutHints: Boolean = true,
        val favoritesFilter: List<Key> = listOf(Key.parse("f")),
        val pinToggle: List<Key> = listOf(Key.parse("d")),
        val deleteEntry: List<Key> = listOf(Key.parse("x")),
        val closeMenu: List<Key> = listOf(Key.parse("q")),
        val nativeResetKey: List<Key> = listOf(Key.parse("Escape")),
        val clearHistory: List<Key> = listOf(Key.parse("BackSpace"), Key.parse("Delete")),
        val selectionKeys: List<Key> = listOf(
            Key.parse("1"), Key.parse("2"), Key.parse("3"), Key.parse("4"), Key.parse("5"),
            Key.parse("6"), Key.parse("7"), Key.parse("8"), Key.parse("9"), Key.parse("0")
        ),
        val pasteSelectedKey: List<Key> =
            listOf(Key.parse("space"), Key.parse("Return"), Key.parse("KP_Enter")),
        val previousCandidateKey: List<Key> = listOf(Key.parse("Up")),
        val nextCandidateKey: List<Key> = listOf(Key.parse("Down")),
        val movePinnedUpKey: List<Key> = listOf(Key.parse("Control+Up")),
        val movePinnedDownKey: List<Key> = listOf(Key.parse("Control+Down")),
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

    fun matchesFavoritesTrigger(event: KeyEvent): Boolean =
        matches(event, configured.favoritesTriggers)

    fun showShortcutHints(): Boolean = configured.showShortcutHints

    fun matchesFavoritesFilter(event: KeyEvent): Boolean =
        matches(event, configured.favoritesFilter)

    fun matchesPinToggle(event: KeyEvent): Boolean = matches(event, configured.pinToggle)

    fun matchesDeleteEntry(event: KeyEvent): Boolean = matches(event, configured.deleteEntry)

    fun matchesCloseMenu(event: KeyEvent): Boolean = matches(event, configured.closeMenu)

    fun matchesNativeReset(event: KeyEvent): Boolean = matches(event, configured.nativeResetKey)

    fun matchesClearHistory(event: KeyEvent): Boolean =
        matches(event, configured.clearHistory)

    fun selectionIndex(event: KeyEvent): Int? {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return null
        return configured.selectionKeys.indexOfFirst { matchesKey(event, it) }.takeIf { it >= 0 }
    }

    fun matchesPasteSelected(event: KeyEvent): Boolean =
        matches(event, configured.pasteSelectedKey)

    fun matchesPreviousCandidate(event: KeyEvent): Boolean =
        matches(event, configured.previousCandidateKey)

    fun matchesNextCandidate(event: KeyEvent): Boolean =
        matches(event, configured.nextCandidateKey)

    fun matchesMovePinnedUp(event: KeyEvent): Boolean =
        matches(event, configured.movePinnedUpKey)

    fun matchesMovePinnedDown(event: KeyEvent): Boolean =
        matches(event, configured.movePinnedDownKey)

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
        return keys.any { matchesKey(event, it) }
    }

    private fun matchesKey(event: KeyEvent, key: Key): Boolean {
        val sym = KeySym.fromKeyEvent(event)?.sym ?: return false
        val states = KeyStates.fromKeyEvent(event).states and KeyState.SimpleMask.state
        return key.sym == sym &&
            (key.states.toUInt() and KeyState.SimpleMask.state) == states
    }

    private fun update(raw: RawConfig) {
        val config = raw.findByName("cfg") ?: raw
        val defaults = Bindings()
        val favoritesTriggers = readKeys(
            config, "FavoritesTriggerKey", defaults.favoritesTriggers
        )
        configured = Bindings(
            triggers = readKeys(config, "TriggerKey", defaults.triggers)
                .filterNot { trigger ->
                    favoritesTriggers.any { favorite -> sameKey(trigger, favorite) }
                },
            favoritesTriggers = favoritesTriggers,
            showShortcutHints = readBoolean(
                config, "ShowShortcutHints", defaults.showShortcutHints
            ),
            favoritesFilter = readKeys(
                config, "FavoritesFilterKey", defaults.favoritesFilter
            ),
            pinToggle = readKeys(config, "PinToggleKey", defaults.pinToggle),
            deleteEntry = readKeys(config, "DeleteEntryKey", defaults.deleteEntry),
            closeMenu = readKeys(config, "CloseMenuKey", defaults.closeMenu),
            nativeResetKey = readKeys(config, "NativeResetKey", defaults.nativeResetKey),
            clearHistory = readKeys(config, "ClearHistoryKey", defaults.clearHistory),
            selectionKeys = readKeys(config, "SelectionKeys", defaults.selectionKeys),
            pasteSelectedKey = readKeys(
                config, "PasteSelectedKey", defaults.pasteSelectedKey
            ),
            previousCandidateKey = readKeys(
                config, "PreviousCandidateKey", defaults.previousCandidateKey
            ),
            nextCandidateKey = readKeys(
                config, "NextCandidateKey", defaults.nextCandidateKey
            ),
            movePinnedUpKey = readKeys(
                config, "MovePinnedUpKey", defaults.movePinnedUpKey
            ),
            movePinnedDownKey = readKeys(
                config, "MovePinnedDownKey", defaults.movePinnedDownKey
            ),
            previousPage = readKeys(config, "PreviousPageKey", defaults.previousPage),
            nextPage = readKeys(config, "NextPageKey", defaults.nextPage)
        )
        Timber.d(
            "Clipboard config: showShortcutHints=${configured.showShortcutHints}" +
                " rawShowShortcutHints=${config.findByName("ShowShortcutHints")?.value}" +
                " favoritesTriggers=${configured.favoritesTriggers}" +
                " rawFavoritesTriggers=${config.findByName("FavoritesTriggerKey")?.subItems?.map { it.value }}"
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

    private fun readBoolean(config: RawConfig, name: String, default: Boolean): Boolean {
        val value = config.findByName(name)?.value?.trim()?.takeIf(String::isNotEmpty)
            ?: return default
        return value.equals("True", ignoreCase = true) || value == "1"
    }

    private fun sameKey(left: Key, right: Key): Boolean {
        val mask = KeyState.SimpleMask.state.toUInt()
        return left.sym == right.sym && (left.states.toUInt() and mask) ==
            (right.states.toUInt() and mask)
    }
}
