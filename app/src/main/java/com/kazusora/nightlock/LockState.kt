package com.kazusora.nightlock

import android.content.Context
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * ロックするかどうかを決める。
 * 直近の 21:30 より後に解除していなければロック中。
 * 一度ロックしたら、時計を戻されても解除されるまでロックのまま。
 */
object LockState {
    const val LOCK_HOUR = 21
    const val LOCK_MINUTE = 30

    private const val PREFS = "lock_state"
    private const val KEY_HASH = "password_hash"
    private const val KEY_SALT = "password_salt"
    private const val KEY_LAST_UNLOCK = "last_unlock"
    private const val KEY_LOCKED = "locked"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_FAILS = "fails"
    private const val KEY_WAIT_UNTIL = "wait_until"

    private const val MAX_FAILS = 5
    private const val WAIT_MILLIS = 30_000L

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lockLabel() = "%d:%02dです".format(LOCK_HOUR, LOCK_MINUTE)

    fun hasPassword(c: Context) = prefs(c).contains(KEY_HASH)

    fun setPassword(c: Context, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        val edit = prefs(c).edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_HASH, hash(salt, password))
        // 初めて設定したときは、その日の 21:30 まではロックしない
        if (!prefs(c).contains(KEY_LAST_UNLOCK)) {
            edit.putLong(KEY_LAST_UNLOCK, System.currentTimeMillis())
        }
        edit.apply()
    }

    fun checkPassword(c: Context, password: String): Boolean {
        val p = prefs(c)
        val salt = p.getString(KEY_SALT, null) ?: return false
        return hash(salt, password) == p.getString(KEY_HASH, null)
    }

    fun isEnabled(c: Context) = prefs(c).getBoolean(KEY_ENABLED, true)

    /** オフにするとロックしない。オンに戻したときは、次の 21:30 からロックする */
    fun setEnabled(c: Context, enabled: Boolean) {
        val edit = prefs(c).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putBoolean(KEY_LOCKED, false)
        if (enabled) edit.putLong(KEY_LAST_UNLOCK, System.currentTimeMillis())
        edit.apply()
    }

    fun isLocked(c: Context): Boolean {
        if (!hasPassword(c) || !isEnabled(c)) return false
        val p = prefs(c)
        if (p.getBoolean(KEY_LOCKED, false)) return true
        val now = System.currentTimeMillis()
        if (p.getLong(KEY_LAST_UNLOCK, now) < latestLockTime(now)) {
            p.edit().putBoolean(KEY_LOCKED, true).apply()
            return true
        }
        return false
    }

    fun lockNow(c: Context) {
        prefs(c).edit().putBoolean(KEY_LOCKED, true).apply()
    }

    fun unlock(c: Context) {
        prefs(c).edit()
            .putBoolean(KEY_LOCKED, false)
            .putLong(KEY_LAST_UNLOCK, System.currentTimeMillis())
            .putInt(KEY_FAILS, 0)
            .apply()
    }

    /** パスワードを 5 回まちがえたら 30 秒待たせる（総当たり対策） */
    fun recordFailure(c: Context) {
        val p = prefs(c)
        val fails = p.getInt(KEY_FAILS, 0) + 1
        if (fails >= MAX_FAILS) {
            p.edit()
                .putInt(KEY_FAILS, 0)
                .putLong(KEY_WAIT_UNTIL, System.currentTimeMillis() + WAIT_MILLIS)
                .apply()
        } else {
            p.edit().putInt(KEY_FAILS, fails).apply()
        }
    }

    fun waitSeconds(c: Context): Long {
        val left = prefs(c).getLong(KEY_WAIT_UNTIL, 0) - System.currentTimeMillis()
        return if (left > 0) (left + 999) / 1000 else 0
    }

    /** now 以前でいちばん新しい 21:30 */
    private fun latestLockTime(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, LOCK_HOUR)
            set(Calendar.MINUTE, LOCK_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis > now) cal.add(Calendar.DAY_OF_YEAR, -1)
        return cal.timeInMillis
    }

    /** 保存するのはパスワードそのものではなく、元に戻しにくい形（PBKDF2）にしたもの */
    private fun hash(salt: String, password: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), 50_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
