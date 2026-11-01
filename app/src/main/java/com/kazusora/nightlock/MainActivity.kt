package com.kazusora.nightlock

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * 設定画面。スマホに詳しくない人でも上から順に進められるよう、
 * 各ステップに ✅ / ⬜ を付け、いちばん上に「次にやること」を出す。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var nextStep: TextView
    private lateinit var enabledSwitch: MaterialSwitch
    private lateinit var currentPassword: EditText
    private lateinit var newPassword: EditText
    private lateinit var newPassword2: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nextStep = findViewById(R.id.nextStep)
        enabledSwitch = findViewById(R.id.enabledSwitch)
        currentPassword = findViewById(R.id.currentPassword)
        newPassword = findViewById(R.id.newPassword)
        newPassword2 = findViewById(R.id.newPassword2)

        findViewById<Button>(R.id.savePasswordButton).setOnClickListener { savePassword() }
        findViewById<Button>(R.id.overlayButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.batteryButton).setOnClickListener { requestIgnoreBattery() }
        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.appInfoButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.testButton).setOnClickListener { testLock() }
    }

    override fun onResume() {
        super.onResume()
        // ロック中にこの画面を開かれたら、ロック画面に戻す
        if (LockState.isLocked(this)) {
            startActivity(Intent(this, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        refresh()
    }

    private fun refresh() {
        val hasPassword = LockState.hasPassword(this)
        val overlay = Settings.canDrawOverlays(this)
        val battery = isIgnoringBattery()
        val accessibility = isAccessibilityOn()
        val tested = prefs().getBoolean(KEY_TESTED, false)

        fun title(id: Int, done: Boolean, text: String) {
            findViewById<TextView>(id).text = (if (done) "✅ " else "⬜ ") + text
        }
        title(R.id.step1Title, hasPassword, if (hasPassword) "1. パスワード（変えるときはここ）" else "1. パスワードを決める")
        title(R.id.step2Title, overlay, "2. 他のアプリの上に表示を許可")
        title(R.id.step3Title, battery, "3. 電池の最適化から外す")
        title(R.id.step4Title, accessibility, "4. ユーザー補助をオンにする")
        title(R.id.step5Title, tested, "5. ためしにロックしてみる")

        val enabled = LockState.isEnabled(this)
        enabledSwitch.visibility = if (hasPassword) View.VISIBLE else View.GONE
        enabledSwitch.setOnCheckedChangeListener(null)
        enabledSwitch.isChecked = enabled
        enabledSwitch.text = if (enabled) "21:30のロック：オン" else "21:30のロック：オフ"
        enabledSwitch.setOnCheckedChangeListener { _, isChecked -> onSwitchChanged(isChecked) }

        nextStep.text = when {
            hasPassword && !enabled -> "いまはオフです\n21:30になってもロックしません。"
            !hasPassword -> "次にやること：1. パスワードを決める"
            !overlay -> "次にやること：2. 他のアプリの上に表示を許可"
            !battery -> "次にやること：3. 電池の最適化から外す"
            !accessibility -> "次にやること：4. ユーザー補助をオンにする"
            !tested -> "次にやること：5. ためしにロックしてみる"
            else -> "設定はすべて完了です 🎉\n毎晩21:30にロックされます。"
        }
        currentPassword.visibility = if (hasPassword) View.VISIBLE else View.GONE
    }

    /** オンはそのまま、オフはパスワードを聞いてから */
    private fun onSwitchChanged(on: Boolean) {
        if (on) {
            LockState.setEnabled(this, true)
            toast("オンにしました。次の21:30からロックします")
            refresh()
            return
        }
        val input = EditText(this).apply {
            hint = "パスワード"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("ロックをオフにする")
            .setMessage("オフにするにはパスワードを入れてください。")
            .setView(input)
            .setPositiveButton("オフにする") { _, _ ->
                if (LockState.checkPassword(this, input.text.toString())) {
                    LockState.setEnabled(this, false)
                    toast("オフにしました")
                } else {
                    toast("パスワードが違います")
                }
                refresh()
            }
            .setNegativeButton("やめる") { _, _ -> refresh() }
            .setOnCancelListener { refresh() }
            .show()
    }

    private fun savePassword() {
        if (LockState.hasPassword(this) && !LockState.checkPassword(this, currentPassword.text.toString())) {
            toast("今のパスワードが違います")
            return
        }
        val pw = newPassword.text.toString()
        if (pw.length < 4) {
            toast("4文字以上にしてください")
            return
        }
        if (pw != newPassword2.text.toString()) {
            toast("2回の入力が一致しません")
            return
        }
        LockState.setPassword(this, pw)
        listOf(currentPassword, newPassword, newPassword2).forEach { it.text.clear() }
        toast("保存しました。紙にメモしておいてください")
        refresh()
    }

    private fun testLock() {
        if (!LockState.hasPassword(this)) {
            toast("先に 1. パスワードを決めてください")
            return
        }
        if (!isAccessibilityOn()) {
            toast("先に 4. ユーザー補助をオンにしてください")
            return
        }
        prefs().edit().putBoolean(KEY_TESTED, true).apply()
        LockState.lockNow(this)
        startActivity(Intent(this, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun isAccessibilityOn(): Boolean {
        val me = ComponentName(this, LockWatchService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    private fun isIgnoringBattery() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBattery() {
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        )
    }

    private fun prefs() = getSharedPreferences("setup", MODE_PRIVATE)

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val KEY_TESTED = "tested"
    }
}
