/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.addon

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.RawConfig
import org.fcitx.fcitx5.android.input.clipboard.ClipboardTriggerKeyConfig
import org.fcitx.fcitx5.android.ui.main.settings.FcitxPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.lazyRoute

class AddonConfigFragment : FcitxPreferenceFragment() {
    private val args by lazyRoute<SettingsRoute.AddonConfig>()

    override fun visibleKeyConfigNames(): Set<String> =
        if (args.uniqueName == "clipboard") {
            setOf(
                "TriggerKey",
                "FavoritesTriggerKey",
                "FavoritesFilterKey",
                "PinToggleKey",
                "DeleteEntryKey",
                "CloseMenuKey",
                "NativeResetKey",
                "ClearHistoryKey",
                "SelectionKeys",
                "PasteSelectedKey",
                "PreviousCandidateKey",
                "NextCandidateKey",
                "MovePinnedUpKey",
                "MovePinnedDownKey",
                "PreviousPageKey",
                "NextPageKey"
            )
        } else {
            emptySet()
        }

    override fun getPageTitle(): String = args.name

    override suspend fun obtainConfig(fcitx: FcitxAPI): RawConfig {
        val addon = args.uniqueName
        val raw = fcitx.getAddonConfig(addon)
        if (addon == "table") {
            // append android specific "Manage Table Input Methods" to config of table addon
            raw.findByName("desc")?.findByName("TableGlobalConfig")?.let {
                it.subItems = (it.subItems ?: emptyArray()) + RawConfig(
                    "AndroidTable", subItems = arrayOf(
                        RawConfig("Type", "External"),
                        RawConfig("Description", getString(R.string.manage_table_im))
                    )
                )
            }
        }
        return raw
    }

    override suspend fun saveConfig(fcitx: FcitxAPI, newConfig: RawConfig) {
        fcitx.setAddonConfig(args.uniqueName, newConfig)
        ClipboardTriggerKeyConfig.update(args.uniqueName, newConfig)
    }
}
