package io.github.warraft.mpq4j

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import io.github.warraft.mpq4j.compression.RecompressOptions
import io.github.warraft.mpq4j.security.MPQEncryption
import io.github.warraft.mpq4j.security.MPQHashGenerator
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.math.min

class MPQ4J {
    private var attributes: AttributesFile? = null

    /**
     * MPQ format version 0 forced compatibility is being used.
     */
    private val legacyCompatibility: Boolean

    /**
     * The fc.
     */
    private var fc: SeekableByteChannel

    /**
     * The header offset.
     */
    private var headerOffset: Long = 0

    /**
     * The header size.
     */
    private var headerSize = 0

    /**
     * The archive size.
     */
    private var archiveSize: Long = 0

    /**
     * The format version.
     */
    private var formatVersion = 0

    /**
     * The sector size shift
     */
    private var sectorSizeShift = 0

    /**
     * The disc block size.
     */
    private var discBlockSize = 0

    /**
     * The hash table file position.
     */
    private var hashPos: Long = 0

    /**
     * The block table file position.
     */
    private var blockPos: Long = 0

    /**
     * The hash size.
     */
    private var hashSize = 0

    /**
     * The block size.
     */
    private var blockSize = 0

    /**
     * The hash table.
     */
    private var hashTable: HashTable? = null

    /**
     * The block table.
     */
    private var blockTable: BlockTable? = null

    /**
     * The list file.
     */
    private var listFile: Listfile? = Listfile()

    /**
     * The internal filename.
     */
    private val filenameToData = LinkedIdentityHashMap<String, ByteBuffer>()
    /** The files to add.  */
    /**
     * The keep header offset.
     */
    private var keepHeaderOffset = true

    /**
     * The new header size.
     */
    private var newHeaderSize = 0

    /**
     * The new archive size.
     */
    private var newArchiveSize: Long = 0

    /**
     * The new format version.
     */
    private var newFormatVersion = 0

    /**
     * The new disc block size.
     */
    private var newSectorSizeShift = 0

    /**
     * The new disc block size.
     */
    private var newDiscBlockSize = 0

    /**
     * The new hash pos.
     */
    private var newHashPos: Long = 0

    /**
     * The new block pos.
     */
    private var newBlockPos: Long = 0

    /**
     * The new hash size.
     */
    private var newHashSize = 0

    /**
     * The new block size.
     */
    private var newBlockSize = 0

    /**
     * If write operations are supported on the archive.
     */
    var isCanWrite: Boolean
        private set

    /**
     * Creates a new MPQ editor for the MPQ file at the specified path.
     *
     *
     * If the archive file does not exist a new archive file will be created
     * automatically. Any changes made to the archive might only propagate to
     * the file system once this's close method is called.
     *
     *
     * When READ_ONLY option is specified then the archive file will never be
     * modified by this editor.
     *
     * @param mpqArchive  path to a MPQ archive file.
     * @param openOptions options to use when opening the archive.
     */
    constructor(mpqArchive: Path, vararg openOptions: MPQOpenOption?) {
        // process open options
        this.isCanWrite = !listOf<MPQOpenOption?>(*openOptions).contains(MPQOpenOption.READ_ONLY)
        legacyCompatibility = listOf<MPQOpenOption?>(*openOptions).contains(MPQOpenOption.FORCE_V0)
        try {
            setupTempDir()

            fc = if (this.isCanWrite)
                FileChannel.open(
                    mpqArchive,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
                )
            else
                FileChannel.open(mpqArchive, StandardOpenOption.READ)

            readMpq()
        } catch (e: IOException) {
            throw Exception(mpqArchive.toAbsolutePath().toString() + ": " + e.message)
        }
    }

    constructor(mpqArchive: ByteArray, vararg openOptions: MPQOpenOption?) {
        // process open options
        this.isCanWrite = !listOf<MPQOpenOption?>(*openOptions).contains(MPQOpenOption.READ_ONLY)
        legacyCompatibility = listOf<MPQOpenOption?>(*openOptions).contains(MPQOpenOption.FORCE_V0)
        try {
            setupTempDir()
            fc = SeekableInMemoryByteChannel(mpqArchive)
            readMpq()
        } catch (e: IOException) {
            throw Exception("Byte array mpq: " + e.message)
        }
    }


