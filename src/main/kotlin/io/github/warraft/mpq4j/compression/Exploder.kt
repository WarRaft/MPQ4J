package io.github.warraft.mpq4j.compression

/*
 Taken from: https://github.com/horschi/OpenTeufel/blob/master/src/main/java/org/openteufel/file/mpq/explode/Exploder.java

 Modifications: Removed unused variables, made the static arrays private and changed formatting, set pInPos to 1

 *************

 Sources:
 https://github.com/ladislav-zezula/StormLib/blob/master/src/pklib/explode.c
 https://github.com/toshok/scsharp/blob/master/SCSharp/SCSharp.Mpq/PKLibDecompress.cs

 https://code.google.com/p/arx-fatalis-fixed/source/browse/trunk/Sources/HERMES/explode.c?r=23
 https://github.com/dcramer/ghostplusplus-nibbits/blob/master/StormLib/stormlib/pklib/explode.c
 https://code.google.com/p/stormlibsharp/source/browse/trunk/development/stormlib/src/pklib/explode.c?r=2
 http://yumiko.svnrepository.com/TrinityCore/trac.cgi/browser/trunk/contrib/vmap_extractor_v2/stormlib/pklib/explode.c

*/

class Exploder(pInBuffer: ByteArray, val pOutBuffer: ByteArray, inPos: Int) {
    var nBitBuffer: Long

    var pDictPos: Int = 0
    var nCurDictSize: Int = 0

    var dict: ByteArray

    var nDictSize: Int
    var nLitSize: Byte
    var nDictSizeByte: Byte

    var nBits: Byte = 16
    var nCopyLen: Int = 0
    var pOutPos: Int = 0

    var pInPos: Int

    init {
        require(pInBuffer.size >= 4) { "PK_ERR_INCOMPLETE_INPUT: Incomplete input" }

        this.pInPos = inPos

        nLitSize = pInBuffer[pInPos++]
        nDictSizeByte = pInBuffer[pInPos++]

        require(!(nLitSize.toInt() != 0 && nLitSize.toInt() != 1)) { "PK_ERR_BAD_DATA: Invalid LitSize: $nLitSize" }

        require(!(4 > nDictSizeByte || nDictSizeByte > 6)) { "PK_ERR_BAD_DATA: Invalid DictSizeByte: $nDictSizeByte" }

        nDictSize = 64 shl nDictSizeByte.toInt()

        dict = ByteArray(0x1000)

        nBitBuffer = (pInBuffer[pInPos++].toLong() and 0xFFL)
        nBitBuffer += ((pInBuffer[pInPos++].toLong() and 0xFFL) shl 0x8)
    }


    fun truncateValue(value: Long, bits: Int): Long {
        return ((value) and ((1L shl (bits)) - 1))
    }

