package systems.crigges.jmpq3test

import org.testng.Assert
import org.testng.annotations.Test
import io.github.warraft.mpq4j.DebugHelper

/**
 * Created by Frotty on 09.03.2017.
 */
class DegugHelperTests {
    @Test
    fun testDebugHelper() {
        Assert.assertTrue(DebugHelper.bytesToHex(byteArrayOf(0)).equals("00", ignoreCase = true))
        Assert.assertTrue(DebugHelper.bytesToHex(byteArrayOf(1)).equals("01", ignoreCase = true))
        Assert.assertTrue(DebugHelper.bytesToHex(byteArrayOf(10)).equals("0A", ignoreCase = true))
        Assert.assertTrue(DebugHelper.bytesToHex(byteArrayOf(16)).equals("10", ignoreCase = true))
    }
}
