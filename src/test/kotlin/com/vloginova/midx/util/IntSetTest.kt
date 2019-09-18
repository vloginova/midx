package com.vloginova.midx.util

import com.vloginova.midx.util.collections.IntSet
import com.vloginova.midx.util.collections.intHashCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.stream.Collectors.toSet
import kotlin.random.Random

internal class IntSetTest {
    @Test
    fun `Check IntSet correctly processes with zero value`() {
        val values = listOf(0, 0)
        val intSet = values.toIntSet(1)
        intSet.checkContainsExactly(values)
    }

    @Test
    fun `Check IntSet correctly processes collision`() {
        val values = ArrayList<Int>(4)
        values.addAll(generateIntWithHashcodeFor(8, 7).take(2))
        values.addAll(generateIntWithHashcodeFor(8, 0).take(2))
        val intSet = values.toIntSet(7)
        intSet.checkContainsExactly(values)
    }

    @Test
    fun `Check IntSet first throws an exception when empty`() {
        val intSet = IntSet()
        assertThrows<NoSuchElementException> { intSet.first() }
    }

    @Test
    fun `Check IntSet first returns an element when non-empty`() {
        val intSet = IntSet()
        intSet.add(5)
        assertEquals(5, intSet.first(), "Iterator returns unexpected value")
    }

    @Test
    fun `Check IntSet isEmpty() when empty`() {
        val intSet = IntSet()
        assertTrue(intSet.isEmpty(), "IntSet.isEmpty() returns false when empty")
    }

    @Test
    fun `Check IntSet isEmpty() when non-empty`() {
        val intSet = IntSet()
        intSet.add(5)
        assertEquals(5, intSet.first(), "IntSet.isEmpty() returns true when non-empty")
    }

    @ParameterizedTest
    @CsvSource(
        "1, 5",
        "10000, 100",
        "10000, 10000000"
    )
    fun `Compare IntSet and library Set behaviour on random values`(
        randomValuesCount: Int,
        randomValuesNumber: Int
    ) {
        val randomValues = List(randomValuesCount) {
            Random.nextInt(
                -randomValuesNumber / 2,
                randomValuesNumber / 2 + randomValuesNumber % 2
            )
        }
        val intSet = randomValues.toIntSet(4)
        intSet.checkContainsExactly(randomValues)
    }

    private fun IntSet.checkContainsExactly(values: List<Int>) {
        val set = values.toSet()
        assertEquals(set.size, size, "Size of set is unexpected")
        assertEquals(set.size, toSet().intersect(set).size, "Content of set is unexpected")
    }

    private fun Iterable<Int>.toIntSet(initialCapacity: Int): IntSet {
        val intSet = IntSet(initialCapacity)
        for (value in this) {
            intSet.add(value)
        }
        return intSet
    }

    private fun generateIntWithHashcodeFor(arrayCapacity: Int, expectedIndex: Int): Sequence<Int> {
        return sequence {
            var i = 0

            while (true) {
                val hashCode = intHashCode(i)
                if ((hashCode and (arrayCapacity - 1)) == expectedIndex)
                    yield(i)
                i++
            }
        }
    }

}