/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp

/**
 * A keyboard-driven Room favorites submenu hosted beside CandidatesView.
 * Key dispatch stays in FcitxInputMethodService, so this panel never steals editor focus.
 */
class ClipboardFavoritesOverlay(
    context: Context,
    private val theme: Theme,
    private val entries: List<ClipboardEntry>,
    page: Int,
    pageCount: Int,
    maskSensitive: Boolean,
    selectedEntryId: Int? = null,
    selectedIndex: Int = 0,
    private val onSelect: (ClipboardEntry) -> Unit,
    private val onDismiss: () -> Unit,
    private val emptyMessage: String? = null
) : FrameLayout(context) {

    private data class Row(
        val root: LinearLayout,
        val number: TextView,
        val entry: ClipboardEntry,
        val entryView: ClipboardEntryUi
    )

    private val scrollView = ScrollView(context)
    private val rows = mutableListOf<Row>()
    private var selectedIndex = selectedIndex.coerceAtLeast(0)

    val currentSelectionIndex: Int
        get() = selectedIndex

    val currentSelection: ClipboardEntry?
        get() = rows.getOrNull(selectedIndex)?.entry

    init {
        isClickable = true
        setBackgroundColor(Color.argb(128, 0, 0, 0))
        setOnClickListener { onDismiss() }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
            background = roundedDrawable(theme.popupBackgroundColor, theme.popupBackgroundColor)
            elevation = context.dp(12).toFloat()
            setOnClickListener { /* Consume taps inside the panel. */ }
        }

        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBar.addView(TextView(context).apply {
            text = context.getString(R.string.clipboard_favorites_title)
            textSize = 18f
            setTextColor(theme.popupTextColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (pageCount > 1) {
            titleBar.addView(TextView(context).apply {
                text = context.getString(R.string.clipboard_favorites_page, page + 1, pageCount)
                textSize = 13f
                setTextColor(theme.popupTextColor)
            })
        }
        panel.addView(titleBar)

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (entries.isEmpty()) {
            list.addView(TextView(context).apply {
                text = emptyMessage ?: context.getString(R.string.clipboard_favorites_empty)
                textSize = 15f
                setTextColor(theme.popupTextColor)
                gravity = Gravity.CENTER
                setPadding(context.dp(12), context.dp(22), context.dp(12), context.dp(22))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else {
            entries.forEachIndexed { index, entry ->
                val number = TextView(context).apply {
                    text = (index + 1).toString()
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(theme.popupTextColor)
                    contentDescription = (index + 1).toString()
                }
                val entryView = ClipboardEntryUi(context, theme, context.dp(7).toFloat()).apply {
                    val excerpt = ClipboardAdapter.excerptText(
                        entry.text,
                        entry.sensitive && maskSensitive,
                        lines = 3,
                        chars = 72
                    )
                    setEntry(excerpt, entry.pinned)
                    root.contentDescription = context.getString(
                        R.string.clipboard_favorites_entry_description,
                        index + 1,
                        excerpt
                    )
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(context.dp(3), context.dp(3), context.dp(3), context.dp(3))
                    isClickable = true
                    setOnClickListener { onSelect(entry) }
                }
                row.addView(
                    number,
                    LinearLayout.LayoutParams(context.dp(42), context.dp(48)).apply {
                        marginEnd = context.dp(8)
                    }
                )
                row.addView(entryView.root, LinearLayout.LayoutParams(0, context.dp(54), 1f))
                entryView.root.setOnClickListener { onSelect(entry) }
                rows += Row(row, number, entry, entryView)
                list.addView(
                    row,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = context.dp(5)
                    }
                )
            }
        }

        if (entries.isNotEmpty()) {
            val restoredIndex = selectedEntryId?.let { id ->
                entries.indexOfFirst { it.id == id }.takeIf { it >= 0 }
            }
            this.selectedIndex = (restoredIndex ?: this.selectedIndex).coerceIn(0, entries.lastIndex)
        } else {
            this.selectedIndex = 0
        }

        val desiredListHeight = if (entries.isEmpty()) context.dp(82) else entries.size * context.dp(62)
        val maxListHeight = minOf(context.dp(430), (context.resources.displayMetrics.heightPixels * 0.62f).toInt())
        val listHeight = minOf(desiredListHeight, maxListHeight).coerceAtLeast(context.dp(72))
        scrollView.addView(
            list,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight)
        )
        panel.addView(TextView(context).apply {
            text = context.getString(R.string.clipboard_favorites_hint)
            textSize = 12f
            setTextColor(theme.popupTextColor)
            setPadding(context.dp(4), context.dp(8), context.dp(4), context.dp(2))
        })

        val availableWidth = (context.resources.displayMetrics.widthPixels - context.dp(36))
            .coerceAtLeast(context.dp(260))
        val panelWidth = minOf(availableWidth, context.dp(620))
        addView(
            panel,
            FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        updateSelection()
        scrollView.post {
            rows.getOrNull(selectedIndex)?.let { scrollView.scrollTo(0, it.root.top) }
        }
    }

    fun moveSelection(delta: Int) {
        if (rows.isEmpty()) return
        selectedIndex = (selectedIndex + delta).coerceIn(0, rows.lastIndex)
        updateSelection()
        scrollView.post { scrollView.smoothScrollTo(0, rows[selectedIndex].root.top) }
    }

    fun selectByNumber(number: Int): ClipboardEntry? {
        val entry = entries.getOrNull(number - 1) ?: return null
        onSelect(entry)
        return entry
    }

    fun selectCurrent(): ClipboardEntry? {
        val entry = rows.getOrNull(selectedIndex)?.entry ?: return null
        onSelect(entry)
        return entry
    }

    private fun updateSelection() {
        rows.forEachIndexed { index, row ->
            val selected = index == selectedIndex
            row.root.isSelected = selected
            row.number.background = roundedDrawable(
                if (selected) theme.genericActiveBackgroundColor else theme.clipboardEntryColor,
                if (selected) theme.genericActiveBackgroundColor else theme.popupBackgroundColor
            )
            row.entryView.root.background = roundedDrawable(
                theme.clipboardEntryColor,
                if (selected) theme.genericActiveBackgroundColor else theme.popupBackgroundColor
            )
        }
    }

    private fun roundedDrawable(color: Int, strokeColor: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(8).toFloat()
            setColor(color)
            setStroke(context.dp(1), strokeColor)
        }
}
