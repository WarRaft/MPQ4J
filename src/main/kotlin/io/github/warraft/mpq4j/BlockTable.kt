package io.github.warraft.mpq4j

import systems.crigges.jmpq3.MpqFile
import systems.crigges.jmpq3.security.MPQEncryption
import java.io.IOException
import java.nio.BufferOverflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

class BlockTable(buf: ByteBuffer) {
    private val blockMap: ByteBuffer
    private val size: Int

    init {
        this.size = (buf.capacity() / 16)

        blockMap = ByteBuffer.allocate(buf.capacity())
        MPQEncryption(-326913117, true).processFinal(buf, blockMap)
        this.blockMap.order(ByteOrder.LITTLE_ENDIAN)
    }

    fun getBlockAtPos(pos: Int): Block {
        if ((pos < 0) || (pos > this.size)) {
            throw Exception("Invaild block position")
        }
        this.blockMap.position(pos * 16)
        try {
            return Block(this.blockMap)
        } catch (e: IOException) {
            throw Exception(e)
        }
    }

    val allVaildBlocks: ArrayList<Block?>
        get() {
            val list = java.util.ArrayList<Block?>()
            for (i in 0..<this.size) {
                val b = getBlockAtPos(i)
                if ((b.flags and -0x80000000) == -2147483648) {
                    list.add(b)
                }
            }
            return list
        }

    class Block {
        internal var filePos: Long

        @JvmField
        var compressedSize: Int

        @JvmField
        var normalSize: Int

        @JvmField
        var flags: Int

        constructor(buf: ByteBuffer) {
            this.filePos = buf.getInt().toLong()
            this.compressedSize = buf.getInt()
            this.normalSize = buf.getInt()
            this.flags = buf.getInt()
        }

        constructor(filePos: Long, compressedSize: Int, normalSize: Int, flags: Int) {
            this.filePos = filePos
            this.compressedSize = compressedSize
            this.normalSize = normalSize
            this.flags = flags
        }

        fun writeToBuffer(bb: ByteBuffer) {
            bb.putInt(this.filePos.toInt())
            bb.putInt(this.compressedSize)
            bb.putInt(this.normalSize)
            bb.putInt(this.flags)
        }

        fun getFilePos(): Int {
            return this.filePos.toInt()
        }

        fun setFilePos(filePos: Int) {
            this.filePos = filePos.toLong()
        }

        fun hasFlag(flag: Int): Boolean {
            return (flags and flag) == flag
        }

        override fun toString(): String {
            return "Block [filePos=" + this.filePos + ", compressedSize=" + this.compressedSize + ", normalSize=" + this.normalSize + ", flags=" +
                    printFlags().trim { it <= ' ' } + "]"
        }

        fun printFlags(): String {
            return ((if (hasFlag(MpqFile.EXISTS)) "EXISTS " else "") + (if (hasFlag(MpqFile.SINGLE_UNIT)) "SINGLE_UNIT " else "") + (if (hasFlag(MpqFile.COMPRESSED)) "COMPRESSED " else "")
                    + (if (hasFlag(MpqFile.ENCRYPTED)) "ENCRYPTED " else "") + (if (hasFlag(MpqFile.ADJUSTED_ENCRYPTED)) "ADJUSTED " else "") + (if (hasFlag(MpqFile.DELETED)) "DELETED " else ""))
        }
    }

    companion object {
        fun writeNewBlocktable(blocks: MutableList<Block?>, size: Int, buf: MappedByteBuffer?) {
            val temp = ByteBuffer.allocate(size * 16)
            temp.order(ByteOrder.LITTLE_ENDIAN)
            for (b in blocks) {
                b?.writeToBuffer(temp)
            }
            temp.clear()
            if (MPQEncryption(-326913117, false).processFinal(temp, buf)) throw BufferOverflowException()
        }
    }
}
