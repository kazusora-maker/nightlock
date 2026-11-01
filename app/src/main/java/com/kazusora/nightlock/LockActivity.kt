package com.kazusora.nightlock

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** 真っ黒な画面に「21:30です」。パスワードか顔認証でだけ閉じられる。 */
class LockActivity : AppCompatActivity() {

    companion object {
        @Volatile var visible = false
        @Volatile var authenticating = false
    }

    private lateinit var passwordInput: EditText
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setContentView(R.layout.activity_lock)

        findViewById<TextView>(R.id.lockTitle).text = LockState.lockLabel()
        passwordInput = findViewById(R.id.passwordInput)
        errorText = findViewById(R.id.errorText)

        // 戻るボタンでは閉じない
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        findViewById<Button>(R.id.unlockButton).setOnClickListener { tryPassword() }
        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryPassword()
                true
            } else {
                false
            }
        }

        findViewById<Button>(R.id.emergencyButton).setOnClickListener { openEmergencyDialer() }

        val faceButton = findViewById<Button>(R.id.faceButton)
        if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
            faceButton.setOnClickListener { startFaceAuth() }
        } else {
            faceButton.isEnabled = false
            faceButton.text = "顔認証は使えません（スマホに顔が未登録）"
        }
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        visible = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!LockState.isLocked(this)) {
            finish()
            return
        }
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun tryPassword() {
        val wait = LockState.waitSeconds(this)
        if (wait > 0) {
            errorText.text = "あと${wait}秒待ってください"
            return
        }
        if (LockState.checkPassword(this, passwordInput.text.toString())) {
            onUnlocked()
        } else {
            LockState.recordFailure(this)
            val next = LockState.waitSeconds(this)
            errorText.text = if (next > 0) "5回まちがえたので${next}秒待ってください" else "パスワードが違います"
            passwordInput.text.clear()
        }
    }

    /** ロック中でも 110・119 にかけられるように、緊急通報の画面を開く */
    private fun openEmergencyDialer() {
        val emergency = Intent("com.android.phone.EmergencyDialer.DIAL")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(emergency)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun startFaceAuth() {
        authenticating = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        errorText.text = errString
                    }
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("顔認証でロック解除")
            .setNegativeButtonText("やめる")
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    private fun onUnlocked() {
        LockState.unlock(this)
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
