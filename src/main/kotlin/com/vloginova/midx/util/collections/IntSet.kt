package com.vloginova.midx.util.collections

/**
 * IntSet is an optimized set for Int values supporting only add operation.
 */
class IntSet(initialCapacity: Int = 1 shl 8) {
    private var array: IntArray
    private var containsZero = false
    private var mask: Int

    var size = 0
        private set

    init {
        array = IntArray(Integer.highestOneBit(initialCapacity * 3 / 2) shl 1)
        mask = array.size - 1
    }

    fun add(value: Int) {
        if (value == 0) {
            if (!containsZero) {
                containsZero = true
                size++
            }
            return
        }

        if (size >= 2 * array.size / 3) {
            resize()
        }

        var i = intHashCode(value) and mask
        while (array[i] != 0 && array[i] != value) {
            i++
            if (i == array.size) {
                i = 0
            }
        }

        if (array[i] == 0) {
            array[i] = value
            size++
        }
    }

    fun isEmpty(): Boolean = size == 0

    operator fun iterator(): IntIterator = IntSetIterator()

    fun first() = iterator().nextInt()

    private fun resize() {
        val oldArray = array
        array = IntArray(array.size shl 1)
        mask = array.size - 1
        size = if (containsZero) 1 else 0

        for (value in oldArray) {
            if (value != 0) {
                add(value)
            }
        }
    }

    private inner class IntSetIterator : IntIterator() {
        private var restElements = size
        private var index = 0

        override fun nextInt(): Int {
            try {
                while (array[index] == 0) {
                    index++
                }
                restElements--
                return array[index++]
            } catch (e: ArrayIndexOutOfBoundsException) {
                if (containsZero) {
                    restElements--
                    return 0
                }
                throw NoSuchElementException(e.message)
            }
        }

        override fun hasNext(): Boolean = restElements > 0
    }

}
