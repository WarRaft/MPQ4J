package systems.crigges.jmpq3.compression;
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

import static systems.crigges.jmpq3.compression.Explorer1.*;

public class Exploder {
    public Exploder(ExplorerData data) {
        while (data.pOutPos < data.pOutBuffer.length) {

            // Fill bit buffer with at least 16 bits
            while (data.nBits < 16) {
                // If input buffer is empty before end of stream, buffer is incomplete
                if (data.pInPos >= data.pInBuffer.length) {
                    // Store the current size of output
                    // nOutSize = pOutPos - pOutBuffer;
                    throw new IllegalArgumentException("PK_ERR_INCOMPLETE_INPUT: Incomplete input");
                }

                data.nBitBuffer += ((long) (data.pInBuffer[data.pInPos++] & 0xFF)) << data.nBits;
                data.nBits += 8;
            }


            // Index into tables
            if ((data.nBitBuffer & 1) != 0) {
                // First bit is 1; copy from dictionary

                // Remove first bit from bit buffer
                data.nBitBuffer >>= 1;
                data.nBits--;

                // Find the base value for the copy length
                int i;
                for (i = 0; i <= 0x0F; i++) {
                    if (TRUNCATE_VALUE(data.nBitBuffer, LenBits[i] & 0xFF) == (LenCode[i] & 0xFF)) {
                        break;
                    }
                }

                // Remove value from bit buffer
                data.nBitBuffer >>= LenBits[i] & 0xFF;
                data.nBits -= (byte) (LenBits[i] & 0xFF);

                // Store the copy length
                data.nCopyLen = (int) ((LenBase[i] & 0xFFFF) + TRUNCATE_VALUE(data.nBitBuffer, ExLenBits[i] & 0xFF)); // Length of data to copy from the dictionary

                // Remove the extra bits from the bit buffer
                data.nBitBuffer >>= ExLenBits[i] & 0xFF;
                data.nBits -= (byte) (ExLenBits[i] & 0xFF);

                // If copy length is 519, the end of the stream has been reached
                if (data.nCopyLen == 519) {
                    break;
                }

                // Fill bit buffer with at least 14 bits
                while (data.nBits < 14) {
                    // If input buffer is empty before end of stream, buffer is incomplete
                    if (data.pInPos >= data.pInBuffer.length) {
                        // Store the current size of output
                        // nOutSize = pOutPos - pOutBuffer;
                        throw new IllegalArgumentException("PK_ERR_INCOMPLETE_INPUT: Incomplete input");
                    }

                    data.nBitBuffer += (long) (data.pInBuffer[data.pInPos++] & 0xFF) << data.nBits;
                    data.nBits += 8;
                }

                // Find most significant 6 bits of offset into the dictionary
                for (i = 0; i <= 63; i++) {
                    if (TRUNCATE_VALUE(data.nBitBuffer, OffsBits[i] & 0xFF) == (OffsCode[i] & 0xFF)) {
                        break;
                    }
                }

                // Remove value from bit buffer
                data.nBitBuffer >>= OffsBits[i] & 0xFF;
                data.nBits -= (byte) (OffsBits[i] & 0xFF);

                // If the copy length is 2, there are only two more bits in the dictionary
                // offset; otherwise, there are 4, 5, or 6 bits left, depending on what
                // the dictionary size is
                int pCopyOffs; // Offset to data to copy from the dictionary
                if (data.nCopyLen == 2) {

                    // Store the exact offset to a byte in the dictionary
                    pCopyOffs = (int) (data.pDictPos - 1 - ((i << 2) + (data.nBitBuffer & 0b11)));

                    // Remove the rest of the dictionary offset from the bit buffer
                    data.nBitBuffer >>= 2;
                    data.nBits -= 2;
                } else {

                    // Store the exact offset to a byte in the dictionary
                    pCopyOffs = (int) (data.pDictPos - 1 - ((i << data.nDictSizeByte) + TRUNCATE_VALUE(data.nBitBuffer, data.nDictSizeByte)));

                    // Remove the rest of the dictionary offset from the bit buffer
                    data.nBitBuffer >>= data.nDictSizeByte;
                    data.nBits -= data.nDictSizeByte;
                }

                // While there are still bytes left, copy bytes from the dictionary
                while (data.nCopyLen > 0) {
                    data.nCopyLen--;

                    // If output buffer has become full, stop immediately!
                    if (data.pOutPos >= data.pOutBuffer.length) {
                        throw new IllegalArgumentException("PK_ERR_BUFFER_TOO_SMALL: Output buffer is full: " + data.pOutPos + " / " + data.pOutBuffer.length);
                    }


                    // Check whether the offset is a valid one into the dictionary
                    while (pCopyOffs < 0) {
                        pCopyOffs += data.nCurDictSize;
                    }
                    while (pCopyOffs >= data.nCurDictSize) {
                        pCopyOffs -= data.nCurDictSize;
                    }

                    // Copy the byte from the dictionary and add it to the end of the dictionary
                    // *pDictPos++ = *pOutPos++ = *pCopyOffs++;
                    data.Dict[data.pDictPos++] = data.pOutBuffer[data.pOutPos++] = data.Dict[pCopyOffs++];

                    // If the dictionary is not full yet, increment the current dictionary size
                    if (data.nCurDictSize < data.nDictSize) {
                        data.nCurDictSize++;
                    }

                    // If the current end of the dictionary is past the end of the buffer,
                    // wrap around back to the start
                    if (data.pDictPos >= data.nDictSize) {
                        data.pDictPos = 0;
                    }
                }
            } else {
                // First bit is 0; literal byte
                if (data.nLitSize == 0) {
                    // Fixed size literal byte

                    // Copy the byte and add it to the end of the dictionary
                    // *pDictPos++ = *pOutPos++ = (byte)(nBitBuffer >> 1);
                    data.Dict[data.pDictPos++] = data.pOutBuffer[data.pOutPos++] = (byte) (data.nBitBuffer >> 1);


                    // Remove the byte from the bit buffer
                    data.nBitBuffer >>= 9;
                    data.nBits -= 9;
                } else {
                    // Variable size literal byte

                    // Remove the first bit from the bit buffer
                    data.nBitBuffer >>= 1;
                    data.nBits--;

                    // Find the actual byte from the bit sequence
                    int i;
                    for (i = 0; i <= 0xFF; i++) {
                        if (TRUNCATE_VALUE(data.nBitBuffer, ChBits[i] & 0xFF) == (ChCode[i] & 0xFFFF)) {
                            break;
                        }
                    }

                    // Copy the byte and add it to the end of the dictionary
                    // *pDictPos++ = *pOutPos++ = (byte)i;
                    data.Dict[data.pDictPos] = data.pOutBuffer[data.pOutPos] = (byte) i;
                    data.pOutPos++;
                    data.pDictPos++;

                    // Remove the byte from the bit buffer
                    data.nBitBuffer >>= ChBits[i] & 0xFF;
                    data.nBits -= (byte) (ChBits[i] & 0xFF);
                }

                // If the dictionary is not full yet, increment the current dictionary size
                if (data.nCurDictSize < data.nDictSize) {
                    data.nCurDictSize++;
                }

                // If the current end of the dictionary is past the end of the buffer,
                // wrap around back to the start
                if (data.pDictPos >= data.nDictSize) {
                    data.pDictPos = 0;
                }
            }
        }

    }

}
