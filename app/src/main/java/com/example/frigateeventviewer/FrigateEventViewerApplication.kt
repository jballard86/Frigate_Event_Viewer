package com.example.frigateeventviewer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.frigateeventviewer.ui.util.StreamingVideoFetcher

/**
 * Application entry point. Provides a global Coil [ImageLoader] configured with
 * [StreamingVideoFetcher] so that .mp4 thumbnail URLs are streamed via
 * MediaMetadataRetriever (frame at 2s) without full-file download. This avoids
 * ProtocolException and network saturation. Use the default Coil ImageLoader app-wide.
 */
class FrigateEventViewerApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(StreamingVideoFetcher.Factory())
            }
            .build()
    }
}
