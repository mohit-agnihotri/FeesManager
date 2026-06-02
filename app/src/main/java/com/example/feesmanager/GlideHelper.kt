package com.example.feesmanager

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions

/**
 * GlideHelper — Centralised avatar image loader.
 *
 * All ImageViews in the app that display profile photos MUST use this helper
 * so caching, error fallback, and circle-crop are applied uniformly.
 *
 * Path: app/src/main/java/com/example/feesmanager/GlideHelper.kt
 *
 * Dependency required in build.gradle.kts:
 *   implementation("com.github.bumptech.glide:glide:4.16.0")
 */
object GlideHelper {

    /**
     * Load [url] into [imageView] with disk cache.
     * Falls back to ic_default_avatar when url is null, blank, or fails to load.
     * Use this for initial load on screen open.
     */
    fun loadAvatar(imageView: ImageView, url: String?) {
        Glide.with(imageView.context)
            .load(url?.takeIf { it.isNotBlank() })
            .apply(
                RequestOptions()
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
            )
            .into(imageView)
    }

    /**
     * Load [url] bypassing all caches — use immediately after a fresh upload
     * so the newly uploaded image is shown without a stale cache hit.
     */
    fun loadAvatarFresh(imageView: ImageView, url: String?) {
        Glide.with(imageView.context)
            .load(url?.takeIf { it.isNotBlank() })
            .apply(
                RequestOptions()
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
            )
            .into(imageView)
    }
}
