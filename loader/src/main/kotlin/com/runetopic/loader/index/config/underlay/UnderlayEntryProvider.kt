package com.runetopic.loader.index.config.underlay

import com.runetopic.cache.store.storage.js5.Js5Store
import com.runetopic.loader.IEntryProvider

/**
 * @author Jordan Abraham
 */
class UnderlayEntryProvider : IEntryProvider<UnderlayEntryType> {

    private val builder = UnderlayEntryBuilder()

    override fun load(store: Js5Store) {
        builder.build(store)
    }

    override fun lookup(id: Int): UnderlayEntryType {
        return builder.underlays.elementAt(id)
    }

    override fun size(): Int {
        return builder.underlays.size
    }

    override fun collect(): Set<UnderlayEntryType> {
        return builder.underlays
    }
}