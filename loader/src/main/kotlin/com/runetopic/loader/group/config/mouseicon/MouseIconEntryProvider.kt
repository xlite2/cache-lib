package com.runetopic.loader.group.config.mouseicon

import com.runetopic.cache.store.Store
import com.runetopic.loader.IEntryProvider


/**
 * @author Jordan Abraham
 */
class MouseIconEntryProvider : IEntryProvider<MouseIconEntryType> {

    private val builder = MouseIconEntryBuilder()

    override fun load(store: Store) {
        builder.build(store)
    }

    override fun lookup(id: Int): MouseIconEntryType {
        return builder.mouseIconTypes.elementAt(id)
    }

    override fun size(): Int {
        return builder.mouseIconTypes.size
    }

    override fun collect(): Set<MouseIconEntryType> {
        return builder.mouseIconTypes
    }
}