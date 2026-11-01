package com.kazusora.nightlock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * ユーザー補助サービス。画面が切り替わるたびにロック中かを確かめ、
 * ロック中ならロック画面を前に出す。21:30 ちょうどを逃さないよう 10 秒ごとにも確かめる。
 */
class LockWatchService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastShownAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            checkAndShow(null)
            handler.postDelayed(this, 10_000)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            checkAndShow(null)
        }
    }

    override fun onServiceConnected() {
        handler.post(tick)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        checkAndShow(event.packageName?.toString())
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    private fun checkAndShow(foregroundPackage: String?) {
        if (!LockState.isLocked(this)) return
        // 顔認証のダイアログを出している間は邪魔しない
        if (LockActivity.authenticating) return
        if (foregroundPackage == null) {
            if (LockActivity.visible) return
        } else if (isAllowed(foregroundPackage)) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastShownAt < 500) return
        lastShownAt = now

        startActivity(
            Intent(this, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
    }

    /**
     * ロック中でも出てよい画面:
     * このアプリ・キーボード・システムの画面（顔認証や通知）・電話（緊急通報と着信）・時計（アラーム）
     */
    private fun isAllowed(pkg: String): Boolean {
        if (pkg in ALWAYS_ALLOWED) return true
        if (pkg == keyboardPackage()) return true
        if (pkg == defaultDialerPackage()) return true
        // 設定画面（MainActivity）はロック中に開かせない
        return pkg == packageName && LockActivity.visible
    }

    private fun keyboardPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    private fun defaultDialerPackage(): String? =
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage

    companion object {
        private val ALWAYS_ALLOWED = setOf(
            "com.android.systemui",
            // 電話・緊急通報・着信画面
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.android.incallui",
            "com.android.emergency",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            // 時計（朝のアラームを止められるように）
            "com.android.deskclock",
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.sonymobile.alarm",
        )
    }
}
