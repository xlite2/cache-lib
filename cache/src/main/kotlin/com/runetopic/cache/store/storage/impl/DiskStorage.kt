package com.runetopic.cache.store.storage.impl

import com.github.michaelbull.logging.InlineLogger
import com.runetopic.cache.Js5File
import com.runetopic.cache.Js5FileEntry
import com.runetopic.cache.Js5Group
import com.runetopic.cache.ReferenceTable
import com.runetopic.cache.extension.whirlpool
import com.runetopic.cache.store.Store
import com.runetopic.cache.store.Constants
import com.runetopic.cache.store.fs.IDatFile
import com.runetopic.cache.store.fs.IIdxFile
import com.runetopic.cache.store.fs.impl.DatFile
import com.runetopic.cache.store.fs.impl.IdxFile
import com.runetopic.cache.store.storage.IStorage
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer

/**
 * @author Tyler Telis
 * @email <xlitersps@gmail.com>
 */
internal class DiskStorage(
    private val directory: File
) : IStorage {
    private var masterIdxFile: IIdxFile
    private var datFile: IDatFile
    private var idxFiles: ArrayList<IdxFile> = arrayListOf()
    private var referenceTables: ArrayList<ReferenceTable> = arrayListOf()
    private val logger = InlineLogger()

    init {
        val masterIndexFile = File("${directory}/${Constants.MAIN_FILE_255}")

        if (masterIndexFile.exists().not()) {
            throw FileNotFoundException("Missing ${Constants.MAIN_FILE_255} in directory ${directory}/${Constants.MAIN_FILE_255}")
        }

        val datFile = File("${directory}/${Constants.MAIN_FILE_DAT}")

        if (datFile.exists().not()) {
            throw FileNotFoundException("Missing ${Constants.MAIN_FILE_DAT} in directory ${directory}/${Constants.MAIN_FILE_DAT}")
        }

        this.masterIdxFile = IdxFile(Constants.MAIN_INDEX_ID, masterIndexFile)
        this.datFile = DatFile(datFile)
    }

    override fun init(store: Store) {
        (0 until masterIdxFile.validIndexCount()).forEach {
            val referenceTable = masterIdxFile.loadReferenceTable(it)
            if (referenceTable.sector > 0) {
                referenceTables.add(referenceTable)
                idxFiles.add(getIdxFile(it))
                store.addGroup(loadGroup(it))
            }
        }
        logger.debug { "Loaded ${idxFiles.size} indices." }
    }

    override fun loadGroup(id: Int): Js5Group {
        val table = referenceTables.find { it.id == id }!!
        val groupData = datFile.readReferenceTable(masterIdxFile.id(), table)
        val whirlpool = ByteBuffer.wrap(groupData).whirlpool()
        return table.loadGroup(id, whirlpool, groupData)
    }

    override fun loadFile(group: Js5Group, fileName: String): Js5File? {
        val file = group.getFile(fileName)
        file?.load(datFile, idxFiles.find { it.id() == file.groupId }!!)
        return file
    }

    override fun loadFile(group: Js5Group, fileId: Int): Js5File? {
        val file = group.getFile(fileId)
        file?.load(datFile, idxFiles.find { it.id() == file.groupId }!!)
        return file
    }

    override fun loadEntry(group: Js5Group, fileId: Int, entryId: Int): Js5FileEntry {
        val js5File = loadFile(group, fileId)
        js5File?.loadFileEntriesData(entryId)
        return js5File?.entries?.find { it.entryId == entryId } ?: Js5FileEntry(fileId, entryId, -1, byteArrayOf(0))
    }

    override fun loadReferenceTable(group: Js5Group, fileId: Int): ByteArray {
        val table = getIdxFile(255).loadReferenceTable(28)
        return datFile.readReferenceTable(255, table)
    }

    private fun getIdxFile(id: Int): IdxFile {
        val cachedIndexFile = idxFiles.find { it.id() == id }

        if (cachedIndexFile != null) return cachedIndexFile

        val file = File("$directory/${Constants.MAIN_FILE_IDX}${id}")

        if (file.exists().not()) {
            throw FileNotFoundException("Missing ${Constants.MAIN_FILE_IDX} in directory $directory")
        }

        return IdxFile(id, file)
    }

    override fun close() {
        masterIdxFile.close()
        datFile.close()
        idxFiles.forEach { it.close() }
    }

    override fun flush() {
        TODO("Not yet implemented")
    }
}