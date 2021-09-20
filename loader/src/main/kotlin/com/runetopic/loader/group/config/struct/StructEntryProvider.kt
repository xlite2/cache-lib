package com.runetopic.loader.group.config.struct

import com.runetopic.cache.store.Store
import com.runetopic.loader.IEntryProvider


/**
 * @author Jordan Abraham
 */
class StructEntryProvider : IEntryProvider<StructEntryType> {

    private val builder = StructEntryBuilder()

    override fun load(store: Store) {
        builder.build(store)
    }

    override fun lookup(id: Int): StructEntryType {
        return builder.structTypes.elementAt(id)
    }

    override fun size(): Int {
        return builder.structTypes.size
    }

    override fun collect(): Set<StructEntryType> {
        return builder.structTypes
    }
}