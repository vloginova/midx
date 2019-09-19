package com.vloginova.midx.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class TrigramUtilsTest {
    private val palindrome = "I did did I"
    private val palindromeTrigrams = mapOf(
        "I d" to 4792420,
        " di" to 2122857,
        "did" to 6580580,
        "id " to 6906912,
        "d d" to 6561892,
        "d I" to 6561865
    )

    @Test
    fun `Check that each 3-char text key from palindromeTrigrams has expected trigram value`() {
        assertAll(palindromeTrigrams.map {
            Executable {
                assertEquals(it.value, it.key.toTrigramSet().first(), "Trigram for ${it.key} has unexpected value")
            }
        })
    }

    @Test
    fun `Check that expected trigram set is created for palindrome`() {
        val trigramSet = palindrome.toTrigramSet()
        assertAll(trigramSet.toSet().map {
            Executable {
                assertTrue(palindromeTrigrams.containsValue(it), "Result has unexpected trigram $it")
            }
        })
        assertEquals(
            palindromeTrigrams.size,
            trigramSet.size,
            "Expected number of trigrams differs from the actual result"
        )
    }

    @Test
    fun `Check for expected collisions`() {
        assertEquals("aMN".toTrigramSet().first(), "aIю".toTrigramSet().first())
    }

    @ParameterizedTest
    @CsvSource(
        "a, 0",
        "ab, 0",
        "aba, 1",
        "abcdefgki, 7",
        "ababababa, 2"
    )
    fun `Check that each input text has expected number of trigrams`(text: String, expectedNumberOfTrigrams: Int) {
        val trigramSet = text.toTrigramSet()
        assertEquals(expectedNumberOfTrigrams, trigramSet.size, "Unexpected number of trigrams for $text")
    }
}