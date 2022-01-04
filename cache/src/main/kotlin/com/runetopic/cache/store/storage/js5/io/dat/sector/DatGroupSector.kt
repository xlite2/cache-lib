package com.runetopic.cache.store.storage.js5.io.dat.sector

import com.runetopic.cache.extension.toByteBuffer
import com.runetopic.cache.hierarchy.index.group.file.File
import com.runetopic.cache.store.storage.js5.io.dat.IDatSector

/**
 * @author Jordan Abraham
 */
data class DatGroupSector(
    val fileIds: Array<IntArray>,
    val fileNameHashes: Array<IntArray>,
    val data: ByteArray,
    val count: Int,
    val groupId: Int
) : IDatSector<Map<Int, File>> {

    @OptIn(ExperimentalStdlibApi::class)
    override fun decode(): Map<Int, File> {
        if (data.isEmpty()) return hashMapOf(Pair(0, File.DEFAULT))
        if (count <= 1) return hashMapOf(Pair(0, File(fileIds[groupId][0], fileNameHashes[groupId][0], data)))

        var position = data.size
        val chunks = data[--position].toInt() and 0xFF
        position -= chunks * (count * 4)
        val buffer = data.toByteBuffer()
        buffer.position(position)
        val filesSizes = IntArray(count)
        repeat(chunks) {
            var bytesRead = 0
            repeat(count) {
                bytesRead += buffer.int
                filesSizes[it] += bytesRead
            }
        }
        val filesDatas = Array(count) { byteArrayOf() }
        repeat(count) {
            filesDatas[it] = ByteArray(filesSizes[it])
            filesSizes[it] = 0
        }
        buffer.position(position)
        var offset = 0
        repeat(chunks) {
            var bytesRead = 0
            repeat(count) {
                bytesRead += buffer.int
                System.arraycopy(data, offset, filesDatas[it], filesSizes[it], bytesRead)
                offset += bytesRead
                filesSizes[it] += bytesRead
            }
        }
        return buildMap { repeat(count) { put(it, File(fileIds[groupId][it], fileNameHashes[groupId][it], filesDatas[it])) } }
    }

    override fun encode(override: Map<Int, File>): ByteArray {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DatGroupSector

        if (!fileIds.contentDeepEquals(other.fileIds)) return false
        if (!fileNameHashes.contentDeepEquals(other.fileNameHashes)) return false
        if (!data.contentEquals(other.data)) return false
        if (count != other.count) return false
        if (groupId != other.groupId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileIds.contentDeepHashCode()
        result = 31 * result + fileNameHashes.contentDeepHashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + count
        result = 31 * result + groupId
        return result
    }
}