    // Bit sequences used to represent literal bytes
    val chCode: ShortArray = shortArrayOf(
        0x0490, 0x0FE0, 0x07E0, 0x0BE0, 0x03E0, 0x0DE0, 0x05E0, 0x09E0,
        0x01E0, 0x00B8, 0x0062, 0x0EE0, 0x06E0, 0x0022, 0x0AE0, 0x02E0,
        0x0CE0, 0x04E0, 0x08E0, 0x00E0, 0x0F60, 0x0760, 0x0B60, 0x0360,
        0x0D60, 0x0560, 0x1240, 0x0960, 0x0160, 0x0E60, 0x0660, 0x0A60,
        0x000F, 0x0250, 0x0038, 0x0260, 0x0050, 0x0C60, 0x0390, 0x00D8,
        0x0042, 0x0002, 0x0058, 0x01B0, 0x007C, 0x0029, 0x003C, 0x0098,
        0x005C, 0x0009, 0x001C, 0x006C, 0x002C, 0x004C, 0x0018, 0x000C,
        0x0074, 0x00E8, 0x0068, 0x0460, 0x0090, 0x0034, 0x00B0, 0x0710,
        0x0860, 0x0031, 0x0054, 0x0011, 0x0021, 0x0017, 0x0014, 0x00A8,
        0x0028, 0x0001, 0x0310, 0x0130, 0x003E, 0x0064, 0x001E, 0x002E,
        0x0024, 0x0510, 0x000E, 0x0036, 0x0016, 0x0044, 0x0030, 0x00C8,
        0x01D0, 0x00D0, 0x0110, 0x0048, 0x0610, 0x0150, 0x0060, 0x0088,
        0x0FA0, 0x0007, 0x0026, 0x0006, 0x003A, 0x001B, 0x001A, 0x002A,
        0x000A, 0x000B, 0x0210, 0x0004, 0x0013, 0x0032, 0x0003, 0x001D,
        0x0012, 0x0190, 0x000D, 0x0015, 0x0005, 0x0019, 0x0008, 0x0078,
        0x00F0, 0x0070, 0x0290, 0x0410, 0x0010, 0x07A0, 0x0BA0, 0x03A0,
        0x0240, 0x1C40, 0x0C40, 0x1440, 0x0440, 0x1840, 0x0840, 0x1040,
        0x0040, 0x1F80, 0x0F80, 0x1780, 0x0780, 0x1B80, 0x0B80, 0x1380,
        0x0380, 0x1D80, 0x0D80, 0x1580, 0x0580, 0x1980, 0x0980, 0x1180,
        0x0180, 0x1E80, 0x0E80, 0x1680, 0x0680, 0x1A80, 0x0A80, 0x1280,
        0x0280, 0x1C80, 0x0C80, 0x1480, 0x0480, 0x1880, 0x0880, 0x1080,
        0x0080, 0x1F00, 0x0F00, 0x1700, 0x0700, 0x1B00, 0x0B00, 0x1300,
        0x0DA0, 0x05A0, 0x09A0, 0x01A0, 0x0EA0, 0x06A0, 0x0AA0, 0x02A0,
        0x0CA0, 0x04A0, 0x08A0, 0x00A0, 0x0F20, 0x0720, 0x0B20, 0x0320,
        0x0D20, 0x0520, 0x0920, 0x0120, 0x0E20, 0x0620, 0x0A20, 0x0220,
        0x0C20, 0x0420, 0x0820, 0x0020, 0x0FC0, 0x07C0, 0x0BC0, 0x03C0,
        0x0DC0, 0x05C0, 0x09C0, 0x01C0, 0x0EC0, 0x06C0, 0x0AC0, 0x02C0,
        0x0CC0, 0x04C0, 0x08C0, 0x00C0, 0x0F40, 0x0740, 0x0B40, 0x0340,
        0x0300, 0x0D40, 0x1D00, 0x0D00, 0x1500, 0x0540, 0x0500, 0x1900,
        0x0900, 0x0940, 0x1100, 0x0100, 0x1E00, 0x0E00, 0x0140, 0x1600,
        0x0600, 0x1A00, 0x0E40, 0x0640, 0x0A40, 0x0A00, 0x1200, 0x0200,
        0x1C00, 0x0C00, 0x1400, 0x0400, 0x1800, 0x0800, 0x1000, 0x0000
    )

    // Lengths of bit sequences used to represent literal bytes
    val chBits: ByteArray = byteArrayOf(
        0x0B, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x08, 0x07, 0x0C, 0x0C, 0x07, 0x0C, 0x0C,
        0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0D, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C,
        0x04, 0x0A, 0x08, 0x0C, 0x0A, 0x0C, 0x0A, 0x08, 0x07, 0x07, 0x08, 0x09, 0x07, 0x06, 0x07, 0x08,
        0x07, 0x06, 0x07, 0x07, 0x07, 0x07, 0x08, 0x07, 0x07, 0x08, 0x08, 0x0C, 0x0B, 0x07, 0x09, 0x0B,
        0x0C, 0x06, 0x07, 0x06, 0x06, 0x05, 0x07, 0x08, 0x08, 0x06, 0x0B, 0x09, 0x06, 0x07, 0x06, 0x06,
        0x07, 0x0B, 0x06, 0x06, 0x06, 0x07, 0x09, 0x08, 0x09, 0x09, 0x0B, 0x08, 0x0B, 0x09, 0x0C, 0x08,
        0x0C, 0x05, 0x06, 0x06, 0x06, 0x05, 0x06, 0x06, 0x06, 0x05, 0x0B, 0x07, 0x05, 0x06, 0x05, 0x05,
        0x06, 0x0A, 0x05, 0x05, 0x05, 0x05, 0x08, 0x07, 0x08, 0x08, 0x0A, 0x0B, 0x0B, 0x0C, 0x0C, 0x0C,
        0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D,
        0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D,
        0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D,
        0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C,
        0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C,
        0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C, 0x0C,
        0x0D, 0x0C, 0x0D, 0x0D, 0x0D, 0x0C, 0x0D, 0x0D, 0x0D, 0x0C, 0x0D, 0x0D, 0x0D, 0x0D, 0x0C, 0x0D,
        0x0D, 0x0D, 0x0C, 0x0C, 0x0C, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D
    )

