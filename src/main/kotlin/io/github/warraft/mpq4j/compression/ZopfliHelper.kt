package io.github.warraft.mpq4j.compression

import ru.eustas.zopfli.Options
import ru.eustas.zopfli.Zopfli
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Created by Frotty on 09.05.2017.
 */
class ZopfliHelper {
    private val compressor: Zopfli = Zopfli(4 * 1024 * 1024)

    fun deflate(bytes: ByteArray, iterations: Int): ByteArray? {
        try {
            ByteArrayOutputStream().use { byteArrayOutputStream ->
                compressor.compress(
                    Options(
                        Options.OutputFormat.ZLIB,
                        Options.BlockSplitting.FIRST, iterations
                    ),
                    bytes, byteArrayOutputStream
                )
                return byteArrayOutputStream.toByteArray()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }
}
