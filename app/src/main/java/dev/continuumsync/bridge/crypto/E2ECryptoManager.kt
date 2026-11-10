package dev.continuumsync.bridge.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object E2ECryptoManager {

    private const val ALGORITHM = "ChaCha20-Poly1305"
    private const val NONCE_LEN_BYTES = 12
    private val secureRandom = SecureRandom()
    private var sharedSessionKey: SecretKey? = null

    fun initializeWithDerivedKey(rawKeyBytes: ByteArray) {
        require(rawKeyBytes.size == 32) { "ChaCha20-Poly1305 requires a 256-bit key" }
        sharedSessionKey = SecretKeySpec(rawKeyBytes, "ChaCha20")
    }

    fun seal(
        plaintextUtf8: ByteArray,
        associatedDataDeviceId: ByteArray = "ContinuumSyncV1".toByteArray(Charsets.UTF_8)
    ): ByteArray {
        val key = checkNotNull(sharedSessionKey) { "Device pairing key not initialized" }
        val nonce = ByteArray(NONCE_LEN_BYTES).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(nonce))
        cipher.updateAAD(associatedDataDeviceId)

        val ciphertextWithTag = cipher.doFinal(plaintextUtf8)
        return nonce + ciphertextWithTag
    }

    fun open(
        frameBytes: ByteArray,
        expectedDeviceId: ByteArray = "ContinuumSyncV1".toByteArray(Charsets.UTF_8)
    ): ByteArray {
        require(frameBytes.size > NONCE_LEN_BYTES + 16) { "Truncated AEAD frame" }
        val key = checkNotNull(sharedSessionKey) { "Device pairing key not initialized" }

        val nonce = frameBytes.copyOfRange(0, NONCE_LEN_BYTES)
        val ciphertextWithTag = frameBytes.copyOfRange(NONCE_LEN_BYTES, frameBytes.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(nonce))
        cipher.updateAAD(expectedDeviceId)

        return cipher.doFinal(ciphertextWithTag)
    }
}
