package com.dev.where.tracker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class TypingAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var isTyping: Boolean = false
        @Volatile var activeApp: String = ""

        private const val TYPING_TIMEOUT_MS = 3_000L
        private var lastTypingTime = 0L

        fun checkAndResetTyping(): Boolean {
            val now = System.currentTimeMillis()
            if (isTyping && (now - lastTypingTime) > TYPING_TIMEOUT_MS) {
                isTyping = false
            }
            return isTyping
        }
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d("TypingService", "AccessibilityService connesso")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                isTyping = true
                lastTypingTime = System.currentTimeMillis()
                Log.d("TypingService", "Digitazione rilevata — app: ${event.packageName}")
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                activeApp = event.packageName?.toString() ?: ""
            }
        }
    }

    override fun onInterrupt() {
        Log.d("TypingService", "AccessibilityService interrotto")
    }
}
