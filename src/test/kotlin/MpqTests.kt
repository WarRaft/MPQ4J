import io.github.warraft.mpq4j.*
import io.github.warraft.mpq4j.compression.RecompressOptions
import io.github.warraft.mpq4j.security.MPQEncryption
import java.io.File
import java.io.FileInputStream
import java.io.FilenameFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.test.*

/**
 * Created by Frotty on 06.03.2017.
 */
class MpqTests {

    @Test
    fun cryptoTest() {
        val bytes = "Hello World!".toByteArray()

        val workBuffer = ByteBuffer.allocate(bytes.size)
        val encryptor = MPQEncryption(-1011927184, false)
        encryptor.processFinal(ByteBuffer.wrap(bytes), workBuffer)
        workBuffer.flip()
        encryptor.changeKey(-1011927184, true)
        encryptor.processSingle(workBuffer)
        workBuffer.flip()

        //assert(Arrays.equals(new byte[]{-96, -93, 89, -50, 43, -60, 18, -33, -31, -71, -81, 86}, a));
        //assert(Arrays.equals(new byte[]{2, -106, -97, 38, 5, -82, -88, -91, -6, 63, 114, -31}, b));
        assert(bytes.contentEquals(workBuffer.array()))
    }

    @Test
    fun hashTableTest() {
        // get real example file paths
        val listFileFile = javaClass.getClassLoader().getResourceAsStream("DefaultListfile.txt")
        val listFile = Scanner(listFileFile)

        val fp1 = listFile.nextLine()
        val fp2 = listFile.nextLine()

        // small test hash table
        val ht = HashTable(8)
        val defaultLocale = HashTable.DEFAULT_LOCALE
        val germanLocale: Short = 0x407
        val frenchLocale: Short = 0x40c
        val russianLocale: Short = 0x419

        // assignment test
        ht.setFileBlockIndex(fp1, defaultLocale, 0)
        ht.setFileBlockIndex(fp2, defaultLocale, 1)
        assertEquals(ht.getFileBlockIndex(fp1, defaultLocale), 0)
        assertEquals(ht.getFileBlockIndex(fp2, defaultLocale), 1)

        // deletion test
        ht.removeFile(fp2, defaultLocale)
        assertEquals(ht.getFileBlockIndex(fp1, defaultLocale), 0)
        assertFalse(ht.hasFile(fp2))

        // locale test
        ht.setFileBlockIndex(fp1, germanLocale, 2)
        ht.setFileBlockIndex(fp1, frenchLocale, 3)
        assertEquals(ht.getFileBlockIndex(fp1, defaultLocale), 0)
        assertEquals(ht.getFileBlockIndex(fp1, germanLocale), 2)
        assertEquals(ht.getFileBlockIndex(fp1, frenchLocale), 3)
        assertEquals(ht.getFileBlockIndex(fp1, russianLocale), 0)

        // file path deletion test
        ht.setFileBlockIndex(fp2, defaultLocale, 1)
        ht.removeFileAll(fp1)
        assertFalse(ht.hasFile(fp1))
        assertEquals(ht.getFileBlockIndex(fp2, defaultLocale), 1)

        // clean up
        listFile.close()
    }

    @Test
    fun testException() {
        assertFailsWith<Exception> { BlockTable(ByteBuffer.wrap(ByteArray(0))).getBlockAtPos(-1) }
    }

    @Test

