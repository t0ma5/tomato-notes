package tomato.notes.helpers

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

// Notebook password hashes: "pbkdf2:<iterations>:<saltB64>:<hashB64>" (PBKDF2-HMAC-SHA256).
// Older versions stored "<saltB64>:<hashB64>" (one salted SHA-256); verify() still accepts it so nobody is locked out,
// and callers re-hash with createHash() after a successful verify when needsRehash() is true.
// createHash() and verify() run PBKDF2, so call them off the main thread.
object NotebookPasswordHasher {
    private const val PBKDF2_PREFIX = "pbkdf2"
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32

    fun createHash(password: String): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val iterations = Pbkdf2.DEFAULT_ITERATIONS
        val hash = Pbkdf2.deriveKey(password, salt, iterations, HASH_BYTES)
        return "$PBKDF2_PREFIX:$iterations:${encode(salt)}:${encode(hash)}"
    }

    // True when the stored value is a notebook-password hash (either format), not a commons PIN/pattern hash
    fun isPasswordHash(stored: String) = parsePbkdf2(stored) != null || parseSha256(stored) != null

    @Deprecated("Matches both hash formats, not only legacy ones", ReplaceWith("isPasswordHash(stored)"))
    fun isLegacyHash(stored: String) = isPasswordHash(stored)

    fun needsRehash(stored: String): Boolean {
        val pbkdf2 = parsePbkdf2(stored) ?: return parseSha256(stored) != null
        return pbkdf2.iterations < Pbkdf2.DEFAULT_ITERATIONS
    }

    fun verify(password: String, stored: String): Boolean {
        parsePbkdf2(stored)?.let { parsed ->
            val actual = Pbkdf2.deriveKey(password, parsed.salt, parsed.iterations, parsed.hash.size)
            return MessageDigest.isEqual(parsed.hash, actual)
        }
        parseSha256(stored)?.let { parsed ->
            return MessageDigest.isEqual(parsed.hash, sha256(parsed.salt, password))
        }
        return false
    }

    private class ParsedHash(val iterations: Int, val salt: ByteArray, val hash: ByteArray)

    private fun parsePbkdf2(stored: String): ParsedHash? {
        val parts = stored.split(':')
        if (parts.size != 4 || parts[0] != PBKDF2_PREFIX) return null
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val salt = decode(parts[2]) ?: return null
        val hash = decode(parts[3]) ?: return null
        return if (salt.size == SALT_BYTES && hash.isNotEmpty()) ParsedHash(iterations, salt, hash) else null
    }

    private fun parseSha256(stored: String): ParsedHash? {
        val parts = stored.split(':', limit = 2)
        if (parts.size != 2) return null
        val salt = decode(parts[0]) ?: return null
        val hash = decode(parts[1]) ?: return null
        return if (salt.size == SALT_BYTES && hash.isNotEmpty()) ParsedHash(1, salt, hash) else null
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String) = runCatching { Base64.decode(text, Base64.NO_WRAP) }.getOrNull()

    private fun sha256(salt: ByteArray, password: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(password.toByteArray(Charsets.UTF_8))
        return digest.digest()
    }
}
