package com.xlite.cache

import com.xlite.cache.compression.Compression
import com.xlite.cache.exception.ProtocolException
import com.xlite.cache.extension.readUnsignedByte
import com.xlite.cache.extension.readUnsignedShort
import com.xlite.cache.store.fs.impl.IdxFile
import java.nio.ByteBuffer
import java.util.*

/**
 * @author Tyler Telis
 * @email <xlitersps@gmail.com>
 */
internal data class ReferenceTable(
    val idxFile: IdxFile,
    val id: Int,
    val sector: Int,
    val length: Int,
) {
    override fun hashCode(): Int {
        var hash = 7
        hash = 19 * hash + Objects.hashCode(this.idxFile)
        hash = 19 * hash + this.id
        hash = 19 * hash + this.sector
        hash = 19 * hash + this.length
        return hash
    }

    override fun equals(other: Any?): Boolean {
        when (other) {
            null -> {
                return false
            }
            else -> when {
                javaClass != other.javaClass -> {
                    return false
                }
                else -> {
                    val referenceTable: ReferenceTable = other as ReferenceTable
                    return when {
                        idxFile != referenceTable.idxFile -> {
                            false
                        }
                        id != referenceTable.id -> {
                            false
                        }
                        sector != referenceTable.sector -> {
                            false
                        }
                        this.length != referenceTable.length -> {
                            false
                        }
                        else -> true
                    }
                }
            }
        }
    }

    fun loadGroup(indexId: Int, whirlpool: ByteArray, data: ByteArray): Js5Group {
        val container = Compression.decompress(data, emptyArray())
        val buffer = ByteBuffer.wrap(container.data)
        val protocol = buffer.readUnsignedByte()
        var revision = 0

        if (protocol < 5 || protocol > 6) {
            throw ProtocolException("Unhandled protocol $protocol when reading index $this")
        }

        if (protocol >= 6) {
            revision = buffer.int
        }

        val hash = buffer.readUnsignedByte()
        val isNamed = (0x1 and hash) != 0
        val isUsingWhirlPool = (0x2 and hash) != 0

        val validArchivesCount = buffer.readUnsignedShort()

        var lastArchiveId = 0
        var biggestArchiveId = -1

        val validArchiveIds = IntArray(validArchivesCount)

        for (index in 0 until validArchivesCount) {
            validArchiveIds[index] = buffer.readUnsignedShort().let { lastArchiveId += it; lastArchiveId }
            if (validArchiveIds[index] > biggestArchiveId) biggestArchiveId = validArchiveIds[index]
        }

        val anIntArray2107 = IntArray(biggestArchiveId + 1)
        val nameHashes = readNameHashes(biggestArchiveId, validArchivesCount, isNamed, validArchiveIds, buffer)
        val crcs = readCRCS(biggestArchiveId, validArchivesCount, validArchiveIds, buffer)
        val whirlpools = readWhirlpool(isUsingWhirlPool, validArchivesCount, buffer, validArchiveIds)
        val revisions = readRevisions(biggestArchiveId, validArchivesCount, validArchiveIds, buffer)
        val validFileIds = readFileIds(biggestArchiveId, validArchivesCount, validArchiveIds, buffer)
        val files = readFiles(biggestArchiveId, validFileIds, validArchivesCount, validArchiveIds, buffer, anIntArray2107, isNamed)

        val archives = mutableListOf<Js5File>()

        (0 until validArchivesCount).forEach {
            archives.add(Js5File(
                id = it,
                indexId = indexId,
                nameHash = if (isNamed) nameHashes[validArchiveIds[it]] else -1,
                crc = crcs[validArchiveIds[it]],
                whirlpool = if (isUsingWhirlPool) whirlpools[validArchiveIds[it]] else byteArrayOf(),
                revision = revisions[validArchiveIds[it]],
                keys = intArrayOf(),
                entries = files[it]
            ))
        }

        return Js5Group(indexId, container.crc, whirlpool, container.compression, protocol, revision, isNamed, archives)
    }

    private fun readFiles(
        biggestArchiveId: Int,
        validFileIds: IntArray,
        validArchivesCount: Int,
        validArchiveIds: IntArray,
        buffer: ByteBuffer,
        anIntArray2107: IntArray,
        isNamed: Boolean,
    ): Array<Array<Js5FileEntry>> {
        val files = Array(biggestArchiveId + 1) { row -> Array(validFileIds[row]) { Js5FileEntry(-1)  } }

        (0 until validArchivesCount).forEach {
            val archiveId = validArchiveIds[it]
            val validFileCount = validFileIds[archiveId]
            var entryId = 0
            var currFileId = -1
            var fileId = 0

            while (validFileCount > fileId) {
                val lastFileId = buffer.readUnsignedShort()
                    .let { id -> entryId += id; entryId }
                    .also { files[archiveId][fileId] = Js5FileEntry(entryId) }
                if (currFileId < lastFileId) currFileId = lastFileId
                fileId++
            }
            anIntArray2107[archiveId] = currFileId + 1
//             if (validFileCount == currFileId + 1) files[archiveId] = Array(validFileCount) { FileEntry(-1)  }
        }

        if (isNamed) {
            (0 until validArchivesCount).forEach { count ->
                val archiveId = validArchiveIds[count]
                val fileCount = validFileIds[archiveId]
                (0 until fileCount).forEach {
                    files[archiveId][it].nameHash = buffer.int
                }
            }
        }
        return files
    }

    private fun readFileIds(
        biggestArchiveId: Int,
        validArchivesCount: Int,
        validArchiveIds: IntArray,
        buffer: ByteBuffer,
    ): IntArray {
        val validFileIds = IntArray(biggestArchiveId + 1)
        (0 until validArchivesCount).forEach {
            validFileIds[validArchiveIds[it]] = buffer.readUnsignedShort()
        }
        return validFileIds
    }

    private fun readRevisions(
        biggestArchiveId: Int,
        validArchivesCount: Int,
        validArchiveIds: IntArray,
        buffer: ByteBuffer,
    ): IntArray {
        val revisions = IntArray(biggestArchiveId + 1)
        (0 until validArchivesCount).forEach {
            revisions[validArchiveIds[it]] = buffer.int
        }
        return revisions
    }

    private fun readWhirlpool(
        usesWhirlpool: Boolean,
        validArchivesCount: Int,
        buffer: ByteBuffer,
        validArchiveIds: IntArray,
    ): Array<ByteArray> {
        val whirlpools = Array(64) { byteArrayOf() }
        if (usesWhirlpool) {
            (0 until validArchivesCount).forEach {
                val whirlpool = ByteArray(64)
                buffer.get(whirlpool, 0, 64)
                whirlpools[validArchiveIds[it]] = whirlpool
            }
        }
        return whirlpools
    }

    private fun readCRCS(
        biggestArchiveId: Int,
        validArchivesCount: Int,
        validArchiveIds: IntArray,
        buffer: ByteBuffer,
    ): IntArray {
        val crcs = IntArray(biggestArchiveId + 1)
        (0 until validArchivesCount).forEach {
            crcs[validArchiveIds[it]] = buffer.int
        }
        return crcs
    }

    private fun readNameHashes(
        biggestArchiveId: Int,
        validArchivesCount: Int,
        isNamed: Boolean,
        validArchiveIds: IntArray,
        buffer: ByteBuffer,
    ): IntArray {
        val nameHashes = IntArray(biggestArchiveId + 1) { -1 }

        if (isNamed.not()) return nameHashes

        (0 until validArchivesCount).forEach {
            nameHashes[validArchiveIds[it]] = buffer.int
        }
        return nameHashes
    }
}