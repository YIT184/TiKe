package com.codex.shuatitimer.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat

/**
 * Adds Huawei HarmonyOS 4 Live Window metadata to an ordinary Android notification.
 *
 * The Android notification remains the functional fallback. HarmonyOS only renders the
 * status-bar capsule when the app's exact package/signature has been granted the TIMER scene
 * entitlement in AppGallery Connect and the user has enabled Live Window notifications.
 */
object LiveWindowExtras {
    const val EVENT_TIMER = "TIMER"

    object Operation {
        const val CREATE = 0
        const val UPDATE = 1
        const val END = 2
    }

    /**
     * Decorates [builder] using the public HarmonyOS 4 Android notification-extension schema.
     * The capsule fields intentionally live in the nested `notification.live.capsule` Bundle.
     */
    fun apply(
        context: Context,
        builder: NotificationCompat.Builder,
        operation: Int,
        title: String,
        content: String,
        @DrawableRes iconResId: Int,
        backgroundColor: Int = Color.rgb(42, 92, 72)
    ) {
        val capsule = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1)
            putInt("notification.live.capsuleType", 1)
            putParcelable(
                "notification.live.capsuleIcon",
                Icon.createWithResource(context, iconResId)
            )
            putInt("notification.live.capsuleBgColor", backgroundColor)
            putString("notification.live.capsuleTitle", title)
            putString("notification.live.capsuleContent", content)
            putBoolean("notification.live.capsuleRemind", operation == Operation.CREATE)
        }

        val liveData = Bundle().apply {
            putInt("notification.live.operation", operation)
            putString("notification.live.event", EVENT_TIMER)
            // Basic notification template. TIMER is the business scene, while type selects layout.
            putInt("notification.live.type", 1)
            putCharSequence("notification.live.titleOverlay", title)
            putCharSequence("notification.live.contentOverlay", content)
            putBundle("notification.live.capsule", capsule)
        }
        builder.addExtras(liveData)
    }
}
