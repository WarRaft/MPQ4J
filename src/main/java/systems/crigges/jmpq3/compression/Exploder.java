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
    public static void pkexplode(byte[] pInBuffer, byte[] pOutBuffer, int inPos) {
        // Compressed data cannot be less than 4 bytes;
        // this is not possible in any case whatsoever
        if (pInBuffer.length < 4) {
            throw new IllegalArgumentException("PK_ERR_INCOMPLETE_INPUT: Incomplete input");
        }

        int pOutPos = 0;
        // This is 1 because in an mpq-sector, the first byte is the compression type flag
        int pInPos = inPos;

        // Get header from compressed data
        byte nLitSize = pInBuffer[pInPos];
        pInPos++;
        byte nDictSizeByte = pInBuffer[pInPos];
        pInPos++;

        // Check for a valid compression type
        if (nLitSize != 0 && nLitSize != 1)
            throw new IllegalArgumentException("PK_ERR_BAD_DATA: Invalid LitSize: " + nLitSize);

        // Only dictionary sizes of 1024, 2048, and 4096 are allowed.
        // The values 4, 5, and 6 correspond with those sizes
        if (4 > nDictSizeByte || nDictSizeByte > 6)
            throw new IllegalArgumentException("PK_ERR_BAD_DATA: Invalid DictSizeByte: " + nDictSizeByte);

        int nDictSize = 64 << nDictSizeByte;

        byte[] Dict = new byte[0x1000];
        int pDictPos = 0;
        int nCurDictSize = 0;

        // Get first 16 bits

        // Stores bits until there are enough to output a byte of data

        long nBitBuffer = (pInBuffer[pInPos] & 0xFFL);
        pInPos++;
        nBitBuffer += ((pInBuffer[pInPos] & 0xFFL) << 0x8);
        pInPos++;

        // Number of bits in bit buffer
        byte nBits = 16;

        // Decompress until output buffer is full
        int i; // Index into tables
        int nCopyLen;
        while (pOutPos < pOutBuffer.length) {

            // Fill bit buffer with at least 16 bits
            while (nBits < 16) {
                // If input buffer is empty before end of stream, buffer is incomplete
                if (pInPos >= pInBuffer.length) {
                    // Store the current size of output
                    // nOutSize = pOutPos - pOutBuffer;
                    throw new IllegalArgumentException("PK_ERR_INCOMPLETE_INPUT: Incomplete input");
                }

                nBitBuffer += ((long) (pInBuffer[pInPos] & 0xFF)) << nBits;
                pInPos++;
                nBits += 8;
            }

            // First bit is 1; copy from dictionary
            if ((nBitBuffer & 1) != 0) {

                // Remove first bit from bit buffer
                nBitBuffer >>= 1;
                nBits--;

                // Find the base value for the copy length
                for (i = 0; i <= 0x0F; i++) {
                    if (TRUNCATE_VALUE(nBitBuffer, LenBits[i] & 0xFF) == (LenCode[i] & 0xFF))
                        break;
                }

                // Remove value from bit buffer
                nBitBuffer >>= LenBits[i] & 0xFF;
                nBits -= (byte) (LenBits[i] & 0xFF);

                // Store the copy length
                nCopyLen = (int) ((LenBase[i] & 0xFFFF) + TRUNCATE_VALUE(nBitBuffer, ExLenBits[i] & 0xFF)); // Length of data to copy from the dictionary

                // Remove the extra bits from the bit buffer
                nBitBuffer >>= ExLenBits[i] & 0xFF;
                nBits -= (byte) (ExLenBits[i] & 0xFF);

                // If copy length is 519, the end of the stream has been reached
                if (nCopyLen == 519)
                    break;

                // Fill bit buffer with at least 14 bits
                while (nBits < 14) {
                    // If input buffer is empty before end of stream, buffer is incomplete
                    if (pInPos >= pInBuffer.length) {
                        // Store the current size of output
                        // nOutSize = pOutPos - pOutBuffer;
                        throw new IllegalArgumentException("PK_ERR_INCOMPLETE_INPUT: Incomplete input");
                    }

                    nBitBuffer += (long) (pInBuffer[pInPos] & 0xFF) << nBits;
                    pInPos++;
                    nBits += 8;
                }

                // Find most significant 6 bits of offset into the dictionary
                for (i = 0; i <= 0x3F; i++) {
                    if (TRUNCATE_VALUE(nBitBuffer, OffsBits[i] & 0xFF) == (OffsCode[i] & 0xFF))
                        break;
                }

                // Remove value from bit buffer
                nBitBuffer >>= OffsBits[i] & 0xFF;
                nBits -= (byte) (OffsBits[i] & 0xFF);

                // If the copy length is 2, there are only two more bits in the dictionary
                // offset; otherwise, there are 4, 5, or 6 bits left, depending on what
                // the dictionary size is
                int pCopyOffs; // Offset to data to copy from the dictionary
                if (nCopyLen == 2) {

                    // Store the exact offset to a byte in the dictionary
                    pCopyOffs = (int) (pDictPos - 1 - ((i << 2) + (nBitBuffer & 0b11)));

                    // Remove the rest of the dictionary offset from the bit buffer
                    nBitBuffer >>= 2;
                    nBits -= 2;
                } else {

                    // Store the exact offset to a byte in the dictionary
                    pCopyOffs = (int) (pDictPos - 1 - ((i << nDictSizeByte) + TRUNCATE_VALUE(nBitBuffer, nDictSizeByte)));

                    // Remove the rest of the dictionary offset from the bit buffer
                    nBitBuffer >>= nDictSizeByte;
                    nBits -= nDictSizeByte;
                }

                // While there are still bytes left, copy bytes from the dictionary
                while (nCopyLen > 0) {
                    nCopyLen--;

                    // If output buffer has become full, stop immediately!
                    if (pOutPos >= pOutBuffer.length)
                        throw new IllegalArgumentException("PK_ERR_BUFFER_TOO_SMALL: Output buffer is full: " + pOutPos + " / " + pOutBuffer.length);


                    // Check whether the offset is a valid one into the dictionary
                    while (pCopyOffs < 0)
                        pCopyOffs += nCurDictSize;
                    while (pCopyOffs >= nCurDictSize)
                        pCopyOffs -= nCurDictSize;

                    // Copy the byte from the dictionary and add it to the end of the dictionary
                    // *pDictPos++ = *pOutPos++ = *pCopyOffs++;
                    Dict[pDictPos] = pOutBuffer[pOutPos] = Dict[pCopyOffs];
                    pCopyOffs++;
                    pOutPos++;
                    pDictPos++;

                    // If the dictionary is not full yet, increment the current dictionary size
                    if (nCurDictSize < nDictSize)
                        nCurDictSize++;

                    // If the current end of the dictionary is past the end of the buffer,
                    // wrap around back to the start
                    if (pDictPos >= nDictSize)
                        pDictPos = 0;
                }
            }

            // First bit is 0; literal byte
            else {

                // Fixed size literal byte
                if (nLitSize == 0) {

                    // Copy the byte and add it to the end of the dictionary
                    // *pDictPos++ = *pOutPos++ = (byte)(nBitBuffer >> 1);
                    Dict[pDictPos] = pOutBuffer[pOutPos++] = (byte) (nBitBuffer >> 1);
                    pDictPos++;

                    // Remove the byte from the bit buffer
                    nBitBuffer >>= 9;
                    nBits -= 9;
                }

                // Variable size literal byte
                else {

                    // Remove the first bit from the bit buffer
                    nBitBuffer >>= 1;
                    nBits--;

                    // Find the actual byte from the bit sequence
                    for (i = 0; i <= 0xFF; i++) {
                        if (TRUNCATE_VALUE(nBitBuffer, ChBits[i] & 0xFF) == (ChCode[i] & 0xFFFF))
                            break;
                    }

                    // Copy the byte and add it to the end of the dictionary
                    // *pDictPos++ = *pOutPos++ = (byte)i;
                    Dict[pDictPos] = pOutBuffer[pOutPos] = (byte) i;
                    pOutPos++;
                    pDictPos++;

                    // Remove the byte from the bit buffer
                    nBitBuffer >>= ChBits[i] & 0xFF;
                    nBits -= (byte) (ChBits[i] & 0xFF);
                }

                // If the dictionary is not full yet, increment the current dictionary size
                if (nCurDictSize < nDictSize)
                    nCurDictSize++;

                // If the current end of the dictionary is past the end of the buffer,
                // wrap around back to the start
                if (pDictPos >= nDictSize)
                    pDictPos = 0;
            }
        }

    }

}
