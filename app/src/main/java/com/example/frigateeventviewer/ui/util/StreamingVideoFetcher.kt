package com.example.frigateeventviewer.ui.util

import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.fetch.FetchResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coil [Fetcher] that streams .mp4 URLs via [MediaMetadataRetriever] and returns a single
 * frame at 2 seconds as a drawable, without downloading the full file. Avoids the
 * ProtocolException caused when Coil's default pipeline buffers the entire video.
 */
class StreamingVideoFetcher(
    private val data: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(data.toString(), emptyMap())
                val bitmap = retriever.getFrameAtTime(
                    2_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: return@withContext null
                val drawable = BitmapDrawable(options.context.resources, bitmap)
                DrawableResult(
                    drawable = drawable,
                    isSampled = false,
                    dataSource = DataSource.NETWORK
                )
            } catch (_: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.toString().lowercase().endsWith(".mp4")) return null
            return StreamingVideoFetcher(data, options)
        }
    }
}
