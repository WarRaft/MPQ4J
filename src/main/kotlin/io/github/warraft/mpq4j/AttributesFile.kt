package io.github.warraft.mpq4j

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.CRC32

class AttributesFile {
    val file: ByteArray

    val crc32: IntArray
    val timestamps: LongArray
    private val refMap = HashMap<String?, Int?>()

    private val crcGen = CRC32()

    constructor(entries: Int) {
        this.file = ByteArray(8 + 12 * entries)
        this.file[0] = 100 // Format Version
        this.file[4] = 3 // Attributes bytemask (crc,timestamp,[md5])
        crc32 = IntArray(entries)
        timestamps = LongArray(entries)
    }

    constructor(file: ByteArray) {
        this.file = file
        val buffer = ByteBuffer.wrap(file)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(8)
        val fileCount = (file.size - 8) / 12 - 1
        crc32 = IntArray(fileCount)
        timestamps = LongArray(fileCount)
        for (i in 0..<fileCount) {
            crc32[i] = buffer.getInt()
        }
        for (i in 0..<fileCount) {
            timestamps[i] = buffer.getLong()
        }
    }

    fun setEntry(i: Int, crc: Int, timestamp: Long) {
        crc32[i] = crc
        timestamps[i] = timestamp
    }

    fun buildFile(): ByteArray? {
        val buffer = ByteBuffer.wrap(file)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(8)
        for (crc in crc32) {
            buffer.putInt(crc)
        }
        for (timestamp in timestamps) {
            buffer.putLong(timestamp)
        }
        return buffer.array()
    }

    fun entries(): Int {
        return crc32.size
    }

    fun setNames(names: MutableList<String>) {
        var i = 0
        for (name in names) {
            refMap.put(name, i)
            i++
        }
    }

    fun getEntry(name: String?): Int {
        return refMap.getOrDefault(name, -1)!!
    }

    
    private fun getCrc32(file: File): Int {
        return getCrc32(Files.readAllBytes(file.toPath()))
    }

    fun getCrc32(bytes: ByteArray?): Int {
        crcGen.reset()
        crcGen.update(bytes)
        return crcGen.value.toInt()
    }
}
