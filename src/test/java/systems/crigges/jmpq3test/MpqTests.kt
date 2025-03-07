package systems.crigges.jmpq3test

import io.github.warraft.mpq4j.BlockTable
import io.github.warraft.mpq4j.MPQ4J
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.Test
import systems.crigges.jmpq3.HashTable
import systems.crigges.jmpq3.JMpqException
import systems.crigges.jmpq3.MPQOpenOption
import systems.crigges.jmpq3.MpqFile
import systems.crigges.jmpq3.compression.RecompressOptions
import systems.crigges.jmpq3.security.MPQEncryption
import java.io.File
import java.io.FileInputStream
import java.io.FilenameFilter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

/**
 * Created by Frotty on 06.03.2017.
 */
class MpqTests {
    private val log: Logger = LoggerFactory.getLogger(this.javaClass.getName())

    @Test
    @Throws(IOException::class)
    fun cryptoTest() {
        val bytes = "Hello World!".toByteArray()

        val workBuffer = ByteBuffer.allocate(bytes.size)
        val encryptor = MPQEncryption(-1011927184, false)
        encryptor.processFinal(ByteBuffer.wrap(bytes), workBuffer)
        workBuffer.flip()
        encryptor.changeKey(-1011927184, true)
        encryptor.processSingle(workBuffer)
        workBuffer.flip()

        //Assert.assertTrue(Arrays.equals(new byte[]{-96, -93, 89, -50, 43, -60, 18, -33, -31, -71, -81, 86}, a));
        //Assert.assertTrue(Arrays.equals(new byte[]{2, -106, -97, 38, 5, -82, -88, -91, -6, 63, 114, -31}, b));
        Assert.assertTrue(bytes.contentEquals(workBuffer.array()))
    }

    @Test
    @Throws(IOException::class)
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
        Assert.assertEquals(ht.getFileBlockIndex(fp1, defaultLocale), 0)
        Assert.assertEquals(ht.getFileBlockIndex(fp2, defaultLocale), 1)

        // deletion test
        ht.removeFile(fp2, defaultLocale)
        Assert.assertEquals(ht.getFileBlockIndex(fp1, defaultLocale), 0)
        Assert.assertFalse(ht.hasFile(fp2))

        // locale test
        ht.setFileBlockIndex(fp1, germanLocale, 2)
        ht.setFileBlockIndex(fp1, frenchLocale, 3)
        Assert.assertEquals(ht.getFileBlockIndex(fp1, defaultLocale), 0)
        Assert.assertEquals(ht.getFileBlockIndex(fp1, germanLocale), 2)
        Assert.assertEquals(ht.getFileBlockIndex(fp1, frenchLocale), 3)
        Assert.assertEquals(ht.getFileBlockIndex(fp1, russianLocale), 0)

        // file path deletion test
        ht.setFileBlockIndex(fp2, defaultLocale, 1)
        ht.removeFileAll(fp1)
        Assert.assertFalse(ht.hasFile(fp1))
        Assert.assertEquals(ht.getFileBlockIndex(fp2, defaultLocale), 1)