    fun testRebuild() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            println(mpq.getName())
            val mpqEditor = MPQ4J(mpq, MPQOpenOption.FORCE_V0)
            if (mpqEditor.isCanWrite) {
                mpqEditor.deleteFile("(listfile)")
            }
            mpqEditor.close(false, false, false)
        }
    }

    fun testInsertOrder() {
        val mpq = Files.copy(getFile("mpqs/normalMap.w3x").toPath(), Paths.get("temp.w3x")).toFile()

        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also {
            it.insertByteArray("a", ByteArray(12))
            it.insertByteArray("b", ByteArray(12))
        }

        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also {
            val aI = it.getHashTable().getBlockIndexOfFile("a")
            val bI = it.getHashTable().getBlockIndexOfFile("b")
            assert(bI > aI)
        }

        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also {
            it.insertByteArray("d", ByteArray(12))
            it.insertByteArray("c", ByteArray(12))
        }

        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also {
            val dI = it.getHashTable().getBlockIndexOfFile("d")
            val cI = it.getHashTable().getBlockIndexOfFile("c")
            assert(cI > dI)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testExternalListfile() {
        val mpq: File = getFile("mpqs/normalMap.w3x")
        val listFile: File = getFile("listfile.txt")
        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            if (mpqEditor.isCanWrite) {
                mpqEditor.deleteFile("(listfile)")
            }
            mpqEditor.setExternalListfile(listFile)
            assert(mpqEditor.listfileEntries.contains("war3map.w3a"))
        }
    }

    @Test

    fun testRecompressBuild() {
        val mpqs: Array<File>? = mpqs
        val options = RecompressOptions(true)
        options.newSectorSizeShift = 15
        for (mpq in mpqs!!) {
            println(mpq.getName())
            val mpqEditor = MPQ4J(mpq, MPQOpenOption.FORCE_V0)
            val length = mpq.length()
            options.useZopfli = !options.useZopfli
            mpqEditor.close(true, true, options)
            val newlength = mpq.length()
            println("Size win: " + (length - newlength))
        }
    }

    @Test

    fun testExtractAll() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            val mpqEditor = MPQ4J(mpq, MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0)
            val file = File("out/")
            file.mkdirs()
            mpqEditor.extractAllFiles(file)
            mpqEditor.close()
        }
    }

    @Test

    fun testExtractScriptFile() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            println("test extract script: " + mpq.getName())
            val mpqEditor = MPQ4J(mpq, MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0)
            val temp = File.createTempFile("war3mapj", "extracted", MPQ4J.tempDir)
            temp.deleteOnExit()
            if (mpqEditor.hasFile("war3map.j")) {
                val extractedFile = mpqEditor.extractFileAsString("war3map.j").replace("\\r\\n".toRegex(), "\n")
                    .replace("\\r".toRegex(), "\n")
                val existingFile =
                    String(Files.readAllBytes(getFile("war3map.j").toPath())).replace("\\r\\n".toRegex(), "\n")
                        .replace("\\r".toRegex(), "\n")
                assertEquals(existingFile, extractedFile)
            }
            mpqEditor.close()
        }
    }

    @Test

    fun testExtractScriptFileBA() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            println("test extract script: " + mpq.getName())
            val mpqEditor = MPQ4J(Files.readAllBytes(mpq.toPath()), MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0)
            val temp = File.createTempFile("war3mapj", "extracted", MPQ4J.tempDir)
            temp.deleteOnExit()
            if (mpqEditor.hasFile("war3map.j")) {
                val extractedFile = mpqEditor.extractFileAsString("war3map.j").replace("\\r\\n".toRegex(), "\n")
                    .replace("\\r".toRegex(), "\n")
                val existingFile =
                    String(Files.readAllBytes(getFile("war3map.j").toPath())).replace("\\r\\n".toRegex(), "\n")
                        .replace("\\r".toRegex(), "\n")
                assertEquals(existingFile, extractedFile)
            }
            mpqEditor.close()
        }
    }

    @Test
    fun testInsertDeleteRegularFile() {
        val mpqs: Array<File>? = mpqs
        if (mpqs == null) return

        val p = Paths.get("src", "test", "resources", "Example.txt").toFile()

        for (mpq in mpqs) {
            println("✅ ${mpq.name}")

            MPQ4J(mpq, MPQOpenOption.FORCE_V0).apply {
                if (!isCanWrite) return
                assertFalse(hasFile(p.name))
                val hashBefore = TestHelper.md5(mpq)
                insertFile(p.name, p)
                deleteFile(p.name)
                insertFile(p.name, p)
                close()

                val hashAfter = TestHelper.md5(mpq)
                assertNotEquals(hashBefore, hashAfter)
            }

            MPQ4J(mpq, MPQOpenOption.FORCE_V0).apply {
                assert(hasFile(p.name))
                deleteFile(p.name)
                close()
            }

            MPQ4J(mpq, MPQOpenOption.FORCE_V0).apply {
                if (!isCanWrite) return
                insertFile(p.name, p, true)
                insertFile(p.name, p, true)
                deleteFile(p.name)
                assertFalse(hasFile(p.name))
                close()
            }

            MPQ4J(mpq, MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0).apply {
                //TODO
                //assertFalse(hasFile(p.name))
                close()
            }
        }
    }

    @Test

    fun testInsertByteArray() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            //insertByteArrayAndVerify(mpq, "Example.txt")
        }
    }

    @Test

    fun testInsertDeleteZeroLengthFile() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            //insertAndDelete(mpq, "0ByteExample.txt")
        }
    }

    @Test

    fun testMultipleInstances() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            val mpqEditors = arrayOf<MPQ4J>(
                MPQ4J(mpq, MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0),
                MPQ4J(mpq, MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0),
                MPQ4J(mpq, MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0)
            )
            for (mpqEditor1 in mpqEditors) {
                mpqEditor1.extractAllFiles(MPQ4J.tempDir!!)
            }
            for (mpqEditor in mpqEditors) {
                mpqEditor.close()
            }
        }
    }

    @Test

    fun testIncompressibleFile() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            println(mpq.getName())
            //insertAndVerify(mpq, "incompressible.w3u")
        }
    }

    @Test

    fun testDuplicatePaths() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            if (mpq.getName() == "invalidHashSize.scx_copy") {
                continue
            }
            MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
                if (!mpqEditor.isCanWrite) {
                    return
                }
                mpqEditor.insertByteArray("Test", "bytesasdadasdad".toByteArray())

                assertFailsWith<IllegalArgumentException> {
                    mpqEditor.insertByteArray("Test", "bytesasdadasdad".toByteArray())
                }

                assertFailsWith<IllegalArgumentException> {
                    mpqEditor.insertByteArray("teST", "bytesasdadasdad".toByteArray())
                }
                mpqEditor.insertByteArray("teST", "bytesasdadasdad".toByteArray(), true)
            }
        }
    }


    private fun insertByteArrayAndVerify(mpq: File, filename: String) {
        val hashBefore: String?
        val bytes: ByteArray?

        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            if (!mpqEditor.isCanWrite) {
                return
            }
            val file: File = getFile(filename)
            hashBefore = TestHelper.md5(mpq)
            bytes = Files.readAllBytes(file.toPath())
            mpqEditor.insertByteArray(filename, Files.readAllBytes(getFile(filename).toPath()))
        }
        verifyMpq(mpq, filename, hashBefore, bytes).also { mpqEditor ->
            assertFalse(mpqEditor.hasFile(filename))
        }
    }


    private fun verifyMpq(mpq: File, filename: String, hashBefore: String?, bytes: ByteArray?): MPQ4J {
        val hashAfter = TestHelper.md5(mpq)
        // If this fails, the mpq is not changed by the insert file command and something went wrong
        assertNotEquals(hashBefore, hashAfter)

        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            assert(mpqEditor.hasFile(filename))
            val bytes2 = mpqEditor.extractFileAsBytes(filename)
            assertEquals(bytes, bytes2)
            mpqEditor.deleteFile(filename)
        }
        return MPQ4J(mpq, MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0)
    }

    private fun insertAndVerify(mpq: File, filename: String) {
        val hashBefore: String?
        val bytes: ByteArray?
        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            if (!mpqEditor.isCanWrite) {
                return
            }
            val file: File = getFile(filename)
            hashBefore = TestHelper.md5(mpq)
            bytes = Files.readAllBytes(file.toPath())
            mpqEditor.insertFile(filename, getFile(filename))
        }
        verifyMpq(mpq, filename, hashBefore, bytes).also { mpqEditor ->
            assertFalse(mpqEditor.hasFile(filename))
        }
    }

    @Test

    fun testRemoveHeaderoffset() {
        val mpqs: Array<File>? = mpqs
        var mpq: File? = null
        for (mpq1 in mpqs!!) {
            if (mpq1.getName().startsWith("normal")) {
                mpq = mpq1
                break
            }
        }
        assertNotNull(mpq)

        println(mpq.getName())
        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            mpqEditor.setKeepHeaderOffset(false)
            mpqEditor.close()
            val bytes = ByteArray(4)
            FileInputStream(mpq).use { fis ->
                fis.read(bytes)
            }
            val order = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(order.getInt(), MPQ4J.ARCHIVE_HEADER_MAGIC)
        }
        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            assert(mpqEditor.isCanWrite)
        }
    }

    private fun getFiles(dir: File): MutableSet<File> {
        val ret: MutableSet<File> = LinkedHashSet<File>()

        for (file in dir.listFiles()!!) {
            if (file.isDirectory()) ret.addAll(getFiles(file))
            else ret.add(file)
        }

        return ret
    }

    @Test

    fun newBlocksizeBufferOverflow() {
        var mpq: File =
            Paths.get("src", "test", "resources", "newBlocksizeBufferOverflow", "mpq", "newBlocksizeBufferOverflow.w3x")
                .toFile()

        val targetMpq = mpq.toPath().resolveSibling("file1.mpq").toFile()

        targetMpq.delete()

        Files.copy(mpq.toPath(), targetMpq.toPath(), StandardCopyOption.REPLACE_EXISTING).toFile()

        mpq = targetMpq

        val resourceDir = "newBlocksizeBufferOverflow/insertions"

        val files = listFilesRecursive(
            Paths.get("src", "test", "resources", "newBlocksizeBufferOverflow", "insertions").toFile()
        )

        val mpqEditor = MPQ4J(mpq, MPQOpenOption.FORCE_V0)

        for (file in files) {
            val inName = file.toString()
                .substring(file.toString().lastIndexOf(resourceDir) + resourceDir.length + File.separator.length)
            mpqEditor.insertFile(inName, file)
        }

        mpqEditor.close()
    }

    @Test

    fun testForGetMpqFileByBlock() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            if (mpq.getName() == "invalidHashSize.scx_copy") {
                continue
            }
            MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
                assert(mpqEditor.mpqFilesByBlockTable.isNotEmpty())
                val blockTable = mpqEditor.getBlockTable()
                assertNotNull(blockTable)
                for (block in blockTable.allVaildBlocks) {
                    if (block!!.hasFlag(MpqFile.ENCRYPTED)) {
                        continue
                    }
                    assertNotNull(mpqEditor.getMpqFileByBlock(block))
                }
            }
        }
    }

    companion object {
        private var files: Array<out File?>? = null


        private val mpqs: Array<File>?
            get() {
                //val files = File(MpqTests::class.java.getClassLoader().getResource("./mpqs/").file)
                val files = Paths.get("src", "test", "resources", "mpqs").toFile()
                    .listFiles(FilenameFilter { dir: File?, name: String? ->
                        name!!.endsWith(".w3x") || name.endsWith(".mpq") || name.endsWith(
                            ".scx"
                        )
                    })
                if (files != null) {
                    for (i in files.indices) {
                        val target = files[i].toPath().resolveSibling(files[i].getName() + "_copy")
                        files[i] = Files.copy(
                            files[i].toPath(), target,
                            StandardCopyOption.REPLACE_EXISTING
                        ).toFile()
                    }
                }
                Companion.files = files
                return files
            }

        private fun getFile(name: String): File {
            return Paths.get("src", "test", "resources", name).toFile()
        }
    }
}

fun listFilesRecursive(dir: File): List<File> = dir.walk().filter { it.isFile }.toList()
