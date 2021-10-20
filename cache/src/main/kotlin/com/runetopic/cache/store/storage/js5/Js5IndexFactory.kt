package com.runetopic.cache.store.storage.js5

import com.runetopic.cache.codec.Container
import com.runetopic.cache.codec.decompress
import com.runetopic.cache.exception.ProtocolException
import com.runetopic.cache.extension.readUnsignedByte
import com.runetopic.cache.extension.readUnsignedIntShortSmart
import com.runetopic.cache.extension.readUnsignedShort
import com.runetopic.cache.hierarchy.index.Index
import com.runetopic.cache.hierarchy.index.group.Group
import com.runetopic.cache.hierarchy.index.group.file.File
import java.nio.ByteBuffer
import java.util.zip.ZipException

/**
 * @author Jordan Abraham
 */
internal fun loadIndex(
    datFile: IDatFile,
    idxFile: IIdxFile,
    whirlpool: ByteArray,
    decompressed: Container
): Index {
    val buffer = ByteBuffer.wrap(decompressed.data)
    val crc = decompressed.crc
    val compression = decompressed.compression
    val protocol = buffer.readUnsignedByte()
    val revision = when {
        protocol < 5 || protocol > 7 -> throw ProtocolException("Unhandled protocol $protocol")
        protocol >= 6 -> buffer.int
        else -> 0
    }
    val hash = buffer.readUnsignedByte()
    val count = if (protocol >= 7) buffer.readUnsignedIntShortSmart() else buffer.readUnsignedShort()

    val groupTables = mutableListOf<ByteArray>()
    (0 until count).forEach {
        groupTables.add(datFile.readReferenceTable(idxFile.id(), idxFile.loadReferenceTable(it)))
    }

    val isNamed = (0x1 and hash) != 0
    val isUsingWhirlpool = (0x2 and hash) != 0

    val groupIds = IntArray(count)
    var lastGroupId = 0
    var biggest = -1
    (0 until count).forEach {
        groupIds[it] = if (protocol >= 7) { buffer.readUnsignedIntShortSmart() } else { buffer.readUnsignedShort() }
            .let { id -> lastGroupId += id; lastGroupId }
        if (groupIds[it] > biggest) biggest = groupIds[it]
    }

    val largestGroupId = biggest + 1
    val groupNameHashes = groupNameHashes(largestGroupId, count, isNamed, groupIds, buffer)
    val groupCrcs = groupCrcs(largestGroupId, count, groupIds, buffer)
    val groupWhirlpools = groupWhirlpools(largestGroupId, isUsingWhirlpool, count, buffer, groupIds)
    val groupRevisions = groupRevisions(largestGroupId, count, groupIds, buffer)
    val groupFileIds = groupFileIds(largestGroupId, count, groupIds, buffer, protocol)
    val fileIds = fileIds(largestGroupId, groupFileIds, count, groupIds, buffer, protocol)
    val fileNameHashes = fileNameHashes(largestGroupId, groupFileIds, count, groupIds, buffer, isNamed)

    val groups = hashMapOf<Int, Group>()
    (0 until count).forEach {
        val groupId = groupIds[it]
        groups[it] = (Group(
            groupId,
            groupNameHashes[groupId],
            groupCrcs[groupId],
            groupWhirlpools[groupId],
            groupRevisions[groupId],
            intArrayOf(),//TODO
            groupFiles(fileIds, fileNameHashes, groupTables[it], groupFileIds[it], it),
            groupTables[it]
        ))
    }
    return Index(idxFile.id(), crc, whirlpool, compression, protocol, revision, isNamed, groups)
}

private fun groupFileIds(
    largestGroupId: Int,
    count: Int,
    groupIds: IntArray,
    buffer: ByteBuffer,
    protocol: Int
): IntArray {
    val groupFileIds = IntArray(largestGroupId)
    (0 until count).forEach {
        groupFileIds[groupIds[it]] = if (protocol >= 7) buffer.readUnsignedIntShortSmart() else buffer.readUnsignedShort()
    }
    return groupFileIds
}

private fun groupRevisions(
    largestGroupId: Int,
    count: Int,
    groupIds: IntArray,
    buffer: ByteBuffer
): IntArray {
    val revisions = IntArray(largestGroupId)
    (0 until count).forEach {
        revisions[groupIds[it]] = buffer.int
    }
    return revisions
}

