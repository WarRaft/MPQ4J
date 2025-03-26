package io.github.warraft.mpq4j.compression

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class ADPCM(channelmax: Int) {
    private class Channel {
        var sampleValue: Short = 0
        var stepIndex: Byte = 0
    }

    private val state: Array<Channel?> = arrayOfNulls<Channel>(channelmax)

    init {
        var i = 0
        while (i < state.size) {
            state[i] = Channel()
            i += 1
        }
    }

    fun decompress(`in`: ByteBuffer, out: ByteBuffer, channeln: Int) {
        // prepare buffers
        `in`.order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.LITTLE_ENDIAN)

        val stepshift = (`in`.getShort().toInt() ushr 8).toByte()

        // initialize channels
        var i = 0
        while (i < channeln) {
            val chan = state[i]
            if (chan != null) {
                chan.stepIndex = INITIAL_ADPCM_STEP_INDEX
                chan.sampleValue = `in`.getShort()
                out.putShort(chan.sampleValue)
            }
            i += 1
        }

        var current = 0

        // decompress
        while (`in`.hasRemaining()) {
            val op = `in`.get()
            val chan = state[current]
            if (chan == null) continue

            if ((op.toInt() and 0x80) != 0) {
                when (op.toInt() and 0x7F) {
                    0 -> {
                        if (chan.stepIndex.toInt() != 0) chan.stepIndex = (chan.stepIndex - 1).toByte()
                        out.putShort(chan.sampleValue)
                        current = (current + 1) % channeln
                    }

                    1 -> {
                        chan.stepIndex = (chan.stepIndex + 8).toByte()
                        if (chan.stepIndex >= STEP_TABLE.size) chan.stepIndex = (STEP_TABLE.size - 1).toByte()
                    }

                    2 -> current = (current + 1) % channeln
                    else -> {
                        chan.stepIndex = (chan.stepIndex - 8).toByte()
                        if (chan.stepIndex < 0) chan.stepIndex = 0
                    }
                }
            } else {
                // adjust value
                val stepbase: Short = STEP_TABLE[chan.stepIndex.toInt()]
                var step = (stepbase.toInt() ushr stepshift.toInt()).toShort()
                var i = 0
                while (i < 6) {
                    if (((op.toInt() and 0xff) and (1 shl i)) != 0) step =
                        (step + (stepbase.toInt() shr i)).toShort()
                    i += 1
                }

                if ((op.toInt() and 0x40) != 0) {
                    chan.sampleValue =
                        max(
                            (chan.sampleValue.toInt() - step).toDouble(),
                            Short.Companion.MIN_VALUE.toDouble()
                        ).toInt()
                            .toShort()
                } else {
                    chan.sampleValue =
                        min(
                            (chan.sampleValue.toInt() + step).toDouble(),
                            Short.Companion.MAX_VALUE.toDouble()
                        ).toInt()
                            .toShort()
                }

                out.putShort(chan.sampleValue)

                chan.stepIndex = (chan.stepIndex + CHANGE_TABLE[op.toInt() and 0x1F]).toByte()
                if (chan.stepIndex < 0) chan.stepIndex = 0
                else if (chan.stepIndex >= STEP_TABLE.size) chan.stepIndex = (STEP_TABLE.size - 1).toByte()

                current = (current + 1) % channeln
            }
        }
    }

    companion object {
        private const val INITIAL_ADPCM_STEP_INDEX: Byte = 0x2C

        private val CHANGE_TABLE = byteArrayOf(
            -1, 0, -1, 4, -1, 2, -1, 6,
            -1, 1, -1, 5, -1, 3, -1, 7,
            -1, 1, -1, 5, -1, 3, -1, 7,
            -1, 2, -1, 4, -1, 6, -1, 8
        )

        private val STEP_TABLE = shortArrayOf(
            7, 8, 9, 10, 11, 12, 13, 14,
            16, 17, 19, 21, 23, 25, 28, 31,
            34, 37, 41, 45, 50, 55, 60, 66,
            73, 80, 88, 97, 107, 118, 130, 143,
            157, 173, 190, 209, 230, 253, 279, 307,
            337, 371, 408, 449, 494, 544, 598, 658,
            724, 796, 876, 963, 1060, 1166, 1282, 1411,
            1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024,
            3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484,
            7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
            15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
            32767
        )
    }
}
