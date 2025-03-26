package io.github.warraft.mpq4j.security

import java.lang.Short

/**
 * Cryptographic lookup tables used by MPQ for cryptographic operations such as
 * hashing and encryption. The tables translate byte values into seeded int
 * values.
 *
 *
 * MPQ uses 5 tables, each having a specific purpose.
 */
internal class CryptographicLUT private constructor(table: Int) {
    /**
     * The cryptographic LUT to use for lookup operators.
     */
    private val cryptographicLUT: IntArray = CRYPTOGRAPHIC_TABLES[table]

    /**
     * Lookup value using this cryptographic LUT.
     *
     * @param value
     * value being looked up.
     * @return cryptographic int from the LUT.
     */
    fun lookup(value: Byte): Int {
        return cryptographicLUT[value.toUByte().toInt()]
    }

    companion object {
        /**
         * The number of cryptographic LUTs to generate. MPQ uses 5 tables.
         */
        private const val TABLE_NUMBER = 5

        /**
         * The number of values per cryptographic LUT. MPQ uses tables to translate
         * byte values into int values.
         */
        private const val VALUE_NUMBER = 256

        /**
         * Updates cryptographic table seed 1 cycle.
         *
         * @param seed
         * old seed.
         * @return new seed.
         */
        private fun updateSeed(seed: Int): Int {
            return (seed * 125 + 3) % 0x2AAAAB
        }

        /**
         * Master cryptographic translation tables.
         *
         *
         * Sub-tables of this are used by individual cryptographic LUT objects.
         */
        private val CRYPTOGRAPHIC_TABLES: Array<IntArray> = Array<IntArray>(TABLE_NUMBER) { IntArray(VALUE_NUMBER) }

        init {
            // initial seed value
            var seed = 0x00100001

            for (value in 0..<VALUE_NUMBER) {
                for (table in 0..<TABLE_NUMBER) {
                    val seed1 = (updateSeed(seed).also { seed = it }).toShort()
                    val seed2 = (updateSeed(seed).also { seed = it }).toShort()
                    CRYPTOGRAPHIC_TABLES[table][value] = seed1.toInt() shl 16 or Short.toUnsignedInt(seed2)
                }
            }
        }

        /**
         * Table used to generate hashes for hashtable bucket array index.
         */
        val HASH_TABLE_OFFSET: CryptographicLUT = CryptographicLUT(0)

        /**
         * Table used to generate hashes for part 1 of hashtable keys.
         */
        val HASH_TABLE_KEY1: CryptographicLUT = CryptographicLUT(1)

        /**
         * Table used to generate hashes for part 2 of hashtable keys.
         */
        val HASH_TABLE_KEY2: CryptographicLUT = CryptographicLUT(2)

        /**
         * Table used to generate hashes for MPQ encryption keys.
         */
        val HASH_ENCRYPTION_KEY: CryptographicLUT = CryptographicLUT(3)

        /**
         * Table used to encrypt data.
         */
        val ENCRYPTION: CryptographicLUT = CryptographicLUT(4)
    }
}
