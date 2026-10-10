package tomato.notes.helpers

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object NotesEncryptionHelper {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val LEGACY_KEY = "SimpleNotesKey12"
    private const val LEGACY_PREFIX = "ENC:"
    const val PREFIX = "ENC2:"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 16
    private const val ITERATIONS = 12000
    private const val KEY_BITS = 256

    fun isPasswordEncrypted(text: String) = text.startsWith(PREFIX)

    fun isLegacyEncrypted(text: String) = text.startsWith(LEGACY_PREFIX)

    fun encrypt(plainText: String, password: String): String {
        val salt = ByteArray(SALT_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = salt + iv + encrypted
        return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedText: String, password: String): String? {
        if (!encryptedText.startsWith(PREFIX)) {
            return null
        }
        return try {
            val combined = Base64.decode(encryptedText.removePrefix(PREFIX), Base64.NO_WRAP)
            if (combined.size <= SALT_BYTES + IV_BYTES) {
                return null
            }
            val salt = combined.copyOfRange(0, SALT_BYTES)
            val iv = combined.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
            val encrypted = combined.copyOfRange(SALT_BYTES + IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), IvParameterSpec(iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun decryptLegacy(encryptedText: String): String? {
        if (!encryptedText.startsWith(LEGACY_PREFIX)) {
            return null
        }
        return try {
            val combined = Base64.decode(encryptedText.removePrefix(LEGACY_PREFIX), Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_BYTES)
            val encrypted = combined.copyOfRange(IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(LEGACY_KEY.toByteArray(Charsets.UTF_8), ALGORITHM),
                IvParameterSpec(iv)
            )
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val encoded = factory.generateSecret(spec).encoded
        return SecretKeySpec(encoded, ALGORITHM)
    }
}
