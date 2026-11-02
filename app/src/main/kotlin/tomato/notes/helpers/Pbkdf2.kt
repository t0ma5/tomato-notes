package tomato.notes.helpers

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// PBKDF2-HMAC-SHA256 (RFC 8018). SecretKeyFactory "PBKDF2WithHmacSHA256" needs API 26, minSdk is 23.
// Slow by design: never call it on the main thread.
object Pbkdf2 {
    const val DEFAULT_ITERATIONS = 310000
    private const val HMAC = "HmacSHA256"

    fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray {
        require(iterations > 0 && keyLengthBytes > 0)
        // HMAC zero-pads the key, so an empty password equals a single zero byte (SecretKeySpec rejects empty keys)
        val passwordBytes = password.toByteArray(Charsets.UTF_8).takeIf { it.isNotEmpty() } ?: byteArrayOf(0)
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(passwordBytes, HMAC))
        passwordBytes.fill(0)

        val hashLength = mac.macLength
        val blocks = (keyLengthBytes + hashLength - 1) / hashLength
        val result = ByteArray(keyLengthBytes)
        val u = ByteArray(hashLength)
        val t = ByteArray(hashLength)
        for (block in 1..blocks) {
            // U1 = PRF(P, S || INT(i))
            mac.update(salt)
            mac.update(
                byteArrayOf(
                    (block ushr 24).toByte(),
                    (block ushr 16).toByte(),
                    (block ushr 8).toByte(),
                    block.toByte()
                )
            )
            mac.doFinal(u, 0)
            u.copyInto(t)
            for (i in 1 until iterations) {
                mac.update(u)
                mac.doFinal(u, 0)
                for (j in t.indices) {
                    t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                }
            }
            val offset = (block - 1) * hashLength
            t.copyInto(result, offset, 0, minOf(hashLength, keyLengthBytes - offset))
        }
        return result
    }
}