        // clean up
        listFile.close()
    }

    @Test
    fun testException() {
        Assert.expectThrows<JMpqException?>(JMpqException::class.java, Assert.ThrowingRunnable { BlockTable(ByteBuffer.wrap(ByteArray(0))).getBlockAtPos(-1) })
    }

    @Test
    @Throws(IOException::class)
    fun testRebuild() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            log.info(mpq.getName())
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
            Assert.assertTrue(bI > aI)
        }

        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also {
            it.insertByteArray("d", ByteArray(12))
            it.insertByteArray("c", ByteArray(12))
        }

        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also {
            val dI = it.getHashTable().getBlockIndexOfFile("d")
            val cI = it.getHashTable().getBlockIndexOfFile("c")
            Assert.assertTrue(cI > dI)
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
            Assert.assertTrue(mpqEditor.listfileEntries.contains("war3map.w3a"))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testRecompressBuild() {
        val mpqs: Array<File>? = mpqs
        val options = RecompressOptions(true)
        options.newSectorSizeShift = 15
        for (mpq in mpqs!!) {
            log.info(mpq.getName())
            val mpqEditor = MPQ4J(mpq, MPQOpenOption.FORCE_V0)
            val length = mpq.length()
            options.useZopfli = !options.useZopfli
            mpqEditor.close(true, true, options)
            val newlength = mpq.length()
            println("Size win: " + (length - newlength))
        }
    }

    @Test
    @Throws(IOException::class)
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
    @Throws(IOException::class)
    fun testExtractScriptFile() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            log.info("test extract script: " + mpq.getName())
            val mpqEditor = MPQ4J(mpq, MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0)
            val temp = File.createTempFile("war3mapj", "extracted", MPQ4J.tempDir)
            temp.deleteOnExit()
            if (mpqEditor.hasFile("war3map.j")) {
                val extractedFile = mpqEditor.extractFileAsString("war3map.j").replace("\\r\\n".toRegex(), "\n").replace("\\r".toRegex(), "\n")
                val existingFile = String(Files.readAllBytes(getFile("war3map.j").toPath())).replace("\\r\\n".toRegex(), "\n").replace("\\r".toRegex(), "\n")
                Assert.assertEquals(existingFile, extractedFile)
            }
            mpqEditor.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testExtractScriptFileBA() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            log.info("test extract script: " + mpq.getName())
            val mpqEditor = MPQ4J(Files.readAllBytes(mpq.toPath()), MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0)
            val temp = File.createTempFile("war3mapj", "extracted", MPQ4J.tempDir)
            temp.deleteOnExit()
            if (mpqEditor.hasFile("war3map.j")) {
                val extractedFile = mpqEditor.extractFileAsString("war3map.j").replace("\\r\\n".toRegex(), "\n").replace("\\r".toRegex(), "\n")
                val existingFile = String(Files.readAllBytes(getFile("war3map.j").toPath())).replace("\\r\\n".toRegex(), "\n").replace("\\r".toRegex(), "\n")
                Assert.assertEquals(existingFile, extractedFile)
            }
            mpqEditor.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testInsertDeleteRegularFile() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            //insertAndDelete(mpq, "Example.txt")
        }
    }

    @Test
    @Throws(IOException::class)
    fun testInsertByteArray() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            //insertByteArrayAndVerify(mpq, "Example.txt")
        }
    }

    @Test
    @Throws(IOException::class)
    fun testInsertDeleteZeroLengthFile() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            //insertAndDelete(mpq, "0ByteExample.txt")
        }
    }

    @Test
    @Throws(IOException::class)
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
    @Throws(IOException::class)
    fun testIncompressibleFile() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            log.info(mpq.getName())
            //insertAndVerify(mpq, "incompressible.w3u")
        }
    }

    @Test
    @Throws(IOException::class)
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
                Assert.expectThrows<IllegalArgumentException?>(IllegalArgumentException::class.java, Assert.ThrowingRunnable {
                    mpqEditor.insertByteArray("Test", "bytesasdadasdad".toByteArray())
                })
                Assert.expectThrows<IllegalArgumentException?>(IllegalArgumentException::class.java, Assert.ThrowingRunnable {
                    mpqEditor.insertByteArray("teST", "bytesasdadasdad".toByteArray())
                })
                mpqEditor.insertByteArray("teST", "bytesasdadasdad".toByteArray(), true)
            }
        }
    }

    @Throws(IOException::class)
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
            Assert.assertFalse(mpqEditor.hasFile(filename))
        }
    }

    @Throws(IOException::class)
    private fun verifyMpq(mpq: File, filename: String, hashBefore: String?, bytes: ByteArray?): MPQ4J {
        val hashAfter = TestHelper.md5(mpq)
        // If this fails, the mpq is not changed by the insert file command and something went wrong
        Assert.assertNotEquals(hashBefore, hashAfter)

        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            Assert.assertTrue(mpqEditor.hasFile(filename))
            val bytes2 = mpqEditor.extractFileAsBytes(filename)
            Assert.assertEquals(bytes, bytes2)
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
            Assert.assertFalse(mpqEditor.hasFile(filename))
        }
    }

    @Throws(IOException::class)
    private fun insertAndDelete(mpq: File, filename: String?) {
        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            if (!mpqEditor.isCanWrite) {
                return
            }
            Assert.assertFalse(mpqEditor.hasFile(filename))
            val hashBefore = TestHelper.md5(mpq)
            mpqEditor.insertFile(filename, getFile(filename))
            mpqEditor.deleteFile(filename)
            mpqEditor.insertFile(filename, getFile(filename))
            mpqEditor.close()

            val hashAfter = TestHelper.md5(mpq)
            // If this fails, the mpq is not changed by the insert file command and something went wrong
            Assert.assertNotEquals(hashBefore, hashAfter)
        }
        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            Assert.assertTrue(mpqEditor.hasFile(filename))
            mpqEditor.deleteFile(filename)
        }
        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            if (!mpqEditor.isCanWrite) {
                return
            }
            mpqEditor.insertFile(filename, getFile(filename), true)
            mpqEditor.insertFile(filename, getFile(filename), true)
            mpqEditor.deleteFile(filename)
        }
        MPQ4J(mpq, MPQOpenOption.READ_ONLY, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            Assert.assertFalse(mpqEditor.hasFile(filename))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testRemoveHeaderoffset() {
        val mpqs: Array<File>? = mpqs
        var mpq: File? = null
        for (mpq1 in mpqs!!) {
            if (mpq1.getName().startsWith("normal")) {
                mpq = mpq1
                break
            }
        }
        Assert.assertNotNull(mpq)

        log.info(mpq!!.getName())
        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            mpqEditor.setKeepHeaderOffset(false)
            mpqEditor.close()
            val bytes = ByteArray(4)
            FileInputStream(mpq).use { fis ->
                fis.read(bytes)
            }
            val order = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            Assert.assertEquals(order.getInt(), MPQ4J.ARCHIVE_HEADER_MAGIC)
        }
        MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
            Assert.assertTrue(mpqEditor.isCanWrite)
        }
    }

    private fun getFiles(dir: File): MutableSet<File> {
        val ret: MutableSet<File> = LinkedHashSet<File>()

        for (file in dir.listFiles()) {
            if (file.isDirectory()) ret.addAll(getFiles(file))
            else ret.add(file)
        }

        return ret
    }

    @Test
    @Throws(IOException::class)
    fun newBlocksizeBufferOverflow() {
        var mpq = File(MpqTests::class.java.getClassLoader().getResource("newBlocksizeBufferOverflow/mpq/newBlocksizeBufferOverflow.w3x").getFile())

        val targetMpq = mpq.toPath().resolveSibling("file1.mpq").toFile()

        targetMpq.delete()

        Files.copy(mpq.toPath(), targetMpq.toPath(), StandardCopyOption.REPLACE_EXISTING).toFile()

        mpq = targetMpq

        val resourceDir = "newBlocksizeBufferOverflow/insertions"

        val files = getFiles(File(MpqTests::class.java.getClassLoader().getResource("./" + resourceDir + "/").getFile()))

        val mpqEditor = MPQ4J(mpq, MPQOpenOption.FORCE_V0)

        for (file in files) {
            val inName = file.toString().substring(file.toString().lastIndexOf(resourceDir) + resourceDir.length + File.separator.length)

            mpqEditor.insertFile(inName, file)
        }

        mpqEditor.close()
    }

    @Test
    @Throws(IOException::class)
    fun testForGetMpqFileByBlock() {
        val mpqs: Array<File>? = mpqs
        for (mpq in mpqs!!) {
            if (mpq.getName() == "invalidHashSize.scx_copy") {
                continue
            }
            MPQ4J(mpq, MPQOpenOption.FORCE_V0).also { mpqEditor ->
                Assert.assertTrue(mpqEditor.mpqFilesByBlockTable.size > 0)
                val blockTable = mpqEditor.getBlockTable()
                Assert.assertNotNull(blockTable)
                for (block in blockTable.allVaildBlocks) {
                    if (block!!.hasFlag(MpqFile.ENCRYPTED)) {
                        continue
                    }
                    Assert.assertNotNull(mpqEditor.getMpqFileByBlock(block))
                }
            }
        }
    }

    companion object {
        private var files: Array<out File?>? = null

        @get:Throws(IOException::class)
        private val mpqs: Array<File>?
            get() {
                val files = File(MpqTests::class.java.getClassLoader().getResource("./mpqs/").file)
                    .listFiles(FilenameFilter { dir: File?, name: String? -> name!!.endsWith(".w3x") || name.endsWith("" + ".mpq") || name.endsWith(".scx") })
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

        @AfterMethod
        @Throws(IOException::class)
        fun clearFiles() {
            if (files != null) {
                for (file in files) {
                    Files.deleteIfExists(file?.toPath())
                }
            }
        }

        private fun getFile(name: String?): File {
            return File(MpqTests::class.java.getClassLoader().getResource(name).getFile())
        }
    }
}
