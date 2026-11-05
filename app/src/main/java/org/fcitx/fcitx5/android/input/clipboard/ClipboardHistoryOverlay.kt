/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp

/**
 * Keyboard-driven clipboard history browser with an in-panel search query.
 *
 * The IME composes search text directly: [setPreedit] shows the current input
 * method preedit inside the search box, while committed text is appended to
 * [query] by FcitxInputMethodService. Tapping a row pastes it, tapping outside
 * the panel (but inside the overlay) dismisses the panel.
 *
 * Like [ClipboardFavoritesOverlay], key dispatch stays in
 * FcitxInputMethodService so opening the panel never steals editor focus.
 */
class ClipboardHistoryOverlay(
    context: Context,
    private val theme: Theme,
    private val entries: List<ClipboardEntry>,
    page: Int,
    pageCount: Int,
    maskSensitive: Boolean,
    private val query: String,
    selectedEntryId: Int? = null,
    selectedIndex: Int = 0,
    private val onSelect: (ClipboardEntry) -> Unit,
    private val onDismiss: () -> Unit,
    private val hint: String?,
    private val emptyMessage: String? = null,
    preedit: String = "",
    maxPanelHeight: Int = 0
) : FrameLayout(context) {

    private data class Row(
        val root: LinearLayout,
        val entry: ClipboardEntry,
        val entryView: ClipboardEntryUi
    )

    private val scrollView = ScrollView(context)
    private val rows = mutableListOf<Row>()
    private var selectedIndex = selectedIndex.coerceAtLeast(0)

    private var preeditText: String = preedit

    private val searchView = TextView(context).apply {
        textSize = 14f
        setTextColor(theme.popupTextColor)
        setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(8))
        background = roundedDrawable(theme.clipboardEntryColor, theme.popupBackgroundColor)
        ellipsize = TextUtils.TruncateAt.START
        maxLines = 1
    }

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
            text = context.getString(R.string.clipboard_history_title)
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

        panel.addView(searchView)

        refreshSearchText()

        if (!hint.isNullOrBlank()) {
            panel.addView(TextView(context).apply {
                text = hint
                textSize = 12f
                setTextColor(theme.popupTextColor)
                setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(6))
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            })
        }

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (entries.isEmpty()) {
            list.addView(TextView(context).apply {
                text = emptyMessage ?: if (query.isEmpty()) {
                    context.getString(R.string.clipboard_history_no_history)
                } else {
                    context.getString(R.string.clipboard_history_empty)
                }
                textSize = 15f
                setTextColor(theme.popupTextColor)
                gravity = Gravity.CENTER
                setPadding(context.dp(12), context.dp(22), context.dp(12), context.dp(22))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else {
            entries.forEachIndexed { index, entry ->
                val entryView = ClipboardEntryUi(context, theme, context.dp(7).toFloat()).apply {
                    val excerpt = ClipboardAdapter.excerptText(
                        entry.text,
                        entry.sensitive && maskSensitive,
                        lines = 3,
                        chars = 72
                    )
                    setEntry(excerpt, entry.pinned)
                    root.contentDescription = context.getString(
                        R.string.clipboard_history_entry_description,
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
                row.addView(entryView.root, LinearLayout.LayoutParams(0, context.dp(54), 1f))
                entryView.root.setOnClickListener { onSelect(entry) }
                rows += Row(row, entry, entryView)
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

        val hintHeight = if (!hint.isNullOrBlank()) context.dp(30) else 0
        val titleHeight = context.dp(34)
        val searchHeight = context.dp(42)
        val panelMaxHeight = when {
            maxPanelHeight > 0 -> maxPanelHeight
            else -> (context.resources.displayMetrics.heightPixels * 0.62f).toInt()
        }
        val listMaxHeight = (panelMaxHeight - titleHeight - searchHeight - hintHeight - context.dp(30))
            .coerceAtLeast(context.dp(72))
        val desiredListHeight = if (entries.isEmpty()) context.dp(82) else entries.size * context.dp(62)
        val listHeight = minOf(desiredListHeight, listMaxHeight).coerceAtLeast(context.dp(72))
        scrollView.addView(
            list,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight)
        )
        val availableWidth = (context.resources.displayMetrics.widthPixels - context.dp(36))
            .coerceAtLeast(context.dp(260))
        val panelWidth = minOf(availableWidth, context.dp(620))
        addView(
            panel,
            FrameLayout.LayoutParams(
                panelWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        updateSelection()
        scrollView.post {
            rows.getOrNull(selectedIndex)?.let { scrollView.scrollTo(0, it.root.top) }
        }
        // The header text wraps differently across devices, so trim the list
        // instead of letting the panel spill over the keyboard area
        panel.post {
            val overflow = panel.height - panelMaxHeight
            if (overflow > 0) {
                val trimmed = (scrollView.height - overflow).coerceAtLeast(context.dp(72))
                if (trimmed < scrollView.height) {
                    scrollView.updateLayoutParams<LinearLayout.LayoutParams> {
                        height = trimmed
                    }
                }
            }
        }
    }

    /**
     * Shows the current input method composition inside the search box.
     * Committed text is appended to [query] by the service and triggers a reload.
     */
    fun setPreedit(preedit: String) {
        if (preeditText == preedit) return
        preeditText = preedit
        refreshSearchText()
    }

    private fun refreshSearchText() {
        searchView.text = when {
            query.isEmpty() && preeditText.isEmpty() ->
                context.getString(R.string.clipboard_history_search_placeholder)
            preeditText.isEmpty() ->
                context.getString(R.string.clipboard_history_search_query, query)
            else -> buildSpannedString {
                if (query.isNotEmpty()) append(query)
                color(theme.genericActiveForegroundColor) { append(preeditText) }
                append("▌")
            }
        }
    }

    fun moveSelection(delta: Int) {
        if (rows.isEmpty()) return
        selectedIndex = (selectedIndex + delta).coerceIn(0, rows.lastIndex)
        updateSelection()
        scrollView.post { scrollView.smoothScrollTo(0, rows[selectedIndex].root.top) }
    }

    fun cycleSelection(delta: Int) {
        if (rows.isEmpty()) return
        selectedIndex = Math.floorMod(selectedIndex + delta, rows.size)
        updateSelection()
        scrollView.post { scrollView.smoothScrollTo(0, rows[selectedIndex].root.top) }
    }

    fun selectByNumber(number: Int): ClipboardEntry? {
        val index = if (number == 0) 9 else number - 1
        val entry = entries.getOrNull(index) ?: return null
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
