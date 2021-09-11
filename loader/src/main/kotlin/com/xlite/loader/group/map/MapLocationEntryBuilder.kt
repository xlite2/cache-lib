package com.xlite.loader.group.map

import com.xlite.cache.compression.Compression
import com.xlite.cache.extension.readUnsignedByte
import com.xlite.cache.extension.readUnsignedIntSmartShortCompat
import com.xlite.cache.extension.readUnsignedSmart
import com.xlite.cache.store.Store
import com.xlite.loader.IEntryBuilder
import java.nio.ByteBuffer
import java.util.zip.ZipException

/**
 * @author Tyler Telis
 * @email <xlitersps@gmail.com>
 */
internal class MapLocationEntryBuilder : IEntryBuilder<MapLocationEntryType> {

    lateinit var mapTypes: Set<MapLocationEntryType>

    @OptIn(ExperimentalStdlibApi::class)
    override fun build(store: Store) {
        mapTypes = buildSet {
            store.group(5).use {
                (0 until Short.MAX_VALUE + 1).forEach { regionId ->
                    val regionX: Int = regionId shr 8
                    val regionY: Int = regionId and 0xFF
                    val js5File = store.file(it, "l${regionX}_${regionY}")

                    if (js5File != null) {
                        try {
                            val container = Compression.decompress(js5File.data!!, emptyArray())
                            add(read(ByteBuffer.wrap(container.data), MapLocationEntryType(regionId, regionX, regionY)))
                        } catch (exception: ZipException) {
                            println("Couldn't decompress Region $regionId")
                        }
                    }
                }
            }
        }
    }

    override fun read(buffer: ByteBuffer, type: MapLocationEntryType): MapLocationEntryType {
        var id = -1
        var idOffset: Int

        while (buffer.readUnsignedIntSmartShortCompat().also { idOffset = it } != 0) {
            id += idOffset

            var position = 0
            var positionOffset: Int

            while (buffer.readUnsignedSmart().also { positionOffset = it } != 0) {
                position += positionOffset - 1
                val localY = position and 0x3F
                val localX = position shr 6 and 0x3F
                val height = position shr 12 and 0x3
                val attributes: Int = buffer.readUnsignedByte()
                val locType = attributes shr 2
                val orientation = attributes and 0x3
                type.locations.add(
                    MapLocationEntryType.MapLocation(
                        id,
                        locType,
                        orientation,
                        localX, localY,
                        height
                    )
                )
            }
        }
        return type
    }
}