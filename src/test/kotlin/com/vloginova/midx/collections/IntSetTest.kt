package com.vloginova.midx.collections

import com.vloginova.midx.generateIntWithHashcodeFor
import com.vloginova.midx.getRandomInt
import com.vloginova.midx.toSet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

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
        assertFalse(intSet.isEmpty(), "IntSet.isEmpty() returns true when non-empty")
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
            getRandomInt(randomValuesNumber)
        }
        val intSet = randomValues.toIntSet(4)
        intSet.checkContainsExactly(randomValues)
    }

    private fun IntSet.checkContainsExactly(values: List<Int>) {
        val set = values.toSet()
        assertEquals(set.size, size, "Unexpected set size")
        assertEquals(set.size, toSet().intersect(set).size, "Unexpected set content")
    }

    private fun Iterable<Int>.toIntSet(initialCapacity: Int): IntSet {
        val intSet = IntSet(initialCapacity)
        for (value in this) {
            intSet.add(value)
        }
        return intSet
    }

}