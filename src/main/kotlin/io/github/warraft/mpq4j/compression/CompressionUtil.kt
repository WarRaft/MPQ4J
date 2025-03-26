package io.github.warraft.mpq4j.compression

import java.nio.ByteBuffer

/**
 * Created by Frotty on 30.04.2017.
 */
object CompressionUtil {
    private var ADPCM: ADPCM? = null
    private var huffman: Huffman? = null
    private var zopfli: ZopfliHelper? = null

    /* Masks for Decompression Type 2 */
    private const val FLAG_HUFFMAN: Byte = 0x01
    const val FLAG_DEFLATE: Byte = 0x02

    // 0x04 is unknown
    private const val FLAG_IMPLODE: Byte = 0x08
    private const val FLAG_BZIP2: Byte = 0x10
    private const val FLAG_SPARSE: Byte = 0x20
    private const val FLAG_ADPCM1C: Byte = 0x40
    private const val FLAG_ADPCM2C: Byte = -0x80
    private const val FLAG_LMZA: Byte = 0x12

    fun compress(temp: ByteArray, recompress: RecompressOptions): ByteArray? {
        if (recompress.recompress && recompress.useZopfli && zopfli == null) {
            zopfli = ZopfliHelper()
        }
        return if (recompress.useZopfli) zopfli!!.deflate(temp, recompress.iterations) else JzLibHelper.deflate(
            temp,
            recompress.recompress
        )
    }

    @Throws(Exception::class)
    fun decompress(sector: ByteArray, compressedSize: Int, uncompressedSize: Int): ByteArray {
        if (compressedSize == uncompressedSize) {
            return sector
        } else {
            val compressionType = sector[0]
            val out = ByteBuffer.wrap(ByteArray(uncompressedSize))
            val `in` = ByteBuffer.wrap(sector)
            `in`.position(1)

            var flip = false
            val isLZMACompressed = (compressionType.toInt() and FLAG_LMZA.toInt()) != 0
            val isBzip2Compressed = (compressionType.toInt() and FLAG_BZIP2.toInt()) != 0
            val isImploded = (compressionType.toInt() and FLAG_IMPLODE.toInt()) != 0
            val isSparseCompressed = (compressionType.toInt() and FLAG_SPARSE.toInt()) != 0
            val isDeflated = (compressionType.toInt() and FLAG_DEFLATE.toInt()) != 0
            val isHuffmanCompressed = (compressionType.toInt() and FLAG_HUFFMAN.toInt()) != 0

            if (isDeflated) {
                out.put(JzLibHelper.inflate(sector, 1, uncompressedSize))
                out.position(0)
                flip = !flip
            } else if (isLZMACompressed) {
                throw Exception("Unsupported compression LZMA")
            } else if (isBzip2Compressed) {
                throw Exception("Unsupported compression Bzip2")
            } else if (isImploded) {
                val output = ByteArray(uncompressedSize)
                Exploder(sector, output, 1)
                out.put(output)
                out.position(0)
                flip = !flip
            }
            if (isSparseCompressed) {
                throw Exception("Unsupported compression sparse")
            }

            if (isHuffmanCompressed) {
                if (huffman == null) {
                    huffman = Huffman()
                }
                (if (flip) `in` else out).clear()
                huffman!!.decompress(if (flip) out else `in`, if (flip) `in` else out)
                out.limit(out.position())
                `in`.position(0)
                out.position(0)
                flip = !flip
            }
            if (((compressionType.toInt() and FLAG_ADPCM2C.toInt()) != 0)) {
                if (ADPCM == null) {
                    ADPCM = ADPCM(2)
                }
                val newOut = ByteBuffer.wrap(ByteArray(uncompressedSize))
                ADPCM!!.decompress(if (flip) out else `in`, newOut, 2)
                (if (flip) out else `in`).position(0)
                return newOut.array()
            }
            if (((compressionType.toInt() and FLAG_ADPCM1C.toInt()) != 0)) {
                if (ADPCM == null) {
                    ADPCM = ADPCM(2)
                }
                val newOut = ByteBuffer.wrap(ByteArray(uncompressedSize))
                ADPCM!!.decompress(if (flip) out else `in`, newOut, 1)
                (if (flip) out else `in`).position(0)
                return newOut.array()
            }
            return (if (flip) out else `in`).array()
        }
    }

    fun explode(sector: ByteArray, compressedSize: Int, uncompressedSize: Int): ByteArray {
        if (compressedSize == uncompressedSize) {
            return sector
        } else {
            val out = ByteBuffer.wrap(ByteArray(uncompressedSize))

            val output = ByteArray(uncompressedSize)
            Exploder(sector, output, 0)
            out.put(output)
            out.position(0)
            return out.array()
        }
    }
}
