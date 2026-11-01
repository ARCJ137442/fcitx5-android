/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.annotation.Keep
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardDao
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardDatabase
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.utils.WeakHashSet
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.clipboardManager
import timber.log.Timber

object ClipboardManager : ClipboardManager.OnPrimaryClipChangedListener,
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private lateinit var clbDb: ClipboardDatabase
    private lateinit var clbDao: ClipboardDao

    fun interface OnClipboardUpdateListener {
        fun onUpdate(entry: ClipboardEntry)
    }

    private val clipboardManager = appContext.clipboardManager

    private val mutex = Mutex()
    private val pinnedOrderMutex = Mutex()
    private val fixedPinnedOrderPref = AppPrefs.getInstance().clipboard.fixedPinnedOrder
    private val fixedPinnedOrderInitializedPref =
        AppPrefs.getInstance().clipboard.fixedPinnedOrderInitialized

    var itemCount: Int = 0
        private set

    private suspend fun updateItemCount() {
        itemCount = clbDao.itemCount()
    }

    private val onUpdateListeners = WeakHashSet<OnClipboardUpdateListener>()

    var transformer: ((String) -> String)? = null

    fun addOnUpdateListener(listener: OnClipboardUpdateListener) {
        onUpdateListeners.add(listener)
    }

    fun removeOnUpdateListener(listener: OnClipboardUpdateListener) {
        onUpdateListeners.remove(listener)
    }

    private val enabledPref = AppPrefs.getInstance().clipboard.clipboardListening

    @Keep
    private val enabledListener = ManagedPreference.OnChangeListener<Boolean> { _, value ->
        if (value) {
            clipboardManager.addPrimaryClipChangedListener(this)
        } else {
            clipboardManager.removePrimaryClipChangedListener(this)
        }
    }

    private val limitPref = AppPrefs.getInstance().clipboard.clipboardHistoryLimit

    @Keep
    private val limitListener = ManagedPreference.OnChangeListener<Int> { _, _ ->
        launch { removeOutdated() }
    }

    var lastEntry: ClipboardEntry? = null

    private fun updateLastEntry(entry: ClipboardEntry) {
        lastEntry = entry
        onUpdateListeners.forEach { it.onUpdate(entry) }
    }

    fun init(context: Context) {
        clbDb = Room
            .databaseBuilder(context, ClipboardDatabase::class.java, "clbdb")
            // allow wipe the database instead of crashing when downgrade
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
        clbDao = clbDb.clipboardDao()
        enabledListener.onChange(enabledPref.key, enabledPref.getValue())
        enabledPref.registerOnChangeListener(enabledListener)
        limitListener.onChange(limitPref.key, limitPref.getValue())
        limitPref.registerOnChangeListener(limitListener)
        launch { updateItemCount() }
    }

    suspend fun get(id: Int) = clbDao.get(id)

    suspend fun haveUnpinned() = clbDao.haveUnpinned()

    fun allEntries() = clbDao.allEntries()

    suspend fun pinnedEntries(limit: Int, offset: Int): List<ClipboardEntry> {
        return if (fixedPinnedOrderPref.getValue()) {
            ensurePinnedOrderInitialized()
            clbDao.pinnedEntriesByOrder(limit, offset)
        } else {
            clbDao.pinnedEntries(limit, offset)
        }
    }

    suspend fun pinnedEntryCount() = clbDao.pinnedEntryCount()

    suspend fun getPinned(id: Int) = clbDao.getPinned(id)

    suspend fun usePinned(id: Int): ClipboardEntry? = clbDb.withTransaction {
        val entry = clbDao.getPinned(id) ?: return@withTransaction null
        val used = entry.copy(timestamp = System.currentTimeMillis())
        clbDao.updateTime(id, used.timestamp)
        used
    }

    suspend fun pin(id: Int) {
        if (fixedPinnedOrderPref.getValue()) ensurePinnedOrderInitialized()
        clbDb.withTransaction {
            val entry = clbDao.get(id) ?: return@withTransaction
            clbDao.updatePinStatus(id, true)
            clbDao.updateTime(id, System.currentTimeMillis())
            if (fixedPinnedOrderInitializedPref.getValue()) {
                normalizePinnedOrder(topId = entry.id)
            }
        }
    }

    suspend fun unpin(id: Int) {
        clbDb.withTransaction {
            clbDao.updatePinStatus(id, false)
            if (fixedPinnedOrderInitializedPref.getValue()) normalizePinnedOrder()
        }
    }

    suspend fun pinFromNative(text: String, sensitive: Boolean): ClipboardEntry? {
        if (text.isBlank()) return null
        if (fixedPinnedOrderPref.getValue()) ensurePinnedOrderInitialized()
        val timestamp = System.currentTimeMillis()
        val entry = clbDb.withTransaction {
            val existing = clbDao.find(text, sensitive)
            val pinned = if (existing != null) {
                clbDao.updatePinStatus(existing.id, true)
                clbDao.updateTime(existing.id, timestamp)
                existing.copy(pinned = true, timestamp = timestamp)
            } else {
                val newEntry = ClipboardEntry(
                    text = text,
                    pinned = true,
                    timestamp = timestamp,
                    sensitive = sensitive
                )
                val rowId = clbDao.insert(newEntry)
                clbDao.get(rowId) ?: newEntry
            }
            if (fixedPinnedOrderInitializedPref.getValue()) {
                normalizePinnedOrder(topId = pinned.id)
            }
            pinned
        }
        updateItemCount()
        return entry
    }

    data class PinnedMoveResult(
        val selectedIndex: Int,
        val fixedOrderWasEnabled: Boolean,
        val moved: Boolean
    )

    suspend fun movePinned(id: Int, delta: Int): PinnedMoveResult? {
        val enabledOrder = fixedPinnedOrderPref.getValue()
        if (!enabledOrder) fixedPinnedOrderPref.setValue(true)
        ensurePinnedOrderInitialized()
        return clbDb.withTransaction {
            val entries = clbDao.allPinnedEntriesByOrder()
            val currentIndex = entries.indexOfFirst { it.id == id }
            if (currentIndex < 0) return@withTransaction null
            val selectedIndex = (currentIndex + delta).coerceIn(0, entries.lastIndex)
            if (selectedIndex != currentIndex) {
                val reordered = entries.toMutableList()
                val selected = reordered.removeAt(currentIndex)
                reordered.add(selectedIndex, selected)
                reordered.forEachIndexed { index, entry ->
                    if (entry.pinnedOrder != index) clbDao.updatePinnedOrder(entry.id, index)
                }
            }
            PinnedMoveResult(selectedIndex, fixedOrderWasEnabled = !enabledOrder, moved = selectedIndex != currentIndex)
        }
    }

    private suspend fun ensurePinnedOrderInitialized() {
        if (fixedPinnedOrderInitializedPref.getValue()) return
        pinnedOrderMutex.withLock {
            if (fixedPinnedOrderInitializedPref.getValue()) return@withLock
            clbDb.withTransaction {
                clbDao.allPinnedEntriesByRecentUse().forEachIndexed { index, entry ->
                    clbDao.updatePinnedOrder(entry.id, index)
                }
            }
            fixedPinnedOrderInitializedPref.setValue(true)
        }
    }

    private suspend fun normalizePinnedOrder(topId: Int? = null) {
        val entries = clbDao.allPinnedEntriesByOrder()
        val ordered = if (topId == null) {
            entries
        } else {
            entries.sortedWith(
                compareBy<ClipboardEntry> { if (it.id == topId) 0 else 1 }
                    .thenBy { it.pinnedOrder }
                    .thenByDescending { it.timestamp }
            )
        }
        ordered.forEachIndexed { index, entry ->
            if (entry.pinnedOrder != index) clbDao.updatePinnedOrder(entry.id, index)
        }
    }

    suspend fun updateText(id: Int, text: String) {
        lastEntry?.let {
            if (id == it.id) updateLastEntry(it.copy(text = text))
        }
        clbDao.updateText(id, text)
    }

    suspend fun delete(id: Int) {
        clbDao.markAsDeleted(id)
        updateItemCount()
    }

    suspend fun deleteAll(skipPinned: Boolean = true): IntArray {
        val ids = if (skipPinned) {
            clbDao.findUnpinnedIds()
        } else {
            clbDao.findAllIds()
        }
        clbDao.markAsDeleted(*ids)
        updateItemCount()
        return ids
    }

    suspend fun undoDelete(vararg ids: Int) {
        clbDao.undoDelete(*ids)
        updateItemCount()
    }

    suspend fun realDelete() {
        clbDao.realDelete()
    }

    suspend fun nukeTable() {
        withContext(coroutineContext) {
            clbDb.clearAllTables()
            updateItemCount()
        }
    }

    private var lastClipTimestamp = -1L
    private var lastClipHash = 0

    override fun onPrimaryClipChanged() {
        val clip = clipboardManager.primaryClip ?: return
        /**
         * skip duplicate ClipData
         * https://developer.android.com/reference/android/content/ClipboardManager.OnPrimaryClipChangedListener#onPrimaryClipChanged()
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timestamp = clip.description.timestamp
            if (timestamp == lastClipTimestamp) return
            lastClipTimestamp = timestamp
        } else {
            val timestamp = System.currentTimeMillis()
            val hash = clip.hashCode()
            if (timestamp - lastClipTimestamp < 100L && hash == lastClipHash) return
            lastClipTimestamp = timestamp
            lastClipHash = hash
        }
        launch {
            mutex.withLock {
                val entry = ClipboardEntry.fromClipData(clip, transformer) ?: return@withLock
                if (entry.text.isBlank()) return@withLock
                try {
                    clbDao.find(entry.text, entry.sensitive)?.let {
                        updateLastEntry(it.copy(timestamp = entry.timestamp))
                        clbDao.updateTime(it.id, entry.timestamp)
                        return@withLock
                    }
                    val insertedEntry = clbDb.withTransaction {
                        val rowId = clbDao.insert(entry)
                        removeOutdated()
                        // new entry can be deleted immediately if clipboard limit == 0
                        clbDao.get(rowId) ?: entry
                    }
                    updateLastEntry(insertedEntry)
                    updateItemCount()
                } catch (exception: Exception) {
                    Timber.w("Failed to update clipboard database: $exception")
                    updateLastEntry(entry)
                }
            }
        }
    }

    private suspend fun removeOutdated() {
        val limit = limitPref.getValue()
        val unpinned = clbDao.getAllUnpinned()
        if (unpinned.size > limit) {
            // the last one we will keep
            val last = unpinned
                .sortedBy { it.id }
                .getOrNull(unpinned.size - limit)
            // delete all unpinned before that, or delete all when limit <= 0
            clbDao.markUnpinnedAsDeletedEarlierThan(last?.timestamp ?: System.currentTimeMillis())
        }
    }

}
