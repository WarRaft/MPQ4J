package systems.crigges.jmpq3.compression

class RecompressOptions(@JvmField var recompress: Boolean) {
    @JvmField
    var useZopfli: Boolean = false

    @JvmField
    var iterations: Int = 16
    var newSectorSizeShift: Int = 3
}
