package com.example.util

import java.io.InputStream
import java.security.MessageDigest

object CryptoUtil {
    fun calculateSha256(inputStream: InputStream): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
            bytesToHex(digest.digest())
        } catch (e: Exception) {
            "HASH_ERROR"
        } finally {
            try {
                inputStream.close()
            } catch (_: Exception) {}
        }
    }

    fun calculateSha256(bytes: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(bytes)
            bytesToHex(digest.digest())
        } catch (e: Exception) {
            "HASH_ERROR"
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun shortenHash(hash: String, length: Int = 8): String {
        return if (hash.length > length * 2) {
            "${hash.take(length)}...${hash.takeLast(length)}"
        } else {
            hash
        }
    }
}
