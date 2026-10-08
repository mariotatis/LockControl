package com.mariotatis.lockcontrol.data

import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256

    fun newSalt(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    fun verify(pin: String, config: LockConfig): Boolean {
        val salt = config.pinSalt ?: return false
        val expected = config.pinHash ?: return false
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), expected.toByteArray())
    }
}

/** Process-wide failed attempt tracking, so re-locking doesn't reset a lockout. */
object LockoutTracker {
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_MS = 30_000L

    private var failures = 0
    var lockedUntil = 0L
        private set

    fun remainingMs(): Long = (lockedUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    fun recordFailure() {
        failures++
        if (failures >= MAX_ATTEMPTS) {
            failures = 0
            lockedUntil = SystemClock.elapsedRealtime() + LOCKOUT_MS
        }
    }

    fun recordSuccess() {
        failures = 0
        lockedUntil = 0
    }
}