    // Bit sequences used to represent the base values of the copy length
    val lenCode: ByteArray = byteArrayOf(
        0x05, 0x03, 0x01, 0x06, 0x0A, 0x02, 0x0C, 0x14, 0x04, 0x18, 0x08, 0x30, 0x10, 0x20, 0x40, 0x00
    )

    // Lengths of bit sequences used to represent the base values of the copy length
    val lenBits: ByteArray = byteArrayOf(
        0x03, 0x02, 0x03, 0x03, 0x04, 0x04, 0x04, 0x05, 0x05, 0x05, 0x05, 0x06, 0x06, 0x06, 0x07, 0x07
    )

    // Base values used for the copy length
    val lenBase: ShortArray = shortArrayOf(
        0x0002, 0x0003, 0x0004, 0x0005, 0x0006, 0x0007, 0x0008, 0x0009,
        0x000A, 0x000C, 0x0010, 0x0018, 0x0028, 0x0048, 0x0088, 0x0108
    )

    // Lengths of extra bits used to represent the copy length
    val exLenBits: ByteArray = byteArrayOf(
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08
    )

    // Bit sequences used to represent the most significant 6 bits of the copy offset
    val offsCode: ByteArray = byteArrayOf(
        0x03, 0x0D, 0x05, 0x19, 0x09, 0x11, 0x01, 0x3E, 0x1E, 0x2E, 0x0E, 0x36, 0x16, 0x26, 0x06, 0x3A,
        0x1A, 0x2A, 0x0A, 0x32, 0x12, 0x22, 0x42, 0x02, 0x7C, 0x3C, 0x5C, 0x1C, 0x6C, 0x2C, 0x4C, 0x0C,
        0x74, 0x34, 0x54, 0x14, 0x64, 0x24, 0x44, 0x04, 0x78, 0x38, 0x58, 0x18, 0x68, 0x28, 0x48, 0x08,
        -16, 0x70, -80, 0x30, -48, 0x50, -112, 0x10, -32, 0x60, -96, 0x20, -64, 0x40,
        -128, 0x00
    )

    // Lengths of bit sequences used to represent the most significant 6 bits of the copy offset
    val offsBits: ByteArray = byteArrayOf(
        0x02, 0x04, 0x04, 0x05, 0x05, 0x05, 0x05, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06,
        0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07,
        0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07,
        0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08
    )