    private fun readMpq() {
        headerOffset = searchHeader()
        readHeaderSize()
        readHeader()
        checkLegacyCompat()
        readHashTable()
        readBlockTable()
        readListFile()
        readAttributesFile()
    }

    /**
     * See [.JMpqEditor] }
     *
     * @param mpqArchive  a MPQ archive file.
     * @param openOptions options to use when opening the archive.
     */
    constructor(mpqArchive: File, vararg openOptions: MPQOpenOption?) : this(mpqArchive.toPath(), *openOptions)


    private fun checkLegacyCompat() {
        if (legacyCompatibility) {
            // limit end of archive by end of file
            archiveSize = min(archiveSize.toDouble(), (fc.size() - headerOffset).toDouble()).toLong()

            // limit block table size by end of archive
            blockSize = (min(blockSize.toDouble(), ((archiveSize - blockPos) / 16).toDouble())).toInt()
        }
    }

    private fun readAttributesFile() {
        if (hasFile("(attributes)")) {
            attributes = AttributesFile(extractFileAsBytes("(attributes)"))
        }
    }

    /**
     * For use when the MPQ is missing a (listfile)
     * Adds this custom listfile into the MPQ and uses it
     * for rebuilding purposes.
     * If this is not a full listfile, the end result will be missing files.
     *
     * @param externalListfilePath Path to a file containing listfile entries
     */
    fun setExternalListfile(externalListfilePath: File) {
        if (!this.isCanWrite) {
            println("⚠️The mpq was opened as readonly, setting an external listfile will have no effect.")
            return
        }
        if (!externalListfilePath.exists()) {
            println(
                "⚠️External MPQ File: " + externalListfilePath.absolutePath +
                        " does not exist and will not be used"
            )
            return
        }
        try {
            // Read and apply listfile
            listFile = Listfile(Files.readAllBytes(externalListfilePath.toPath()))
            checkListfileEntries()
            // Operation succeeded and added a listfile so we can now write properly.
            // (as long as it wasn't read-only to begin with)
        } catch (_: Exception) {
            println("⚠️Could not apply external listfile: " + externalListfilePath.absolutePath)
            // The value of canWrite is not changed intentionally
        }
    }

    /**
     * Reads an internal Listfile name called (listfile)
     * and applies that as the archive's listfile.
     */
    private fun readListFile() {
        if (hasFile("(listfile)")) {
            try {
                listFile = Listfile(extractFileAsBytes("(listfile)"))
                checkListfileEntries()
            } catch (e: Exception) {
                println("⚠️Extracting the mpq's listfile failed. It cannot be rebuild. | $e")
            }
        } else {
            println("⚠️The mpq doesn't contain a listfile. It cannot be rebuild.")
            this.isCanWrite = false
        }
    }

    /**
     * Performs verification to see if we know all the blocks of this file.
     * Prints warnings if we don't know all blocks.
     *
     */
    private fun checkListfileEntries() {
        val hiddenFiles = (if (hasFile("(attributes)")) 2 else 1) + (if (hasFile("(signature)")) 1 else 0)
        if (this.isCanWrite) {
            checkListfileCompleteness(hiddenFiles)
        }
    }

    /**
     * Checks listfile for completeness against block table
     *
     * @param hiddenFiles Num. hidden files
     */
    private fun checkListfileCompleteness(hiddenFiles: Int) {
        if (listFile!!.files.size <= blockTable!!.allVaildBlocks.size - hiddenFiles) {
            println("🔥 mpq's listfile is incomplete. Blocks without listfile entry will be discarded")
        }
        for (fileName in listFile!!.files) {
            if (!hasFile(fileName)) {
                println("🔥 listfile entry does not exist in archive and will be discarded: $fileName")
            }
        }
        listFile!!.fileMap.entries.removeIf { file: MutableMap.MutableEntry<Long, String>? -> !hasFile(file!!.value) }
    }


    private fun readBlockTable() {
        val blockBuffer = ByteBuffer.allocate(blockSize * 16).order(ByteOrder.LITTLE_ENDIAN)
        fc.position(headerOffset + blockPos)
        readFully(blockBuffer, fc)
        blockBuffer.rewind()
        blockTable = BlockTable(blockBuffer)
    }


