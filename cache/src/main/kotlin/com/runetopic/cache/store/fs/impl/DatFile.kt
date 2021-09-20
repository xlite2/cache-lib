package com.runetopic.cache.store.fs.impl

import com.runetopic.cache.ReferenceTable
import com.runetopic.cache.exception.DatFileException
import com.runetopic.cache.exception.EndOfDatFileException
import com.runetopic.cache.store.Constants.SECTOR_SIZE
import com.runetopic.cache.store.fs.IDatFile
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * @author Tyler Telis
 * @email <xlitersps@gmail.com>
 */
internal class DatFile(
    file: File
): IDatFile  {
    private val datFile: RandomAccessFile = RandomAccessFile(file, "rw")

    override fun readReferenceTable(groupId: Int, referenceTable: ReferenceTable): ByteArray {
        var sector = referenceTable.sector
        val length = referenceTable.length
        if (groupId == 28)
            println(length)
        val id = referenceTable.id

        if (validateSector(sector, length).not()) return byteArrayOf()

        val buffer = ByteBuffer.allocate(length)
        val readBuffer = ByteBuffer.wrap(ByteArray(SECTOR_SIZE))

        var part = 0
        var readBytes = 0
        var nextSector: Int

        while (length > readBytes) {
            if (sector == 0) {
                throw EndOfDatFileException("Unexpected end of file. GroupId=[$groupId} ArchiveId=[$id] Length=[$length]")
            }

            datFile.seek((SECTOR_SIZE * sector).toLong())

            var blockLength = length - readBytes
            var headerLength: Byte
            var currentIndex: Int
            var currentPart: Int
            var currentContainerId: Int

            if (id > 0xFFFF) {
                headerLength = 10
                blockLength = adjustBlockLength(blockLength, headerLength)

                validateHeader(readBuffer.array(), headerLength, blockLength, groupId, id)

                currentContainerId = (readBuffer[0].toInt() and 0xFF shl 24
                        or (readBuffer[1].toInt() and 0xFF shl 16)
                        or (readBuffer[2].toInt() and 0xFF shl 8)
                        or (readBuffer[3].toInt() and 0xFF))
                currentPart = ((readBuffer[4].toInt() and 0xFF) shl 8) + (readBuffer[5].toInt() and 0xFF)
                nextSector = (readBuffer[6].toInt() and 0xFF shl 16
                        or (readBuffer[7].toInt() and 0xFF shl 8)
                        or (readBuffer[8].toInt() and 0xFF))
                currentIndex = (readBuffer[9].toInt() and 0xFF)

            } else {
                headerLength = 8
                blockLength = adjustBlockLength(blockLength, headerLength)

                validateHeader(readBuffer.array(), headerLength, blockLength, groupId, id)

                currentContainerId = (readBuffer[0].toInt() and 0xFF shl 8
                        or (readBuffer[1].toInt() and 0xFF))
                currentPart = ((readBuffer[2].toInt() and 0xFF) shl 8) + (readBuffer[3].toInt() and 0xFF)
                nextSector = (readBuffer[4].toInt() and 0xFF shl 16
                        or (readBuffer[5].toInt() and 0xFF shl 8)
                        or (readBuffer[6].toInt() and 0xFF))
                currentIndex = (readBuffer[7].toInt() and 0xFF)
            }

            validateData(id, currentContainerId, currentPart, part, groupId, currentIndex)
            validateNextSector(nextSector)

            buffer.put(readBuffer.array(), headerLength.toInt(), blockLength)
            readBytes += blockLength
            sector = nextSector
            ++part
        }

        buffer.flip()
        return buffer.array()
    }

    private fun adjustBlockLength(blockLength: Int, headerLength: Byte): Int {
        return if (blockLength <= SECTOR_SIZE - headerLength) {
            blockLength
        } else {
            SECTOR_SIZE - headerLength
        }
    }

    private fun validateData(
        archiveId: Int,
        currentArchiveId: Int,
        currentPart: Int,
        part: Int,
        index: Int,
        currentIndex: Int,
    ) {
        if (archiveId != currentArchiveId || currentPart != part || index != currentIndex) {
           throw DatFileException("DataFile mismatch Id={${currentIndex}} != {${index}}, ArchiveId={${currentArchiveId}} != {${archiveId}}, CurrentPart={${currentPart}} != {${part}}")
        }
    }

    private fun validateNextSector(nextSector: Int) {
        if (nextSector < 0 || datFile.length() / SECTOR_SIZE < nextSector) {
            throw DatFileException("Invalid next sector $nextSector")
        }
    }

    private fun validateHeader(
        buffer: ByteArray,
        headerLength: Byte,
        blockLength: Int,
        id: Int,
        containerId: Int,
    ) {
        if (datFile.read(buffer, 0, headerLength + blockLength) != headerLength + blockLength) {
            throw DatFileException("Header length mismatch for Id=[$id] Container=[$containerId]")
        }
    }

    private fun validateSector(sector: Int, length: Int): Boolean {
        if (sector <= 0L || datFile.length() / SECTOR_SIZE < sector) {
            return false//throw DatFileException("Could not read $length for sector $sector")
        }
        return true
    }

    override fun close() = datFile.close()
}