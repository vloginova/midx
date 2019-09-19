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
        array = IntArray(Integer.highestOneBit(initialCapacity) shl 1)
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

        // TODO: optimize load factor
        if (size >= 2 * array.size / 3) {
            resize()
        }

        var i = intHashCode(value)
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

    operator fun iterator(): IntIterator = Iterator()

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

    /**
     * Hash function is taken here:
     * https://stackoverflow.com/questions/664014/what-integer-hash-function-are-good-that-accepts-an-integer-hash-key
     * It gives a pretty good distribution and respectively low number of collisions. Compared to Intellij Idea version,
     * it has approximately 4-5 times less collisions without initial capacity optimization, and it gives less
     * degradation when increasing load factor.
     */
    private fun intHashCode(y: Int): Int {
        var x = ((y shr 16) xor y) * 0x45d9f3b
        /* Found no difference in terms collisions count after commenting this */
//        x = ((x shr 16) xor x) * 0x45d9f3b
        x = (x shr 16) xor x
        return x and mask
    }

    private inner class Iterator : IntIterator() {
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
