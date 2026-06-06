package com.example.feesmanager.utils

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * AnimUtil — Shared animation helpers used across all Activities.
 * Provides staggered entrance, click bounce, and page transitions.
 */
object AnimUtil {

    /** Staggered fade+slide entrance for a list of views */
    fun staggerIn(views: List<View>, baseDelayMs: Long = 60L) {
        views.forEachIndexed { i, view ->
            view.alpha = 0f
            view.translationY = 40f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(i * baseDelayMs)
                .setDuration(320)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }
    }

    /** Scale bounce on click — gives physical "press" feel */
    fun bounce(view: View) {
        view.animate()
            .scaleX(0.92f).scaleY(0.92f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(120)
                    .setInterpolator(OvershootInterpolator(3f))
                    .start()
            }.start()
    }

    /** Slide up + fade in a single view */
    fun slideUp(view: View, delayMs: Long = 0L) {
        view.alpha = 0f
        view.translationY = 50f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delayMs)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    /** Attach bounce to any clickable view — call in setupButtons() */
    fun View.withBounce(action: () -> Unit) {
        setOnClickListener {
            bounce(this)
            postDelayed({ action() }, 90)
        }
    }

    /** Scale in from 0.8 — good for cards / stat tiles appearing */
    fun scaleIn(view: View, delayMs: Long = 0L) {
        view.scaleX = 0.82f; view.scaleY = 0.82f; view.alpha = 0f
        view.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setStartDelay(delayMs)
            .setDuration(380)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()
    }
}