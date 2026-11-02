package tomato.notes.helpers

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Export format: "ENC3:" + Base64(salt(16) | iv(12) | AES-256-GCM ciphertext + 128-bit tag), key from PBKDF2-HMAC-SHA256.
// encrypt() and decrypt() run PBKDF2, so call them off the main thread.
object NotesEncryptionHelper {
    const val PREFIX = "ENC3:"
    // ENC: (fixed key) and ENC2: (AES-CBC) exports from older versions are no longer readable
    private val UNSUPPORTED_PREFIXES = listOf("ENC:", "ENC2:")
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BYTES = 32

    fun isPasswordEncrypted(text: String) = text.startsWith(PREFIX)

    fun isUnsupportedEncryption(text: String) = UNSUPPORTED_PREFIXES.any { text.startsWith(it) }

    fun encrypt(plainText: String, password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(salt + iv + encrypted, Base64.NO_WRAP)
    }

    // Returns null for a wrong password or a damaged file (the GCM tag check fails)
    fun decrypt(encryptedText: String, password: String): String? {
        if (!encryptedText.startsWith(PREFIX)) {
            return null
        }
        return try {
            val combined = Base64.decode(encryptedText.removePrefix(PREFIX).trim(), Base64.NO_WRAP)
            if (combined.size < SALT_BYTES + IV_BYTES + TAG_BITS / 8) {
                return null
            }
            val salt = combined.copyOfRange(0, SALT_BYTES)
            val iv = combined.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
            val encrypted = combined.copyOfRange(SALT_BYTES + IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        return SecretKeySpec(Pbkdf2.deriveKey(password, salt, Pbkdf2.DEFAULT_ITERATIONS, KEY_BYTES), "AES")
    }
}