private fun groupWhirlpools(
    largestGroupId: Int,
    usesWhirlpool: Boolean,
    count: Int,
    buffer: ByteBuffer,
    groupIds: IntArray
): Array<ByteArray> {
    val whirlpools = Array(largestGroupId) { ByteArray(64) }
    if (usesWhirlpool.not()) return whirlpools

    (0 until count).forEach {
        val whirlpool = ByteArray(64)
        buffer.get(whirlpool)
        whirlpools[groupIds[it]] = whirlpool
    }
    return whirlpools
}

private fun groupCrcs(
    largestGroupId: Int,
    count: Int,
    groupIds: IntArray,
    buffer: ByteBuffer
): IntArray {
    val crcs = IntArray(largestGroupId)
    (0 until count).forEach {
        crcs[groupIds[it]] = buffer.int
    }
    return crcs
}

private fun groupNameHashes(
    largestGroupId: Int,
    count: Int,
    isNamed: Boolean,
    groupIds: IntArray,
    buffer: ByteBuffer
): IntArray {
    val nameHashes = IntArray(largestGroupId) { -1 }
    if (isNamed.not()) return nameHashes

    (0 until count).forEach {
        nameHashes[groupIds[it]] = buffer.int
    }
    return nameHashes
}

private fun fileIds(
    largestGroupId: Int,
    validFileIds: IntArray,
    count: Int,
    groupIds: IntArray,
    buffer: ByteBuffer,
    protocol: Int
): Array<IntArray> {
    val fileIds = Array(largestGroupId) { IntArray(validFileIds[it]) }
    (0 until count).forEach {
        val groupId = groupIds[it]
        var currentFileId = 0
        (0 until validFileIds[groupId]).forEach { fileId ->
            if (protocol >= 7) { buffer.readUnsignedIntShortSmart() } else { buffer.readUnsignedShort() }
                .let { i -> currentFileId += i; currentFileId }
                .also { fileIds[groupId][fileId] = currentFileId }
        }
    }
    return fileIds
}

private fun fileNameHashes(
    largestGroupId: Int,
    validFileIds: IntArray,
    count: Int,
    groupIds: IntArray,
    buffer: ByteBuffer,
    isNamed: Boolean
): Array<IntArray> {
    val fileNameHashes = Array(largestGroupId) { IntArray(validFileIds[it]) }
    if (isNamed) {
        (0 until count).forEach {
            val groupId = groupIds[it]
            (0 until validFileIds[groupId]).forEach { fileId ->
                fileNameHashes[groupId][fileId] = buffer.int
            }
        }
    }
    return fileNameHashes
}

internal fun groupFiles(
    fileIds: Array<IntArray>,
    fileNameHashes: Array<IntArray>,
    groupReferenceTableData: ByteArray,
    count: Int,
    groupId: Int
): Map<Int, File> {
    if (groupReferenceTableData.isEmpty()) return hashMapOf(Pair(0, File.DEFAULT))

    val src: ByteArray = try {
        groupReferenceTableData.decompress()
    } catch (exception: ZipException) {
        groupReferenceTableData
    }

    if (count == 1) {
        return hashMapOf(Pair(0, File(fileIds[groupId][0], fileNameHashes[groupId][0], src)))
    }

    var position = src.size
    val chunks = src[--position].toInt() and 0xFF
    position -= chunks * (count * 4)
    val buffer = ByteBuffer.wrap(src)
    buffer.position(position)
    val filesSizes = IntArray(count)
    (0 until chunks).forEach { _ ->
        var read = 0
        (0 until count).forEach {
            read += buffer.int
            filesSizes[it] += read
        }
    }
    val filesDatas = Array(count) { byteArrayOf() }
    (0 until count).forEach {
        filesDatas[it] = ByteArray(filesSizes[it])
        filesSizes[it] = 0
    }
    buffer.position(position)
    var offset = 0
    (0 until chunks).forEach { _ ->
        var read = 0
        (0 until count).forEach {
            read += buffer.int
            System.arraycopy(src, offset, filesDatas[it], filesSizes[it], read)
            offset += read
            filesSizes[it] += read
        }
    }

    val files = hashMapOf<Int, File>()
    (0 until count).forEach {
        files[it] = File(fileIds[groupId][it], fileNameHashes[groupId][it], filesDatas[it])
    }
    return files
}