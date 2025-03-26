import io.github.warraft.mpq4j.DebugHelper
import kotlin.test.Test

/**
 * Created by Frotty on 09.03.2017.
 */
class DegugHelperTests {
    @Test
    fun testDebugHelper() {
        assert(DebugHelper.bytesToHex(byteArrayOf(0)).equals("00", ignoreCase = true))
        assert(DebugHelper.bytesToHex(byteArrayOf(1)).equals("01", ignoreCase = true))
        assert(DebugHelper.bytesToHex(byteArrayOf(10)).equals("0A", ignoreCase = true))
        assert(DebugHelper.bytesToHex(byteArrayOf(16)).equals("10", ignoreCase = true))
    }
}
