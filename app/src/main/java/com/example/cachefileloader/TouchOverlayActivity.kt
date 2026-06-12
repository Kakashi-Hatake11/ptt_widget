package com.example.cachefileloader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager

/**
 * TouchOverlayActivity
 *
 * A transparent, theme-less Activity launched by the PTT home-screen widget
 * when the user taps the widget button. It immediately captures the touch
 * stream to simulate true hold-to-talk behaviour — something plain
 * AppWidget PendingIntents cannot do on their own.
 *
 * Flow:
 *  1. Widget button tap → this Activity starts (invisible to user).
 *  2. ACTION_DOWN → broadcasts PTT_PRESS, widget turns red/active.
 *  3. ACTION_UP / CANCEL → broadcasts PTT_RELEASE, widget returns to idle.
 *  4. Activity finishes immediately after release.
 */
class TouchOverlayActivity : Activity() {

    companion object {
        const val EXTRA_IS_TALKING = "extra_is_talking"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS  // add this
        )
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val isTalking = intent.getBooleanExtra(EXTRA_IS_TALKING, false)
        if (isTalking) {
            firePttRelease()
            finish()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                firePttPress()
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                firePttRelease()
                finish()
                true
            }
            else -> super.dispatchTouchEvent(event)
        }
    }

    private fun firePttPress() {
        sendBroadcast(
            Intent(PttWidget.ACTION_PTT_PRESS).setPackage(packageName)
        )
    }

    private fun firePttRelease() {
        sendBroadcast(
            Intent(PttWidget.ACTION_PTT_RELEASE).setPackage(packageName)
        )
    }
}
