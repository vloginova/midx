package com.vloginova.midx.collections

import com.vloginova.midx.generateIntWithHashcodeFor
import com.vloginova.midx.getRandomInt
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class IntKeyMapTest {
    @Test
    fun `Check IntKeyMap correctly overrides value for existing key`() {
        val values = listOf(1 to "1", 1 to "2")
        val intKeyMap = values.toIntKeyMap(1)
        intKeyMap.checkContainsExactly(values.toMap())
    }

    @Test
    fun `Check IntKeyMap correctly processes collision`() {
        val values = ArrayList<Pair<Int, String>>(4)
        values.addAll(generateIntWithHashcodeFor(8, 7).take(2).map { Pair(it, it.toString()) })
        values.addAll(generateIntWithHashcodeFor(8, 0).take(2).map { Pair(it, it.toString()) })
        val intKeyMap = values.toIntKeyMap(7)
        intKeyMap.checkContainsExactly(values.toMap())
    }

    @Test
    fun `Check IntKeyMap iterator throws exception when trying to access non-existing element`() {
        val intKeyMap = IntKeyMap<Int>()
        assertThrows<NoSuchElementException> { intKeyMap.iterator().next() }
    }

    @Test
    fun `Check IntKeyMap computeIfAbsent when value with same key exists`() {
        val intKeyMap = IntKeyMap<Int>()
        var computed = false

        intKeyMap.put(1, 2)
        intKeyMap.computeIfAbsent(1) {
            computed = true
            return@computeIfAbsent 3
        }

        assertEquals(2, intKeyMap[1], "Value for key 1 was overridden")
        assertEquals(false, computed, "Value was computed, though value for key 1 is present")
    }

    @Test
    fun `Check IntKeyMap computeIfAbsent when value with same key is absent`() {
        val intKeyMap = IntKeyMap<Int>()
        var computed = false

        intKeyMap.put(1, 2)
        intKeyMap.computeIfAbsent(2) {
            computed = true
            return@computeIfAbsent 3
        }

        assertEquals(2, intKeyMap[1], "Value for key 1 was overridden")
        assertEquals(true, computed, "Value wasn't computed, though value for key 2 wasn't present")
        assertEquals(3, intKeyMap[2], "Unexpected value for key 2")
    }

    @ParameterizedTest
    @CsvSource(
        "1, 5",
        "10000, 100",
        "10000, 10000000"
    )
    fun `Compare IntKeyMap and library Set behaviour on random values`(
        randomValuesCount: Int,
        randomValuesNumber: Int
    ) {
        val randomValues = List(randomValuesCount) {
            Pair(getRandomInt(randomValuesNumber), getRandomInt(randomValuesNumber).toString())
        }
        val intKeyMap = randomValues.toIntKeyMap(4)
        intKeyMap.checkContainsExactly(randomValues.toMap())
    }

    private fun IntKeyMap<String>.checkContainsExactly(map: Map<Int, String>) {
        assertEquals(map.size, size, "Map size is unexpected")
        Assertions.assertAll("Expected value is missing in the result map", map { entry ->
            Executable {
                assertEquals(entry.value, this[entry.key])
            }
        })
    }

    private fun Iterable<Pair<Int, String>>.toIntKeyMap(initialCapacity: Int): IntKeyMap<String> {
        val intKeyMap = IntKeyMap<String>(initialCapacity)
        for (pair in this) {
            intKeyMap.put(pair.first, pair.second)
        }
        return intKeyMap
    }

}