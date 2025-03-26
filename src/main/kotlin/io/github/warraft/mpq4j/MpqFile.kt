package io.github.warraft.mpq4j

import io.github.warraft.mpq4j.compression.CompressionUtil
import io.github.warraft.mpq4j.compression.RecompressOptions
import io.github.warraft.mpq4j.security.MPQEncryption
import io.github.warraft.mpq4j.security.MPQHashGenerator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.*
import java.nio.file.Files
import kotlin.math.ceil

class MpqFile(
    private val buf: ByteBuffer,
    private val block: BlockTable.Block,
    private val sectorSize: Int,
    val name: String,
) {
    private var isEncrypted = false
    private val compressedSize: Int
    private val normalSize: Int
    val flags: Int
    private val sectorCount: Int
    private var baseKey: Int

    init {
        this.compressedSize = block.compressedSize
        this.normalSize = block.normalSize
        this.flags = block.flags
        this.sectorCount = (ceil((normalSize.toDouble() / sectorSize.toDouble())) + 1).toInt()
        this.baseKey = 0
        val sepIndex = name.lastIndexOf('\\')
        val pathlessName = name.substring(sepIndex + 1)
        if (block.hasFlag(ENCRYPTED)) {
            isEncrypted = true
            val keyGen = MPQHashGenerator.Companion.getFileKeyGenerator()
            keyGen.process(pathlessName)
            baseKey = keyGen.hash
            if (block.hasFlag(ADJUSTED_ENCRYPTED)) {
                baseKey = ((baseKey + block.getFilePos()) xor block.normalSize)
            }
        }
    }

    @Throws(Exception::class)
    fun extractToFile(f: File) {
        if (sectorCount == 1) {
            f.createNewFile()
        }
        extractToOutputStream(Files.newOutputStream(f.toPath()))
    }

    @Throws(Exception::class)
    fun extractToBytes(): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        extractToOutputStream(byteArrayOutputStream)
        val bytes = byteArrayOutputStream.toByteArray()
        byteArrayOutputStream.close()
        return bytes
    }

    @Throws(Exception::class)
    fun extractToOutputStream(writer: OutputStream) {
        if (sectorCount == 1) {
            writer.close()
            return
        }
        if (extractImplodedBlock(writer)) return
        if (extractSingleUnitBlock(writer)) return
        if (block.hasFlag(COMPRESSED)) {
            extractCompressedBlock(writer)
        } else {
            check(writer)
        }
    }

    @Throws(Exception::class)
    private fun extractCompressedBlock(writer: OutputStream) {
        buf.position(0)
        val sot = ByteArray(sectorCount * 4)
        buf.get(sot)
        if (isEncrypted) {
            MPQEncryption(baseKey - 1, true).processSingle(ByteBuffer.wrap(sot))
        }
        val sotBuffer = ByteBuffer.wrap(sot).order(ByteOrder.LITTLE_ENDIAN)
        var start = sotBuffer.getInt()
        var end = sotBuffer.getInt()
        var finalSize = 0
        for (i in 0..<sectorCount - 1) {
            buf.position(start)
            var arr = getSectorAsByteArray(buf, end - start)
            if (isEncrypted) {
                MPQEncryption(baseKey + i, true).processSingle(ByteBuffer.wrap(arr))
            }
            arr = if (block.normalSize - finalSize <= sectorSize) {
                decompressSector(arr, end - start, block.normalSize - finalSize)
            } else {
                decompressSector(arr, end - start, sectorSize)
            }
            writer.write(arr)

            finalSize += sectorSize
            start = end
            try {
                end = sotBuffer.getInt()
            } catch (_: BufferUnderflowException) {
                break
            }
        }
        writer.flush()
        writer.close()
    }

    @Throws(Exception::class)
    private fun extractSingleUnitBlock(writer: OutputStream): Boolean {
        if (block.hasFlag(SINGLE_UNIT)) {
            if (block.hasFlag(COMPRESSED)) {
                buf.position(0)
                var arr = getSectorAsByteArray(buf, compressedSize)
                if (isEncrypted) {
                    MPQEncryption(baseKey, true).processSingle(ByteBuffer.wrap(arr))
                }
                arr = decompressSector(arr, block.compressedSize, block.normalSize)
                writer.write(arr)
                writer.flush()
                writer.close()
            } else {
                check(writer)
            }
            return true
        }
        return false
    }

    @Throws(Exception::class)
    private fun extractImplodedBlock(writer: OutputStream): Boolean {
        if (block.hasFlag(IMPLODED)) {
            buf.position(0)
            val sot = ByteArray(sectorCount * 4)
            buf.get(sot)
            if (isEncrypted) {
                MPQEncryption(baseKey - 1, true).processSingle(ByteBuffer.wrap(sot))
            }
            val sotBuffer = ByteBuffer.wrap(sot).order(ByteOrder.LITTLE_ENDIAN)
            var start = sotBuffer.getInt()
            var end = sotBuffer.getInt()
            var finalSize = 0
            for (i in 0..<sectorCount - 1) {
                buf.position(start)
                var arr = getSectorAsByteArray(buf, end - start)
                if (isEncrypted) {
                    MPQEncryption(baseKey + i, true).processSingle(ByteBuffer.wrap(arr))
                }
                arr = if (block.normalSize - finalSize <= sectorSize) {
                    decompressImplodedSector(arr, end - start, block.normalSize - finalSize)
                } else {
                    decompressImplodedSector(arr, end - start, sectorSize)
                }
                writer.write(arr)

                finalSize += sectorSize
                start = end
                try {
                    end = sotBuffer.getInt()
                } catch (_: BufferUnderflowException) {
                    break
                }
            }
            writer.flush()
            writer.close()
            return true
        }
        return false
    }

    
    private fun check(writer: OutputStream) {
        buf.position(0)
        val arr = getSectorAsByteArray(buf, compressedSize)
        if (isEncrypted) {
            MPQEncryption(baseKey, true).processSingle(ByteBuffer.wrap(arr))
        }
        writer.write(arr)
        writer.flush()
        writer.close()
    }

    /**
     * Write file and block.
     *
     * @param newBlock    the new block
     * @param writeBuffer the write buffer
     */
    fun writeFileAndBlock(newBlock: BlockTable.Block, writeBuffer: MappedByteBuffer) {
        newBlock.normalSize = normalSize
        newBlock.compressedSize = compressedSize
        if (normalSize == 0) {
            newBlock.flags = block.flags
            return
        }
        if ((block.hasFlag(SINGLE_UNIT)) || (!block.hasFlag(COMPRESSED))) {
            buf.position(0)
            val arr = getSectorAsByteArray(buf, if (block.hasFlag(COMPRESSED)) compressedSize else normalSize)
            if (block.hasFlag(ENCRYPTED)) {
                MPQEncryption(baseKey, true).processSingle(ByteBuffer.wrap(arr))
            }
            writeBuffer.put(arr)

            if (block.hasFlag(SINGLE_UNIT)) {
                if ((block.flags and COMPRESSED) == COMPRESSED) {
                    newBlock.flags = EXISTS or SINGLE_UNIT or COMPRESSED
                } else {
                    newBlock.flags = EXISTS or SINGLE_UNIT
                }
            } else {
                if ((block.flags and COMPRESSED) == COMPRESSED) {
                    newBlock.flags = EXISTS or COMPRESSED
                } else {
                    newBlock.flags = EXISTS
                }
            }
        } else {
            buf.position(0)
            val sot = ByteArray(sectorCount * 4)
            buf.get(sot)
            if (isEncrypted) {
                MPQEncryption(baseKey - 1, true).processSingle(ByteBuffer.wrap(sot))
            }
            writeBuffer.put(sot)
            val sotBuffer = ByteBuffer.wrap(sot).order(ByteOrder.LITTLE_ENDIAN)
            var start = sotBuffer.getInt()
            var end = sotBuffer.getInt()
            for (i in 0..<sectorCount - 1) {
                buf.position(start)
                val arr = getSectorAsByteArray(buf, end - start)
                if (isEncrypted) {
                    MPQEncryption(baseKey + i, true).processSingle(ByteBuffer.wrap(arr))
                }
                writeBuffer.put(arr)

                start = end
                try {
                    end = sotBuffer.getInt()
                } catch (_: BufferUnderflowException) {
                    break
                }
            }
            if ((block.flags and COMPRESSED) == COMPRESSED) {
                newBlock.flags = EXISTS or COMPRESSED
            } else {
                newBlock.flags = EXISTS
            }
        }
    }

    /**
     * Gets the sector as byte array.
     *
     * @param buf        the buf
     * @param sectorSize the sector size
     * @return the sector as byte array
     */
    private fun getSectorAsByteArray(buf: ByteBuffer, sectorSize: Int): ByteArray {
        val arr = ByteArray(sectorSize)
        buf.get(arr)
        return arr
    }

    /**
     * Decompress sector.
     *
     * @param sector           the sector
     * @param normalSize       the normal size
     * @param uncompressedSize the uncomp size
     * @return the byte[]
     */
    @Throws(Exception::class)
    private fun decompressSector(sector: ByteArray, normalSize: Int, uncompressedSize: Int): ByteArray {
        return CompressionUtil.decompress(sector, normalSize, uncompressedSize)
    }

    @Throws(Exception::class)
    private fun decompressImplodedSector(sector: ByteArray, normalSize: Int, uncompressedSize: Int): ByteArray {
        return CompressionUtil.explode(sector, normalSize, uncompressedSize)
    }

    override fun toString(): String {
        return ("MpqFile [sectorSize=" + sectorSize + ", compressedSize=" + compressedSize + ", normalSize=" + normalSize + ", flags=" + flags + ", name=" + name
                + "]")
    }

    companion object {
        const val COMPRESSED: Int = 0x00000200
        const val ENCRYPTED: Int = 0x00010000
        const val SINGLE_UNIT: Int = 0x01000000
        const val ADJUSTED_ENCRYPTED: Int = 0x00020000
        const val EXISTS: Int = -0x80000000
        const val DELETED: Int = 0x02000000
        const val IMPLODED: Int = 0x00000100

        fun writeFileAndBlock(
            file: ByteArray,
            b: BlockTable.Block,
            buf: MappedByteBuffer,
            sectorSize: Int,
            recompress: RecompressOptions,
        ) {
            writeFileAndBlock(file, b, buf, sectorSize, "", recompress)
        }

        fun writeFileAndBlock(
            fileArr: ByteArray,
            b: BlockTable.Block,
            buf: MappedByteBuffer,
            sectorSize: Int,
            pathlessName: String,
            recompress: RecompressOptions,
        ) {
            val fileBuf = ByteBuffer.wrap(fileArr)
            fileBuf.position(0)
            b.normalSize = fileArr.size
            if (b.flags == 0) {
                if (fileArr.isNotEmpty()) {
                    b.flags = EXISTS or COMPRESSED
                } else {
                    b.flags = EXISTS
                    return
                }
            }
            val sectorCount = (ceil((fileArr.size.toDouble() / sectorSize.toDouble())) + 1).toInt()
            val sot = ByteBuffer.allocate(sectorCount * 4)
            sot.order(ByteOrder.LITTLE_ENDIAN)
            sot.position(0)
            sot.putInt(sectorCount * 4)
            buf.position(sectorCount * 4)
            var sotPos = sectorCount * 4
            var temp = ByteArray(sectorSize)
            for (i in 0..<sectorCount - 1) {
                if (fileBuf.position() + sectorSize > fileArr.size) {
                    temp = ByteArray(fileArr.size - fileBuf.position())
                }
                fileBuf.get(temp)
                var compSector: ByteArray? = null
                try {
                    compSector = CompressionUtil.compress(temp, recompress)
                } catch (_: ArrayIndexOutOfBoundsException) {
                }
                if (compSector != null && compSector.size + 1 < temp.size) {
                    if (b.hasFlag(ENCRYPTED)) {
                        val keyGen = MPQHashGenerator.Companion.getFileKeyGenerator()
                        keyGen.process(pathlessName)
                        var bKey = keyGen.hash
                        if (b.hasFlag(ADJUSTED_ENCRYPTED)) {
                            bKey = ((bKey + b.getFilePos()) xor b.normalSize)
                        }

                        if (MPQEncryption(bKey + i, false).processFinal(
                                ByteBuffer.wrap(DebugHelper.appendData(2.toByte(), compSector), 0, compSector.size + 1), buf
                            )
                        ) throw BufferOverflowException()
                    } else {
                        // deflate compression indicator
                        buf.put(2.toByte())
                        buf.put(compSector)
                    }
                    sotPos += compSector.size + 1
                } else {
                    if (b.hasFlag(ENCRYPTED)) {
                        val keyGen = MPQHashGenerator.Companion.getFileKeyGenerator()
                        keyGen.process(pathlessName)
                        var bKey = keyGen.hash
                        if (b.hasFlag(ADJUSTED_ENCRYPTED)) {
                            bKey = ((bKey + b.getFilePos()) xor b.normalSize)
                        }
                        if (MPQEncryption(bKey + i, false).processFinal(
                                ByteBuffer.wrap(temp),
                                buf
                            )
                        ) throw BufferOverflowException()
                    } else {
                        buf.put(temp)
                    }
                    sotPos += temp.size
                }
                sot.putInt(sotPos)
            }
            b.compressedSize = sotPos
            buf.position(0)
            sot.position(0)
            buf.order(ByteOrder.LITTLE_ENDIAN)
            if (b.hasFlag(ENCRYPTED)) {
                val keyGen = MPQHashGenerator.Companion.getFileKeyGenerator()
                keyGen.process(pathlessName)
                var bKey = keyGen.hash
                if (b.hasFlag(ADJUSTED_ENCRYPTED)) {
                    bKey = ((bKey + b.getFilePos()) xor b.normalSize)
                }
                if (MPQEncryption(bKey - 1, false).processFinal(sot, buf)) throw BufferOverflowException()
            } else {
                buf.put(sot)
            }
        }
    }
}
