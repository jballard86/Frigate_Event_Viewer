package com.example.frigateeventviewer.data.push

import android.graphics.Bitmap
import android.util.LruCache

/**
 * In-memory cache of scaled notification bitmaps by ce_id.
 * Entries expire after [TTL_MILLIS]; [get] returns null and evicts if expired.
 * Call [removeForCeId] when an event is deleted so the cache does not retain it.
 */
object NotificationImageCache {

    private const val TTL_MILLIS = 72L * 60 * 60 * 1000

    private data class Entry(val bitmap: Bitmap, val cachedAtMillis: Long)

    private val cache = object : LruCache<String, Entry>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Entry): Int = value.bitmap.byteCount
    }

    /**
     * Returns the cached bitmap for [ceId], or null if missing or older than 72 hours.
     * Evicts the entry if expired.
     */
    @Synchronized
    fun get(ceId: String): Bitmap? {
        val entry = cache.get(ceId) ?: return null
        if (System.currentTimeMillis() - entry.cachedAtMillis > TTL_MILLIS) {
            cache.remove(ceId)
            return null
        }
        return entry.bitmap
    }

    /**
     * Stores [bitmap] for [ceId] with current timestamp.
     */
    @Synchronized
    fun put(ceId: String, bitmap: Bitmap) {
        cache.put(ceId, Entry(bitmap, System.currentTimeMillis()))
    }

    /**
     * Removes the cached image for [ceId]. Call when the event is deleted.
     */
    @Synchronized
    fun removeForCeId(ceId: String) {
        cache.remove(ceId)
    }

    /**
     * Removes the cached image for the event identified by [eventPath].
     * [eventPath] is the API path e.g. "events/1772256011_69405f11"; the cache is keyed by ce_id ("ce_1772256011_69405f11").
     */
    @Synchronized
    fun removeForEventPath(eventPath: String) {
        val folder = eventPath.substringAfterLast('/')
        if (folder.isNotBlank()) {
            cache.remove("ce_$folder")
        }
    }
}