    private fun readHashTable() {
        // read hash table
        val hashBuffer = ByteBuffer.allocate(hashSize * 16)
        fc.position(headerOffset + hashPos)
        readFully(hashBuffer, fc)
        hashBuffer.rewind()

        // decrypt hash table
        val decrypt = MPQEncryption(KEY_HASH_TABLE, true)
        decrypt.processSingle(hashBuffer)
        hashBuffer.rewind()

        // create hash table
        hashTable = HashTable(hashSize)
        hashTable!!.readFromBuffer(hashBuffer)
    }


    private fun readHeaderSize() {
        // probe to sample file with
        val probe = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        // read header size
        fc.position(headerOffset + 4)
        readFully(probe, fc)
        headerSize = probe.getInt(0)
        if (legacyCompatibility) {
            // force version 0 header size
            headerSize = 32
        } else if (headerSize < 32 || 208 < headerSize) {
            // header too small or too big
            throw Exception("Bad header size.")
        }
    }

    private fun setupTempDir() {
        try {
            val path = Paths.get(System.getProperty("java.io.tmpdir") + "jmpq")
            tempDir = path.toFile()
            if (!tempDir!!.exists()) Files.createDirectory(path)

            val files: Array<File>? = tempDir!!.listFiles()
            if (files != null) {
                for (f in files) {
                    f.delete()
                }
            }
        } catch (_: IOException) {
            try {
                tempDir = Files.createTempDirectory("jmpq").toFile()
            } catch (e1: IOException) {
                throw Exception(e1)
            }
        }
    }


    //    /**
    //     * Loads a default listfile for mpqs that have none
    //     * Makes the archive readonly.
    //     */
    //    private void loadDefaultListFile() throws IOException {
    //        println("⚠️The mpq doesn't come with a listfile so it cannot be rebuild");
    //        InputStream resource = getClass().getClassLoader().getResourceAsStream("DefaultListfile.txt");
    //        if (resource != null) {
    //            File tempFile = File.createTempFile("jmpq", "lf", tempDir);
    //            tempFile.deleteOnExit();
    //            try (FileOutputStream out = new FileOutputStream(tempFile)) {
    //                //copy stream
    //                byte[] buffer = new byte[1024];
    //                int bytesRead;
    //                while ((bytesRead = resource.read(buffer)) != -1) {
    //                    out.write(buffer, 0, bytesRead);
    //                }
    //            }
    //            listFile = new Listfile(Files.readAllBytes(tempFile.toPath()));
    //            canWrite = false;
    //        }
    //    }
    /**
     * Searches the file for the MPQ archive header.
     *
     * @return the file position at which the MPQ archive starts.
     * @throws IOException   if an error occurs while searching.
     */

    private fun searchHeader(): Long {
        // probe to sample file with
        val probe = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

        val fileSize = fc.size()
        var filePos: Long = 0
        while (filePos + probe.capacity() < fileSize) {
            probe.rewind()
            fc.position(filePos)
            readFully(probe, fc)

            val sample = probe.getInt(0)
            if (sample == ARCHIVE_HEADER_MAGIC) {
                // found archive header
                return filePos
            } else if (sample == USER_DATA_HEADER_MAGIC && !legacyCompatibility) {
                // MPQ user data header with redirect to MPQ header
                // ignore in legacy compatibility mode

                probe.rewind()
                fc.position(filePos + 8)
                readFully(probe, fc)

                // add header offset and align
                filePos += (probe.getInt(0).toLong() and 0xFFFFFFFFL)
                filePos = filePos and (-0x200).toLong()
            }
            filePos += 0x200
        }

        throw Exception("No MPQ archive in file.")
    }

    /**
     * Read the MPQ archive header from the header chunk.
     */

    private fun readHeader() {
        val buffer = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)
        readFully(buffer, fc)
        buffer.rewind()

        archiveSize = buffer.getInt().toLong() and 0xFFFFFFFFL
        formatVersion = buffer.getShort().toInt()
        if (legacyCompatibility) {
            // force version 0 interpretation
            formatVersion = 0
        }

        sectorSizeShift = buffer.getShort().toInt()
        discBlockSize = 512 * (1 shl sectorSizeShift)
        hashPos = buffer.getInt().toLong() and 0xFFFFFFFFL
        blockPos = buffer.getInt().toLong() and 0xFFFFFFFFL
        hashSize = buffer.getInt() and 0x0FFFFFFF
        blockSize = buffer.getInt()

