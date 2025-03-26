package io.github.warraft.mpq4j.compression

import com.jcraft.jzlib.Deflater
import com.jcraft.jzlib.GZIPException
import com.jcraft.jzlib.Inflater

object JzLibHelper {
    private val inf = Inflater()

    private var defLvl = 0
    private var def: Deflater? = null

    fun inflate(bytes: ByteArray, offset: Int, uncompSize: Int): ByteArray {
        val uncomp = ByteArray(uncompSize)
        inf.init()
        inf.setInput(bytes, offset, bytes.size - 1, false)
        inf.setOutput(uncomp)
        while ((inf.total_out < uncompSize) && (inf.total_in < bytes.size)) {
            inf.avail_in = (1.also { inf.avail_out = it })
            val err = inf.inflate(0)
            if (err == 1) break
        }
        inf.end()
        return uncomp
    }

    var comp: ByteArray = ByteArray(1024)

    fun deflate(bytes: ByteArray, strongDeflate: Boolean): ByteArray {
        val created = tryCreateDeflater(if (strongDeflate) 9 else 1)

        if (comp.size < bytes.size) {
            comp = ByteArray(bytes.size)
        }
        if (!created) {
            def!!.init(if (strongDeflate) 9 else 1)
        }
        def!!.setInput(bytes)
        def!!.setOutput(comp)
        while ((def!!.total_in != bytes.size.toLong()) && (def!!.total_out < bytes.size)) {
            def!!.avail_in = (1.also { def!!.avail_out = it })
            def!!.deflate(0)
        }
        var err: Int
        do {
            def!!.avail_out = 1
            err = def!!.deflate(4)
        } while (err != 1)

        val temp = ByteArray(def!!.getTotalOut().toInt())
        System.arraycopy(comp, 0, temp, 0, def!!.getTotalOut().toInt())
        def!!.end()
        return temp
    }

    private fun tryCreateDeflater(lvl: Int): Boolean {
        if (def == null || lvl != defLvl) {
            try {
                def = Deflater(lvl)
                defLvl = lvl
                return true
            } catch (e: GZIPException) {
                throw RuntimeException(e)
            }
        }
        return false
    }
}
