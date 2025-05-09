package io.github.warraft.mpq4j.compression

class RecompressOptions(@JvmField var recompress: Boolean) {
    @JvmField
    var useZopfli: Boolean = false

    @JvmField
    var iterations: Int = 16
    var newSectorSizeShift: Int = 3
}
