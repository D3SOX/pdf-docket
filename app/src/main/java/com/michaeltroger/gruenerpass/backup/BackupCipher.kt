package com.michaeltroger.gruenerpass.backup

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.io.CipherInputStream
import org.bouncycastle.crypto.io.CipherOutputStream
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

private val MAGIC = "PDFWALLET".toByteArray(Charsets.US_ASCII)
private const val FORMAT_VERSION = 1
// 32 MiB of memory, which even old phones can spare
private const val SCRYPT_LOG_N = 15
private const val SCRYPT_R = 8
private const val SCRYPT_P = 1
// rejects files that would make the key derivation take ages or run out of memory.
// scrypt needs 128 * r * N bytes.
private const val MAX_SCRYPT_LOG_N = 20
private const val MAX_SCRYPT_R = 16
private const val MAX_SCRYPT_P = 4
private const val MAX_SCRYPT_MEMORY = 64L * 1024 * 1024
private const val SCRYPT_BLOCK_BYTES = 128L
private const val SALT_SIZE = 16
private const val NONCE_SIZE = 12
private const val KEY_SIZE = 32
private const val TAG_SIZE_BITS = 128
private val HEADER_SIZE = MAGIC.size + 4 + SALT_SIZE + NONCE_SIZE

class NotABackupException : IOException()

private class Header(
    val bytes: ByteArray,
    val logN: Int,
    val r: Int,
    val p: Int,
    val salt: ByteArray,
    val nonce: ByteArray,
)

/**
 * Encrypts backups with AES-256-GCM, using a key derived from the password with scrypt.
 *
 * File layout: magic, format version, scrypt log2(N), r and p (1 byte each), salt, nonce, ciphertext with the tag.
 * The header is authenticated as additional data.
 */
object BackupCipher {

    private val random = SecureRandom()

    /** Writes the header to [output] and returns a stream that encrypts into it. Closing it writes the tag. */
    fun encrypt(output: OutputStream, password: String): OutputStream {
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val header = MAGIC +
            byteArrayOf(FORMAT_VERSION.toByte(), SCRYPT_LOG_N.toByte(), SCRYPT_R.toByte(), SCRYPT_P.toByte()) +
            salt +
            nonce
        output.write(header)
        val key = deriveKey(password, salt, SCRYPT_LOG_N, SCRYPT_R, SCRYPT_P)
        return CipherOutputStream(output, cipher(forEncryption = true, key, nonce, header))
    }

    /**
     * Reads the header from [input] and returns a stream of the decrypted content.
     * The content is only authentic once the stream was read to the end without an
     * [org.bouncycastle.crypto.io.InvalidCipherTextIOException], which a wrong password also causes.
     *
     * @throws NotABackupException if [input] doesn't start with a backup header
     */
    fun decrypt(input: InputStream, password: String): InputStream {
        val header = readHeader(input) ?: throw NotABackupException()
        val key = deriveKey(password, header.salt, header.logN, header.r, header.p)
        return CipherInputStream(input, cipher(forEncryption = false, key, header.nonce, header.bytes))
    }

    @Suppress("SwallowedException")
    private fun readHeader(input: InputStream): Header? {
        val bytes = ByteArray(HEADER_SIZE)
        try {
            DataInputStream(input).readFully(bytes)
        } catch (e: EOFException) {
            return null
        }
        var offset = MAGIC.size
        val isBackup = bytes.copyOfRange(0, offset).contentEquals(MAGIC) && bytes[offset++].toInt() == FORMAT_VERSION
        val header = Header(
            bytes = bytes,
            logN = bytes[offset++].toInt(),
            r = bytes[offset++].toInt(),
            p = bytes[offset++].toInt(),
            salt = bytes.copyOfRange(offset, offset + SALT_SIZE),
            nonce = bytes.copyOfRange(offset + SALT_SIZE, HEADER_SIZE),
        )
        val hasSaneParameters = header.logN in 1..MAX_SCRYPT_LOG_N &&
            header.r in 1..MAX_SCRYPT_R &&
            header.p in 1..MAX_SCRYPT_P &&
            (SCRYPT_BLOCK_BYTES * header.r shl header.logN) <= MAX_SCRYPT_MEMORY
        return header.takeIf { isBackup && hasSaneParameters }
    }

    private fun deriveKey(password: String, salt: ByteArray, logN: Int, r: Int, p: Int): ByteArray =
        SCrypt.generate(password.toByteArray(Charsets.UTF_8), salt, 1 shl logN, r, p, KEY_SIZE)

    private fun cipher(forEncryption: Boolean, key: ByteArray, nonce: ByteArray, header: ByteArray) =
        GCMBlockCipher.newInstance(AESEngine.newInstance()).apply {
            init(forEncryption, AEADParameters(KeyParameter(key), TAG_SIZE_BITS, nonce, header))
        }
}
