import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Created by Frotty on 11.06.2017.
 */
object TestHelper {
    // stolen from http://stackoverflow.com/a/304350/303637
    fun md5(f: File): String {
        try {
            val buf = ByteArray(1024)
            val md = MessageDigest.getInstance("MD5")
            Files.newInputStream(f.toPath()).use { `is` ->
                DigestInputStream(`is`, md).use { dis ->
                    while (dis.read(buf) >= 0);
                }
            }
            val digest = md.digest()
            return bytesToHex(digest)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    // stolen from http://stackoverflow.com/a/9855338/303637
    internal val hexArray: CharArray = "0123456789abcdef".toCharArray()

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        var v: Int
        for (j in bytes.indices) {
            v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