        // version 1 extension
        if (formatVersion >= 1) {
            buffer.getLong()

            // high 16 bits of file pos
            hashPos = hashPos or ((buffer.getShort().toLong() and 0xFFFFL) shl 32)
            blockPos = blockPos or ((buffer.getShort().toLong() and 0xFFFFL) shl 32)
        }

        // version 2 extension
        if (formatVersion >= 2) {
            // 64 bit archive size
            archiveSize = buffer.getLong()
            buffer.getLong()
            buffer.getLong()
        }

        // version 3 extension
        if (formatVersion >= 3) {
            buffer.getLong()
            buffer.getLong()
            buffer.getLong()
            buffer.getLong()
            buffer.getLong()

            buffer.getInt()
            val md5 = ByteArray(16)
            buffer.get(md5)
            buffer.get(md5)
            buffer.get(md5)
            buffer.get(md5)
            buffer.get(md5)
            buffer.get(md5)
        }
    }

    /**
     * Write header.
     *
     * @param buffer the buffer
     */
    private fun writeHeader(buffer: MappedByteBuffer) {
        buffer.putInt(newHeaderSize)
        buffer.putInt(newArchiveSize.toInt())
        buffer.putShort(newFormatVersion.toShort())
        buffer.putShort(newSectorSizeShift.toShort())
        buffer.putInt(newHashPos.toInt())
        buffer.putInt(newBlockPos.toInt())
        buffer.putInt(newHashSize)
        buffer.putInt(newBlockSize)
    }

    private fun calcNewTableSize() {
        val target = listFile!!.files.size + 2
        var current = 2
        while (current < target) {
            current *= 2
        }
        newHashSize = current * 2
        newBlockSize = listFile!!.files.size + 2
    }

    fun extractAllFiles(dest: File) {
        if (!dest.isDirectory()) {
            throw Exception("Destination location isn't a directory")
        }
        if (hasFile("(listfile)") && listFile != null) {
            for (s in listFile!!.files) {
                val normalized = if (File.separatorChar == '\\') s else s.replace("\\", File.separator)
                val temp = File(dest.absolutePath + File.separator + normalized)
                temp.getParentFile().mkdirs()
                if (hasFile(s)) {
                    // Prevent exception due to nonexistent listfile entries
                    try {
                        extractFile(s, temp)
                    } catch (_: Exception) {
                        println("File possibly corrupted and could not be extracted: $s")
                    }
                }
            }
            if (hasFile("(attributes)")) {
                val temp = File(dest.absolutePath + File.separator + "(attributes)")
                extractFile("(attributes)", temp)
            }
            val temp = File(dest.absolutePath + File.separator + "(listfile)")
            extractFile("(listfile)", temp)
        } else {
            val blocks = blockTable!!.allVaildBlocks
            try {
                var i = 0
                for (b in blocks) {
                    if (b!!.hasFlag(MpqFile.ENCRYPTED)) {
                        continue
                    }
                    val buf = ByteBuffer.allocate(b.compressedSize).order(ByteOrder.LITTLE_ENDIAN)
                    fc.position(headerOffset + b.filePos)
                    readFully(buf, fc)
                    buf.rewind()
                    val f = MpqFile(buf, b, discBlockSize, "")
                    f.extractToFile(File(dest.absolutePath + File.separator + i))
                    i++
                }
            } catch (e: IOException) {
                throw Exception(e)
            }
        }
    }

    @Suppress("unused")
    val totalFileCount: Int
        /**
         * Gets the total file count.
         *
         * @return the total file count
         */
        get() = blockTable!!.allVaildBlocks.size

    /**
     * Extracts the specified file out of the mpq to the target location.
     *
     * @param name name of the file
     * @param dest destination to that the files content is written
     */
    fun extractFile(name: String, dest: File) {
        try {
            val f = getMpqFile(name)
            f.extractToFile(dest)
        } catch (e: Exception) {
            throw Exception(e)
        }
    }

    fun extractFileAsBytes(name: String): ByteArray {
        try {
            val f = getMpqFile(name)
            return f.extractToBytes()
        } catch (e: IOException) {
            throw Exception(e)
        }
    }

    fun extractFileAsString(name: String): String {
        try {
            val f = extractFileAsBytes(name)
            return String(f)
        } catch (e: IOException) {
            throw Exception(e)
        }
    }

    fun hasFile(name: String): Boolean = hashTable!!.getBlockIndexOfFile(name) >= 0

    @Suppress("unused")
    val fileNames: MutableList<String?>
        /**
         * Gets the file names.
         *
         * @return the file names
         */
        get() = ArrayList<String?>(listFile!!.files)

    /**
     * Extracts the specified file out of the mpq and writes it to the target
     * outputstream.
     *
     */
    @Suppress("unused")
    fun extractFile(name: String, dest: OutputStream) {
        try {
            val f = getMpqFile(name)
            f.extractToOutputStream(dest)
        } catch (e: IOException) {
            throw Exception(e)
        }
    }

    /**
     * Gets the mpq file.
     *
     * @param name the name
     * @return the mpq file
     * @throws IOException Signals that an I/O exception has occurred.
     */

    fun getMpqFile(name: String): MpqFile {
        val pos = hashTable!!.getBlockIndexOfFile(name)
        val b = blockTable!!.getBlockAtPos(pos)

        val buffer = ByteBuffer.allocate(b.compressedSize).order(ByteOrder.LITTLE_ENDIAN)
        fc.position(headerOffset + b.filePos)
        readFully(buffer, fc)
        buffer.rewind()

        return MpqFile(buffer, b, discBlockSize, name)
    }

    /**
     * Gets the mpq file.
     *
     * @param block a block
     * @return the mpq file
     * @throws IOException Signals that an I/O exception has occurred.
     */

    fun getMpqFileByBlock(block: BlockTable.Block): MpqFile {
        if (block.hasFlag(MpqFile.ENCRYPTED)) {
            throw IOException("cant access this block")
        }
        val buffer = ByteBuffer.allocate(block.compressedSize).order(ByteOrder.LITTLE_ENDIAN)
        fc.position(headerOffset + block.filePos)
        readFully(buffer, fc)
        buffer.rewind()

        return MpqFile(buffer, block, discBlockSize, "")
    }

    @get:Throws(IOException::class)
    val mpqFilesByBlockTable: MutableList<MpqFile?>
        /**
         * Gets the mpq files.
         *
         * @return the mpq files
         * @throws IOException Signals that an I/O exception has occurred.
         */
        get() {
            val mpqFiles: MutableList<MpqFile?> = ArrayList<MpqFile?>()
            val list = blockTable!!.allVaildBlocks
            for (block in list) {
                try {
                    val mpqFile = getMpqFileByBlock(block!!)
                    mpqFiles.add(mpqFile)
                } catch (_: IOException) {
                }
            }
            return mpqFiles
        }

    /**
     * Deletes the specified file out of the mpq once you rebuild the mpq.
     *
     * @param name of the file inside the mpq
     */
    fun deleteFile(name: String) {
        if (!this.isCanWrite) {
            throw NonWritableChannelException()
        }

        if (listFile!!.containsFile(name)) {
            listFile!!.removeFile(name)
            filenameToData.remove(name)
        }
    }

    @JvmOverloads
    fun insertByteArray(name: String, input: ByteArray, override: Boolean = false) {
        if (!this.isCanWrite) {
            throw NonWritableChannelException()
        }

        require(!((!override) && listFile!!.containsFile(name))) { "Archive already contains file with name: $name" }

        listFile!!.addFile(name)
        val data = ByteBuffer.wrap(input)
        filenameToData.put(name, data)
    }

    /**
     * Inserts the specified file into the mpq once you close the editor.
     */
    fun insertFile(name: String, file: File, override: Boolean = false) {
        if (!this.isCanWrite) {
            throw NonWritableChannelException()
        }

        require(!((!override) && listFile!!.containsFile(name))) { "Archive already contains file with name: $name" }

        try {
            listFile!!.addFile(name)
            val data = ByteBuffer.wrap(Files.readAllBytes(file.toPath()))
            filenameToData.put(name, data)
        } catch (e: IOException) {
            throw Exception(e)
        }
    }

    fun close() {
        close(true, true, false)
    }

    fun close(buildListfile: Boolean, buildAttributes: Boolean, recompress: Boolean) {
        close(buildListfile, buildAttributes, RecompressOptions(recompress))
    }

    /**
     * @param addListfile   whether or not to add a (listfile) to this mpq
     * @param adddAttributes whether or not to add a (attributes) file to this mpq
     * @throws IOException
     */
    fun close(addListfile: Boolean, adddAttributes: Boolean, options: RecompressOptions) {
        // only rebuild if allowed
        if (!this.isCanWrite || !fc.isOpen) {
            fc.close()
            return
        }

        var t = System.nanoTime()
        if (listFile == null) {
            fc.close()
            return
        }
        val temp = File.createTempFile("jmpq", "temp", tempDir)
        temp.deleteOnExit()
        FileChannel.open(temp.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ)
            .use { writeChannel ->
                val headerReader = ByteBuffer.allocate(((if (keepHeaderOffset) headerOffset else 0) + 4).toInt())
                    .order(ByteOrder.LITTLE_ENDIAN)
                fc.position((if (keepHeaderOffset) 0 else headerOffset))
                readFully(headerReader, fc)
                headerReader.rewind()
                writeChannel.write(headerReader)

                newFormatVersion = formatVersion
                when (newFormatVersion) {
                    0 -> newHeaderSize = 32
                    1 -> newHeaderSize = 44
                    2, 3 -> newHeaderSize = 208
                }
                newSectorSizeShift = if (options.recompress) min(
                    options.newSectorSizeShift.toDouble(),
                    15.0
                ).toInt() else sectorSizeShift
                newDiscBlockSize = if (options.recompress) 512 * (1 shl newSectorSizeShift) else discBlockSize
                calcNewTableSize()

                val newBlocks = mutableListOf<BlockTable.Block?>()
                val newFiles = mutableListOf<String>()
                val existingFiles = listFile!!.files

                sortListfileEntries(existingFiles)

                if (attributes != null) {
                    attributes!!.setNames(existingFiles)
                }
                var currentPos = (if (keepHeaderOffset) headerOffset else 0) + headerSize

                for (fileName in filenameToData.keys) {
                    existingFiles.remove(fileName)
                }

                for (existingName in existingFiles) {
                    if (options.recompress && !existingName.endsWith(".wav")) {
                        val extracted = ByteBuffer.wrap(extractFileAsBytes(existingName))
                        filenameToData.put(existingName, extracted)
                    } else {
                        newFiles.add(existingName)
                        val pos = hashTable!!.getBlockIndexOfFile(existingName)
                        val b = blockTable!!.getBlockAtPos(pos)
                        val buf = ByteBuffer.allocate(b.compressedSize).order(ByteOrder.LITTLE_ENDIAN)
                        fc.position(headerOffset + b.filePos)
                        readFully(buf, fc)
                        buf.rewind()
                        val f = MpqFile(buf, b, discBlockSize, existingName)
                        val fileWriter =
                            writeChannel.map(FileChannel.MapMode.READ_WRITE, currentPos, b.compressedSize.toLong())
                        val newBlock =
                            BlockTable.Block(currentPos - (if (keepHeaderOffset) headerOffset else 0), 0, 0, b.flags)
                        newBlocks.add(newBlock)
                        f.writeFileAndBlock(newBlock, fileWriter)
                        currentPos += b.compressedSize.toLong()
                    }
                }

                val newFileMap = HashMap<String?, ByteBuffer?>()
                for (newFileName in filenameToData) {
                    if (newFileName == null) continue
                    val newFile: ByteBuffer = filenameToData.get(newFileName)!!
                    newFiles.add(newFileName)
                    newFileMap.put(newFileName, newFile)
                    val fileWriter = writeChannel.map(FileChannel.MapMode.READ_WRITE, currentPos, newFile.limit() * 2L)
                    val newBlock = BlockTable.Block(currentPos - (if (keepHeaderOffset) headerOffset else 0), 0, 0, 0)
                    newBlocks.add(newBlock)
                    MpqFile.writeFileAndBlock(newFile.array(), newBlock, fileWriter, newDiscBlockSize, options)
                    currentPos += newBlock.compressedSize.toLong()
                }
                if (addListfile && !listFile!!.files.isEmpty()) {
                    // Add listfile
                    newFiles.add("(listfile)")
                    val listfileArr = listFile!!.asByteArray()
                    val fileWriter =
                        writeChannel.map(FileChannel.MapMode.READ_WRITE, currentPos, listfileArr!!.size * 2L)
                    val newBlock = BlockTable.Block(
                        currentPos - (if (keepHeaderOffset) headerOffset else 0),
                        0,
                        0,
                        MpqFile.EXISTS or MpqFile.COMPRESSED or MpqFile.ENCRYPTED or MpqFile.ADJUSTED_ENCRYPTED
                    )
                    newBlocks.add(newBlock)
                    MpqFile.writeFileAndBlock(
                        listfileArr,
                        newBlock,
                        fileWriter,
                        newDiscBlockSize,
                        "(listfile)",
                        options
                    )
                    currentPos += newBlock.compressedSize.toLong()
                }

                // if (attributes != null) {
                // newFiles.add("(attributes)");
                // // Only generate attributes file when there has been one before
                // AttributesFile attributesFile = new AttributesFile(newFiles.size());
                // // Generate new values
                // long time = (new Date().getTime() + 11644473600000L) * 10000L;
                // for (int i = 0; i < newFiles.size() - 1; i++) {
                // String name = newFiles.get(i);
                // int entry = attributes.getEntry(name);
                // if (newFileMap.containsKey(name)){
                // // new file
                // attributesFile.setEntry(i, getCrc32(newFileMap.get(name)), time);
                // }else if (entry >= 0) {
                // // has timestamp
                // attributesFile.setEntry(i, getCrc32(name),
                // attributes.getTimestamps()[entry]);
                // } else {
                // // doesnt have timestamp
                // attributesFile.setEntry(i, getCrc32(name), time);
                // }
                // }
                // // newfiles don't contain the attributes file yet, hence -1
                // System.out.println("added attributes");
                // byte[] attrArr = attributesFile.buildFile();
                // fileWriter = writeChannel.map(MapMode.READ_WRITE, currentPos,
                // attrArr.length);
                // newBlock = new Block(currentPos - headerOffset, 0, 0, EXISTS |
                // COMPRESSED | ENCRYPTED | ADJUSTED_ENCRYPTED);
                // newBlocks.add(newBlock);
                // MpqFile.writeFileAndBlock(attrArr, newBlock, fileWriter,
                // newDiscBlockSize, "(attributes)");
                // currentPos += newBlock.getCompressedSize();
                // }
                newBlockSize = newBlocks.size

                newHashPos = currentPos - (if (keepHeaderOffset) headerOffset else 0)
                newBlockPos = newHashPos + newHashSize * 16L

                // generate new hash table
                val hashSize = newHashSize
                val hashTable = HashTable(hashSize)
                var blockIndex = 0
                for (file in newFiles) {

                    hashTable.setFileBlockIndex(file, HashTable.DEFAULT_LOCALE, blockIndex++)
                }

                // prepare hashtable for writing
                val hashTableBuffer = ByteBuffer.allocate(hashSize * 16)
                hashTable.writeToBuffer(hashTableBuffer)
                hashTableBuffer.flip()

                // encrypt hash table
                val encrypt = MPQEncryption(KEY_HASH_TABLE, false)
                encrypt.processSingle(hashTableBuffer)
                hashTableBuffer.flip()

                // write out hash table
                writeChannel.position(currentPos)
                writeFully(hashTableBuffer, writeChannel)
                currentPos = writeChannel.position()

                // write out block table
                val blocktableWriter = writeChannel.map(FileChannel.MapMode.READ_WRITE, currentPos, newBlockSize * 16L)
                blocktableWriter.order(ByteOrder.LITTLE_ENDIAN)
                BlockTable.writeNewBlocktable(newBlocks, newBlockSize, blocktableWriter)
                currentPos += newBlockSize * 16L

                newArchiveSize = currentPos + 1 - (if (keepHeaderOffset) headerOffset else 0)

                val headerWriter = writeChannel.map(
                    FileChannel.MapMode.READ_WRITE,
                    (if (keepHeaderOffset) headerOffset else 0L) + 4L,
                    headerSize + 4L
                )
                headerWriter.order(ByteOrder.LITTLE_ENDIAN)
                writeHeader(headerWriter)

                val tempReader = writeChannel.map(FileChannel.MapMode.READ_WRITE, 0, currentPos + 1)
                tempReader.position(0)

                fc.position(0)
                fc.write(tempReader)
                fc.truncate(fc.position())
                fc.close()
            }
        t = System.nanoTime() - t
    }

    private fun sortListfileEntries(remainingFiles: MutableList<String>?) {
        if (remainingFiles == null) return

        // Sort entries to preserve block table order
        remainingFiles.sortWith<String>(Comparator { o1: String, o2: String ->
            var pos1 = 999999999
            var pos2 = 999999999
            try {
                pos1 = hashTable!!.getBlockIndexOfFile(o1)
            } catch (_: IOException) {
            }
            try {
                pos2 = hashTable!!.getBlockIndexOfFile(o2)
            } catch (_: IOException) {
            }
            pos1 - pos2
        })
    }

    /**
     * Whether or not to keep the data before the actual mpq in the file
     *
     * @param keepHeaderOffset
     */
    fun setKeepHeaderOffset(keepHeaderOffset: Boolean) {
        this.keepHeaderOffset = keepHeaderOffset
    }


    /**
     * Get block table block table.
     *
     * @return the block table
     */
    fun getBlockTable(): BlockTable {
        return blockTable!!
    }

    fun getHashTable(): HashTable {
        return hashTable!!
    }

    /**
     * (non-Javadoc)
     *
     * @see Object.toString
     */
    override fun toString(): String {
        return ("JMpqEditor [headerSize=" + headerSize + ", archiveSize=" + archiveSize + ", formatVersion=" + formatVersion + ", discBlockSize=" + discBlockSize
                + ", hashPos=" + hashPos + ", blockPos=" + blockPos + ", hashSize=" + hashSize + ", blockSize=" + blockSize + ", hashMap=" + hashTable + "]")
    }

    val listfileEntries: MutableCollection<String?>
        /**
         * Returns an unmodifiable collection of all Listfile entries
         *
         * @return Listfile entries
         */
        get() = Collections.unmodifiableCollection<String?>(listFile!!.files)

    companion object {
        @JvmField
        val ARCHIVE_HEADER_MAGIC: Int =
            ByteBuffer.wrap(byteArrayOf('M'.code.toByte(), 'P'.code.toByte(), 'Q'.code.toByte(), 0x1A))
                .order(ByteOrder.LITTLE_ENDIAN).getInt()
        val USER_DATA_HEADER_MAGIC: Int =
            ByteBuffer.wrap(byteArrayOf('M'.code.toByte(), 'P'.code.toByte(), 'Q'.code.toByte(), 0x1B))
                .order(ByteOrder.LITTLE_ENDIAN).getInt()

        /**
         * Encryption key for hash table data.
         */
        private val KEY_HASH_TABLE: Int

        /**
         * Encryption key for block table data.
         */
        private val KEY_BLOCK_TABLE: Int

        init {
            val hasher = MPQHashGenerator.getFileKeyGenerator()
            hasher.process("(hash table)")
            KEY_HASH_TABLE = hasher.hash
            hasher.reset()
            hasher.process("(block table)")
            KEY_BLOCK_TABLE = hasher.hash
        }

        @JvmField
        var tempDir: File? = null

        /**
         * Utility method to fill a buffer from the given channel.
         *
         * @param buffer buffer to fill.
         * @param src    channel to fill from.
         * @throws IOException  if an exception occurs when reading.
         * @throws java.io.EOFException if EoF is encountered before buffer is full or channel is non
         * blocking.
         */

        private fun readFully(buffer: ByteBuffer, src: ReadableByteChannel) {
            while (buffer.hasRemaining()) {
                if (src.read(buffer) < 1) throw EOFException("Cannot read enough bytes.")
            }
        }

        /**
         * Utility method to write out a buffer to the given channel.
         *
         * @param buffer buffer to write out.
         * @param dest   channel to write to.
         * @throws IOException if an exception occurs when writing.
         */

        private fun writeFully(buffer: ByteBuffer, dest: WritableByteChannel) {
            while (buffer.hasRemaining()) {
                if (dest.write(buffer) < 1) throw EOFException("Cannot write enough bytes.")
            }
        }
    }
}
