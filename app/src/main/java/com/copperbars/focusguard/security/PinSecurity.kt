package com.copperbars.focusguard.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.copperbars.focusguard.data.FocusRepository
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.first

sealed interface PinCheck {
    data object Success : PinCheck
    data object Invalid : PinCheck
    data object NotConfigured : PinCheck
    data class Locked(val untilMillis: Long) : PinCheck
}

class PinSecurity(private val repository: FocusRepository) {
    companion object {
        private const val KEY_ALIAS = "focus_guard_pin_hmac_v1"
        private const val ITERATIONS = 160_000
        private const val KEY_BITS = 256
    }

    suspend fun setPin(pin: String): Boolean {
        if (pin.length !in 4..12 || !pin.all(Char::isDigit)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val verifier = calculateVerifier(pin, salt)
        repository.savePinRecord(
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(verifier)
        )
        return true
    }

    suspend fun verify(pin: String): PinCheck {
        val settings = repository.settings.first()
        if (settings.pinSalt.isBlank() || settings.pinVerifier.isBlank()) {
            return PinCheck.NotConfigured
        }

        val now = System.currentTimeMillis()
        if (settings.pinLockUntilMillis > now) {
            return PinCheck.Locked(settings.pinLockUntilMillis)
        }

        return try {
            val salt = Base64.getDecoder().decode(settings.pinSalt)
            val expected = Base64.getDecoder().decode(settings.pinVerifier)
            val actual = calculateVerifier(pin, salt)
            if (MessageDigest.isEqual(expected, actual)) {
                repository.clearPinFailures()
                PinCheck.Success
            } else {
                val lockUntil = repository.recordWrongPin()
                if (lockUntil > now) PinCheck.Locked(lockUntil) else PinCheck.Invalid
            }
        } catch (_: Exception) {
            PinCheck.Invalid
        }
    }

    private fun calculateVerifier(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val derived = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(getOrCreateKeystoreKey())
        return mac.doFinal(derived)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setDigests(KeyProperties.DIGEST_SHA256).build()
        )
        return generator.generateKey()
    }
}
