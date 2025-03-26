package io.github.warraft.mpq4j

import io.github.warraft.mpq4j.HashTable
import java.nio.charset.StandardCharsets
import java.util.Scanner

class Listfile {
    val fileMap: java.util.HashMap<Long, String> = HashMap<Long, String>()

    constructor(file: ByteArray) {
        val list = String(file, StandardCharsets.UTF_8)
        val sc = Scanner(list)
        while (sc.hasNextLine()) {
            addFile(sc.nextLine())
        }
        sc.close()
    }

    constructor()

    val files: MutableList<String>
        get() = this.fileMap.values.toMutableList()

    fun addFile(name: String) {
        val key = HashTable.calculateFileKey(name)
        if (!name.isEmpty() && !this.fileMap.containsKey(key)) {
            this.fileMap.put(key, name)
        }
    }

    fun removeFile(name: String) {
        val key = HashTable.calculateFileKey(name)
        this.fileMap.remove(key)
    }

    fun containsFile(name: String): Boolean {
        val key = HashTable.calculateFileKey(name)
        return fileMap.containsKey(key)
    }

    fun asByteArray(): ByteArray? {
        val temp = StringBuilder()
        for (entry in this.fileMap.values) {
            temp.append(entry)
            temp.append("\r\n")
        }
        return temp.toString().toByteArray()
    }
}
