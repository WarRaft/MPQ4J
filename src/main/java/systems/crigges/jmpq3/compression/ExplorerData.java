package systems.crigges.jmpq3.compression;

public class ExplorerData {
    public ExplorerData(byte[] pInBuffer, byte[] pOutBuffer, int inPos) {
        this.pInBuffer = pInBuffer;
        this.pOutBuffer = pOutBuffer;
        this.inPos = inPos;

        if (pInBuffer.length < 4) {
            throw new IllegalArgumentException("PK_ERR_INCOMPLETE_INPUT: Incomplete input");
        }

        this.pInPos = inPos;

        nLitSize = pInBuffer[pInPos++];
        nDictSizeByte = pInBuffer[pInPos++];

        if (nLitSize != 0 && nLitSize != 1) {
            throw new IllegalArgumentException("PK_ERR_BAD_DATA: Invalid LitSize: " + nLitSize);
        }

        if (4 > nDictSizeByte || nDictSizeByte > 6) {
            throw new IllegalArgumentException("PK_ERR_BAD_DATA: Invalid DictSizeByte: " + nDictSizeByte);
        }

        nDictSize = 64 << nDictSizeByte;

        Dict = new byte[0x1000];

        nBitBuffer = (pInBuffer[pInPos++] & 0xFFL);
        nBitBuffer += ((pInBuffer[pInPos++] & 0xFFL) << 0x8);
    }

    long nBitBuffer;

    int pDictPos = 0;
    int nCurDictSize = 0;

    byte[] Dict;

    int nDictSize;
    byte nLitSize;
    byte nDictSizeByte;

    byte nBits = 16;
    int nCopyLen;
    int pOutPos = 0;

    int pInPos;
    byte[] pInBuffer;
    byte[] pOutBuffer;
    int inPos;
}
