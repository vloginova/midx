package com.vloginova.midx.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.random.Random

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
                assertEquals(it.value, createTrigramSet(it.key).first(), "Trigram for ${it.key} has unexpected value")
            }
        })
    }

    @Test
    fun `Check that expected trigram set is created for palindrome`() {
        val trigramSet = createTrigramSet(palindrome).toSet()
        assertAll(trigramSet.map {
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
        assertEquals(createTrigramSet("aMN").first(), createTrigramSet("aIю").first())
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
        val trigramSet = createTrigramSet(text)
        assertEquals(expectedNumberOfTrigrams, trigramSet.size, "Unexpected number of trigrams for $text")
    }

    @ParameterizedTest
    @CsvSource(
        "a, 10, 0",
        "ab, 10, 0",
        "aba, 10, 1",
        "abcdefgki, 3, 7",
        "ababababa, 10, 2"
    )
    fun `Check that each input file has expected number of trigrams`(
        text: String,
        bufferSize: Int,
        expectedNumberOfTrigrams: Int
    ) {
        val tempFile = createTempFile()
        tempFile.writeText(text)
        val trigramSet = createTrigramSet(tempFile, bufferSize)
        assertEquals(
            expectedNumberOfTrigrams,
            trigramSet.size,
            "Unexpected number of trigrams for file with content $text"
        )
    }

    @Test
    fun `Check that for to small buffer size createTrigramSet() throws exception`() {
        val tempFile = createTempFile()
        assertThrows<IllegalArgumentException> { createTrigramSet(tempFile, 2) }
    }

    @Test
    fun `Check that createTrigramSet() behavior with default buffer size is the same as of string method version`() {
        val alphabet = "ABCDabcdАБВГабвг{}(); "
        val text = (1..10 * 1024)
            .map { Random.nextInt(0, alphabet.length) }
            .map(alphabet::get)
            .joinToString("");
        val tempFile = createTempFile()
        tempFile.writeText(text)
        val trigramsFromFile = createTrigramSet(tempFile).toSet()
        val trigramsFromString = createTrigramSet(text).toSet()
        assertEquals(
            trigramsFromString.size,
            trigramsFromFile.size,
            "String and file versions of createTrigramSet() produced sets with different size"
        )
        assertEquals(
            trigramsFromString.size,
            trigramsFromFile.intersect(trigramsFromFile).size,
            "String and file versions of createTrigramSet() produced sets with different content"
        )
    }
}