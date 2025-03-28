package io.github.warraft.mpq4j

import io.github.warraft.mpq4j.security.MPQHashGenerator
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MPQ hash table. Used to map file paths to block table indices.
 *
 *
 * Supports localised files using Windows Language ID codes. When requesting a
 * localised mapping it will prioritise finding the requested locale, then the
 * default locale and finally the first locale found.
 *
 *
 * File paths are uniquely identified using a combination of a 64 bit key and
 * their bucket position. As such the hash table does not know what file paths
 * it contains. To get around this limitation MPQs often contain a list file
 * which lists all the file paths used by the hash table. The list file can be
 * used to populate a different capacity hash table with the same mappings.
 */
class HashTable(capacity: Int) {
    /**
     * Hash table bucket array.
     */
    private val buckets: Array<Bucket?>

    /**
     * The number of mappings in the hash table.
     */
    private var mappingNumber = 0

    /**
     * Construct an empty hash table with the specified size.
     *
     *
     * The table can hold at most the specified capacity worth of file mappings,
     * which must be a power of 2.
     *
     * @param capacity power of 2 capacity for the underlying bucket array.
     */
    init {
        require(!(capacity <= 0 || (capacity and (capacity - 1)) != 0)) { "Capacity must be power of 2." }

        buckets = arrayOfNulls<Bucket>(capacity)
        for (i in buckets.indices) {
            buckets[i] = Bucket()
        }
    }

    fun readFromBuffer(src: ByteBuffer) {
        for (entry in buckets) {
            if (entry == null) continue

            entry.readFromBuffer(src)

            // count active mappings
            val blockIndex = entry.blockTableIndex
            if (blockIndex != ENTRY_UNUSED && blockIndex != ENTRY_DELETED) mappingNumber++
        }
    }

    fun writeToBuffer(dest: ByteBuffer) {
        for (bucket in buckets) {
            bucket?.writeToBuffer(dest)
        }
    }

    /**
     * Internal method to get a bucket index for the specified file.
     *
     * @param file file identifier.
     * @return the bucket index used, or -1 if the file has no mapping.
     */
    private fun getFileEntryIndex(file: FileIdentifier): Int {
        val mask = buckets.size - 1
        val start = file.offset and mask
        var bestEntryIndex = -1
        for (c in buckets.indices) {
            val index = start + c and mask
            val entry = buckets[index]

            if (entry?.blockTableIndex == ENTRY_UNUSED) {
                break
            } else if (entry?.blockTableIndex == ENTRY_DELETED) {
                continue
            } else if (entry?.key == file.key) {
                if (entry.locale == file.locale) {
                    return index
                } else if (bestEntryIndex == -1 || entry.locale == DEFAULT_LOCALE) {
                    bestEntryIndex = index
                }
            }
        }

        return bestEntryIndex
    }

    /**
     * Internal method to get a bucket for the specified file.
     *
     * @param file file identifier.
     * @return the file bucket, or null if the file has no mapping.
     */
    private fun getFileEntry(file: FileIdentifier): Bucket? {
        val index = getFileEntryIndex(file)
        return if (index != -1) buckets[index] else null
    }

    /**
     * Check if the specified file path has a mapping in this hash table.
     *
     *
     * A file path has a mapping if it has been mapped for at least 1 locale.
     *
     * @param file file path.
     * @return true if the hash table has a mapping for the file, otherwise
     * false.
     */
    fun hasFile(file: String): Boolean {
        return getFileEntryIndex(FileIdentifier(file, DEFAULT_LOCALE)) != -1
    }

    /**
     * Get the block table index for the specified file.
     *
     * @param name file path name.
     * @return block table index.
     */
    fun getBlockIndexOfFile(name: String): Int {
        return getFileBlockIndex(name, DEFAULT_LOCALE)
    }

    /**
     * Get the block table index for the specified file.
     *
     *
     * Locale parameter is only a recommendation and the return result might be
     * for a different locale. When multiple locales are available the order of
     * priority for selection is the specified locale followed by the default
     * locale and lastly the first locale found.
     *
     * @param name   file path name.
     * @param locale file locale.
     * @return block table index.
     */
    fun getFileBlockIndex(name: String, locale: Short): Int {
        val fid = FileIdentifier(name, locale)
        val entry = getFileEntry(fid)

        if (entry == null) {
            //println("File Not Found <$name>.")
            return -100500
        } else if (entry.blockTableIndex < 0) {
            //println("File has invalid block table index <" + entry.blockTableIndex + ">.")
            return -100500
        }

        return entry.blockTableIndex
    }

    /**
     * Set a block table index for the specified file. Existing mappings are
     * updated.
     *
     * @param name       file path name.
     * @param locale     file locale.
     * @param blockIndex block table index.
     */
    @Throws(Exception::class)
    fun setFileBlockIndex(name: String, locale: Short, blockIndex: Int) {
        require(blockIndex >= 0) { "Block index numbers cannot be negative." }

        val fid = FileIdentifier(name, locale)

        // check if file entry already exists
        val exist = getFileEntry(fid)
        if (exist != null && exist.locale == locale) {
            exist.blockTableIndex = blockIndex
            return
        }

        // check if space for new entry
        if (mappingNumber == buckets.size) throw Exception("Hash table cannot fit another mapping.")

        // locate suitable entry
        val mask = buckets.size - 1
        val start = fid.offset and mask
        var newEntry: Bucket? = null
        for (c in buckets.indices) {
            val entry = buckets[start + c and mask]

            if (entry?.blockTableIndex == ENTRY_UNUSED || entry?.blockTableIndex == ENTRY_DELETED) {
                newEntry = entry
                break
            }
        }

        // setup entry
        if (newEntry != null) {
            newEntry.key = fid.key
            newEntry.locale = fid.locale
            newEntry.blockTableIndex = blockIndex
            mappingNumber++
        }
    }