    init {
        while (pOutPos < pOutBuffer.size) {
            // Fill bit buffer with at least 16 bits
            while (nBits < 16) {
                // If input buffer is empty before end of stream, buffer is incomplete
                require(pInPos < pInBuffer.size) { "PK_ERR_INCOMPLETE_INPUT: Incomplete input" }

                nBitBuffer += ((pInBuffer[pInPos++].toInt() and 0xFF).toLong()) shl nBits.toInt()
                nBits = (nBits + 8).toByte()
            }


            // Index into tables
            if ((nBitBuffer and 1L) != 0L) {
                // Remove first bit from bit buffer

                nBitBuffer = nBitBuffer shr 1
                nBits--

                // Find the base value for the copy length
                var i = 0
                while (i <= 0x0F) {
                    if (truncateValue(
                            nBitBuffer,
                            lenBits[i].toInt() and 0xFF
                        ) == (lenCode[i].toInt() and 0xFF).toLong()
                    ) {
                        break
                    }
                    i++
                }

                // Remove value from bit buffer
                nBitBuffer = nBitBuffer shr (lenBits[i].toInt() and 0xFF)
                nBits = (nBits - (lenBits[i].toInt() and 0xFF).toByte()).toByte()

                // Store the copy length
                nCopyLen = ((lenBase[i].toInt() and 0xFFFF) + truncateValue(
                    nBitBuffer,
                    exLenBits[i].toInt() and 0xFF
                )).toInt() // Length of data to copy from the dictionary

                // Remove the extra bits from the bit buffer
                nBitBuffer = nBitBuffer shr (exLenBits[i].toInt() and 0xFF)
                nBits = (nBits - (exLenBits[i].toInt() and 0xFF).toByte()).toByte()

                // If copy length is 519, the end of the stream has been reached
                if (nCopyLen == 519) break

                // Fill bit buffer with at least 14 bits
                while (nBits < 14) {
                    // If input buffer is empty before end of stream, buffer is incomplete
                    require(pInPos < pInBuffer.size) { "PK_ERR_INCOMPLETE_INPUT: Incomplete input" }

                    nBitBuffer += (pInBuffer[pInPos++].toInt() and 0xFF).toLong() shl nBits.toInt()
                    nBits = (nBits + 8).toByte()
                }

                // Find most significant 6 bits of offset into the dictionary
                i = 0
                while (i <= 63) {
                    if (truncateValue(
                            nBitBuffer,
                            offsBits[i].toInt() and 0xFF
                        ) == (offsCode[i].toInt() and 0xFF).toLong()
                    ) {
                        break
                    }
                    i++
                }

                // Remove value from bit buffer
                nBitBuffer = nBitBuffer shr (offsBits[i].toInt() and 0xFF)
                nBits = (nBits - (offsBits[i].toInt() and 0xFF).toByte()).toByte()

                // If the copy length is 2, there are only two more bits in the dictionary
                // offset; otherwise, there are 4, 5, or 6 bits left, depending on what
                // the dictionary size is
                var pCopyOffs: Int // Offset to data to copy from the dictionary
                if (nCopyLen == 2) {
                    // Store the exact offset to a byte in the dictionary

                    pCopyOffs = (pDictPos - 1 - ((i shl 2) + (nBitBuffer and 3L))).toInt()

                    // Remove the rest of the dictionary offset from the bit buffer
                    nBitBuffer = nBitBuffer shr 2
                    nBits = (nBits - 2).toByte()
                } else {
                    // Store the exact offset to a byte in the dictionary

                    pCopyOffs = (pDictPos - 1 - ((i shl nDictSizeByte.toInt()) + truncateValue(
                        nBitBuffer,
                        nDictSizeByte.toInt()
                    ))).toInt()

                    // Remove the rest of the dictionary offset from the bit buffer
                    nBitBuffer = nBitBuffer shr nDictSizeByte.toInt()
                    nBits = (nBits - nDictSizeByte).toByte()
                }

                // While there are still bytes left, copy bytes from the dictionary
                while (nCopyLen > 0) {
                    nCopyLen--

                    // If output buffer has become full, stop immediately!
                    require(pOutPos < pOutBuffer.size) { "PK_ERR_BUFFER_TOO_SMALL: Output buffer is full: " + pOutPos + " / " + pOutBuffer.size }


                    // Check whether the offset is a valid one into the dictionary
                    while (pCopyOffs < 0) {
                        pCopyOffs += nCurDictSize
                    }
                    while (pCopyOffs >= nCurDictSize) {
                        pCopyOffs -= nCurDictSize
                    }

                    // Copy the byte from the dictionary and add it to the end of the dictionary
                    // *pDictPos++ = *pOutPos++ = *pCopyOffs++;
                    val a = dict[pCopyOffs++]
                    pOutBuffer[pOutPos++] = a
                    dict[pDictPos++] = a

                    // If the dictionary is not full yet, increment the current dictionary size
                    if (nCurDictSize < nDictSize) {
                        nCurDictSize++
                    }

                    // If the current end of the dictionary is past the end of the buffer,
                    // wrap around back to the start
                    if (pDictPos >= nDictSize) {
                        pDictPos = 0
                    }
                }
            } else {
                if (nLitSize.toInt() == 0) {
                    val a = (nBitBuffer shr 1).toByte()
                    pOutBuffer[pOutPos++] = a
                    dict[pDictPos++] = a
                    nBitBuffer = nBitBuffer shr 9
                    nBits = (nBits - 9).toByte()
                } else {
                    nBitBuffer = nBitBuffer shr 1
                    nBits--
                    var i = 0
                    while (i <= 0xFF) {
                        if (truncateValue(
                                nBitBuffer,
                                chBits[i].toInt() and 0xFF
                            ) == (chCode[i].toInt() and 0xFFFF).toLong()
                        ) {
                            break
                        }
                        i++
                    }

                    val a = i.toByte()
                    pOutBuffer[pOutPos++] = a
                    dict[pDictPos++] = a
                    nBitBuffer = nBitBuffer shr (chBits[i].toInt() and 0xFF)
                    nBits = (nBits - (chBits[i].toInt() and 0xFF).toByte()).toByte()
                }

                if (nCurDictSize < nDictSize) {
                    nCurDictSize++
                }

                if (pDictPos >= nDictSize) {
                    pDictPos = 0
                }
            }
        }
    }

}
