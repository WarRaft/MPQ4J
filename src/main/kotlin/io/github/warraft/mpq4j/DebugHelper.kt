package io.github.warraft.mpq4j

import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.min

object DebugHelper {
    internal val hexArray: CharArray = "0123456789ABCDEF".toCharArray()

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 3)
        for (j in 0..<min(bytes.size.toDouble(), 500.0).toInt()) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[(j * 3)] = hexArray[(v ushr 4)]
            hexChars[(j * 3 + 1)] = hexArray[(v and 0xF)]
            hexChars[(j * 3 + 2)] = ' '
        }
        return String(hexChars).trim { it <= ' ' }
    }

    fun appendData(firstObject: Byte, secondObject: ByteArray?): ByteArray? {
        val byteArray = byteArrayOf(firstObject)
        return appendData(byteArray, secondObject)
    }

    fun appendData(firstObject: ByteArray?, secondObject: ByteArray?): ByteArray? {
        val outputStream = ByteArrayOutputStream()
        try {
            if (firstObject != null && firstObject.isNotEmpty()) outputStream.write(firstObject)
            if (secondObject != null && secondObject.isNotEmpty()) outputStream.write(secondObject)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return outputStream.toByteArray()
    }
}
