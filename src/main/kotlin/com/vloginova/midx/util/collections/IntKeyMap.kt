package com.vloginova.midx.util.collections

/**
 * IntKeyMap is an open-addressing hash map, where key is a primitive Int.
 */
class IntKeyMap<T>(initialCapacity: Int = 1 shl 4) : Iterable<IntKeyMap.Entity<T>> {
    data class Entity<T>(val key: Int, val value: T)

    private var array: Array<Entity<T>?>
    private var mask: Int

    var size = 0
        private set

    init {
        array = arrayOfNulls(Integer.highestOneBit(initialCapacity) shl 1)
        mask = array.size - 1
    }

    fun computeIfAbsent(key: Int, valueProducer: () -> T) : T {
        if (size >= 2 * array.size / 3) resize()

        val i = getInsertionIndex(key)
        if (array[i] == null) {
            array[i] = Entity(key, valueProducer.invoke())
            size++
        }

        return array[i]!!.value
    }

    fun put(key: Int, value: T) {
        if (size >= 2 * array.size / 3) resize()

        val i = getInsertionIndex(key)
        if (array[i] == null) size++
        array[i] = Entity(key, value)
    }

    private fun getInsertionIndex(key: Int): Int {
        var i = intHashCode(key) and mask
        while (array[i] != null && array[i]!!.key != key) {
            i++
            if (i == array.size) {
                i = 0
            }
        }
        return i
    }

    operator fun get(key: Int): T? {
        return array[getInsertionIndex(key)]?.value
    }

    override operator fun iterator(): Iterator<Entity<T>> = IntKeyMapIterator()

    private fun resize() {
        val oldArray = array
        array = arrayOfNulls(array.size shl 1)
        mask = array.size - 1
        size = 0

        for (entity in oldArray) {
            if (entity != null) {
                put(entity.key, entity.value)
            }
        }
    }

    private inner class IntKeyMapIterator : Iterator<Entity<T>> {
        private var restElements = size
        private var index = 0

        override fun next(): Entity<T> {
            try {
                while (array[index] == null) {
                    index++
                }
                restElements--
                return array[index++]!!
            } catch (e: ArrayIndexOutOfBoundsException) {
                throw NoSuchElementException(e.message)
            }
        }

        override fun hasNext(): Boolean = restElements > 0
    }
}
