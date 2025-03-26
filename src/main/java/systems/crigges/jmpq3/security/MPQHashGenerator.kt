package systems.crigges.jmpq3.security

import java.lang.Byte
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.Int
import kotlin.String

/**
 * MPQ cryptographic hashing function. Generates a 32 bit hash from the supplied
 * data using the specified cryptographic lookup table.
 *
 *
 * New generators are created using the static constructor methods. There are 4
 * different types of hash generator available for use with different parts of
 * MPQ.
 */
class MPQHashGenerator private constructor(lut: CryptographicLUT) {
    /**
     * Get the resulting hash for the processed input.
     *
     * @return 32 bit hash.
     */
    /**
     * Seed 1 used as hash result.
     */
    var hash: Int = 0
        private set

    /**
     * Seed 2 used internally.
     */
    private var seed2 = 0

    /**
     * The cryptographic lookup table used to generate the hash.
     */
    private val lut: CryptographicLUT

    /**
     * Constructs a hash generator using the given cryptographic LUT.
     *
     *
     * The hash generator can be used immediately.
     *
     * @param lut
     * cryptographic LUT used to generate hashes.
     */
    init {
        reset()
        this.lut = lut
    }

    /**
     * Reset the hash generator state. After a call to this method the hash
     * generator will behave as if it was freshly created.
     */
    fun reset() {
        this.hash = 0x7FED7FED
        seed2 = -0x11111112
    }

    /**
     * Convenience method to process data from the given string, assuming UTF_8
     * encoding.
     *
     * @param src
     * string to be hashed.
     */
    fun process(src: String) {
        process(ByteBuffer.wrap(src.uppercase(Locale.getDefault()).toByteArray(StandardCharsets.UTF_8)))
    }

    /**
     * Processes data from the given buffer.
     *
     *
     * Calling this method multiple times on different data sets produces the
     * same resulting hash as calling it once with a data set produced by
     * concatenating the separate data sets together in call order.
     *
     * @param src
     * data to be hashed.
     */
    fun process(src: ByteBuffer) {
        while (src.hasRemaining()) {
            val value = src.get()
            this.hash = lut.lookup(value) xor (this.hash + seed2)
            seed2 = Byte.toUnsignedInt(value) + this.hash + seed2 + (seed2 shl 5) + 3
        }
    }

    companion object {

        /**
         * Create a new hash generator for hashtable bucket array index hashes.
         */
        fun getTableOffsetGenerator(): MPQHashGenerator = MPQHashGenerator(CryptographicLUT.HASH_TABLE_OFFSET)


        /**
         * Create a new hash generator for part 1 of hashtable keys.
         */
        fun getTableKey1Generator(): MPQHashGenerator = MPQHashGenerator(CryptographicLUT.HASH_TABLE_KEY1)


        /**
         * Create a new hash generator for part 2 of hashtable keys.
         */
        fun getTableKey2Generator(): MPQHashGenerator = MPQHashGenerator(CryptographicLUT.HASH_TABLE_KEY2)


        /**
         * Create a new hash generator for MPQ encryption keys.
         */
        fun getFileKeyGenerator(): MPQHashGenerator = MPQHashGenerator(CryptographicLUT.HASH_ENCRYPTION_KEY)
    }
}