    /**
     * Internal method to remove a file entry at the specified bucket index.
     *
     * @param index bucket to clear.
     */
    private fun removeFileEntry(index: Int) {
        val bi = buckets[index]?.blockTableIndex
        require(!(bi == ENTRY_UNUSED || bi == ENTRY_DELETED)) { "Bucket already clear." }

        // delete file
        val newEntry = Bucket()
        newEntry.blockTableIndex = ENTRY_DELETED
        buckets[index] = newEntry
        mappingNumber--

        // cleanup to empty if possible
        val mask = buckets.size - 1
        if (buckets[index + 1 and mask]?.blockTableIndex == ENTRY_UNUSED) {
            var entry: Bucket?
            var i = index
            while ((buckets[i].also { entry = it })?.blockTableIndex == ENTRY_DELETED) {
                entry?.blockTableIndex = ENTRY_UNUSED
                i = i - 1 and mask
            }
        }
    }

    /**
     * Remove the specified file from the hash table.
     *
     * @param name   file path name.
     * @param locale file locale.
     */
    @Throws(Exception::class)
    fun removeFile(name: String, locale: Short) {
        val fid = FileIdentifier(name, locale)

        // check if file exists
        val index = getFileEntryIndex(fid)
        if (index == -1 || buckets[index]?.locale != locale) throw Exception("File Not Found <$name>")

        // delete file
        removeFileEntry(index)
    }

    /**
     * Remove the specified file from the hash table for all locales.
     *
     * @param name file path name.
     * @return number of file entries that were removed.
     */
    @Throws(Exception::class)
    fun removeFileAll(name: String): Int {
        val fid = FileIdentifier(name, DEFAULT_LOCALE)
        var count = 0
        var index: Int
        while ((getFileEntryIndex(fid).also { index = it }) != -1) {
            removeFileEntry(index)
            count++
        }

        // check if file was removed
        if (count == 0) throw Exception("File Not Found <$name>")

        return count
    }

    /**
     * Plain old data class to internally represent a uniquely identifiable
     * file.
     *
     *
     * Used to cache file name hash results.
     */
    private class FileIdentifier(name: String, locale: Short) {
        /**
         * 64 bit file key.
         */
        val key: Long

        /**
         * Offset into hash table bucket array to start search.
         */
        val offset: Int

        /**
         * File locale in the form of a Windows Language ID.
         */
        val locale: Short

        init {
            // generate file offset
            val offsetGen = MPQHashGenerator.Companion.getTableOffsetGenerator()
            offsetGen.process(name)
            offset = offsetGen.hash
            key = calculateFileKey(name)

            this.locale = locale
        }
    }

    /**
     * Plain old data class for hash table buckets.
     */
    private class Bucket {
        /**
         * 64 bit file key.
         */
        var key: Long = 0

        /**
         * File locale in the form of a Windows Language ID.
         */
        var locale: Short = 0

        /**
         * Block table index for file data.
         *
         *
         * Some negative magic numbers are used to represent the bucket state.
         */
        var blockTableIndex: Int = ENTRY_UNUSED

        fun readFromBuffer(src: ByteBuffer) {
            src.order(ByteOrder.LITTLE_ENDIAN)
            key = src.getLong()
            locale = src.getShort()
            src.getShort() // platform not used
            blockTableIndex = src.getInt()
        }

        fun writeToBuffer(dest: ByteBuffer) {
            dest.order(ByteOrder.LITTLE_ENDIAN)
            dest.putLong(key)
            dest.putShort(locale)
            dest.putShort(0.toShort()) // platform not used
            dest.putInt(blockTableIndex)
        }

        override fun toString(): String {
            return "Entry [key=" + key + ",\tlcLocale=" + this.locale + ",\tdwBlockIndex=" + this.blockTableIndex + "]"
        }
    }

    companion object {
        /**
         * Magic block number representing a hash table entry which is not in use.
         */
        private const val ENTRY_UNUSED = -1

        /**
         * Magic block number representing a hash table entry which was deleted.
         */
        private const val ENTRY_DELETED = -2

        /**
         * The default file locale, US English.
         */
        const val DEFAULT_LOCALE: Short = 0

        fun calculateFileKey(name: String): Long {
            // generate file key
            val key1Gen = MPQHashGenerator.Companion.getTableKey1Generator()
            key1Gen.process(name)
            val key1 = key1Gen.hash
            val key2Gen = MPQHashGenerator.Companion.getTableKey2Generator()
            key2Gen.process(name)
            val key2 = key2Gen.hash
            return (key2.toLong() shl 32) or Integer.toUnsignedLong(key1)
        }
    }
}
