package com.vloginova.midx.util

import com.vloginova.midx.alphabet
import com.vloginova.midx.createTempFileWithText
import com.vloginova.midx.toSet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.random.Random

internal class TrigramUtilsTest {

    companion object {
        private const val palindrome = "I did did I"
        private val palindromeTrigrams = mapOf(
            "I d" to 4792420,
            " di" to 2122857,
            "did" to 6580580,
            "id " to 6906912,
            "d d" to 6561892,
            "d I" to 6561865
        )

        @Suppress("unused")
        @JvmStatic
        private fun dataProviderForCheckNumberOfTrigramsTest(): Stream<Pair<String, Int>> {
            return Stream.of(
                Pair("a", 0),
                Pair("ab", 0),
                Pair("aba", 1),
                Pair("abcdefgki", 7),
                Pair("ababababa", 2),
                Pair("\r\n\r\n\r\n", 1),
                Pair("\r\n\r\n\r", 1),
                Pair("\r\n\r\r", 1),
                Pair("\r\r\r", 1),
                Pair("\n\n\n", 1),
                Pair("\r\n\r\n", 0)
            )
        }

        @Suppress("unused")
        @JvmStatic
        private fun dataProviderForMultilineInputsTest(): Stream<String> {
            return Stream.of(
                "\r\n\r\n\r\n",
                "\r\n\r\nABCD\r\nEFG",
                "\r\r\r\nABCD\r\nEFG",
                "\r\r\n\nABCD\r\nEFG",
                "ABCD\r\n\r\n\r\nEFG",
                "ABCDE\r\n\r\n\r\nFGH",
                "ABCDE\r\r\r\n\r\nFGH",
                "ABCDE\r\r\r\r\r\rFGH",
                "ABCDE\n\n\n\n\n\nFGH"
            )
        }
    }

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
    @MethodSource("dataProviderForCheckNumberOfTrigramsTest")
    fun `Check that each input text has expected number of trigrams`(testData: Pair<String, Int>) {
        val text = testData.first
        val expectedNumberOfTrigrams = testData.second
        val trigramSet = createTrigramSet(text)
        assertEquals(expectedNumberOfTrigrams, trigramSet.size, "Unexpected number of trigrams for $text")
    }

    @Test
    fun `Check that for empty string createTrigramSet() created empty trigram set`() {
        val trigramSet = createTrigramSet("")
        assertTrue(trigramSet.isEmpty(), "Non-empty set for empty input")
    }

    @ParameterizedTest
    @MethodSource("dataProviderForCheckNumberOfTrigramsTest")
    fun `Check that each input file has expected number of trigrams`(testData: Pair<String, Int>) {
        val text = testData.first
        val expectedNumberOfTrigrams = testData.second
        val tempFile = createTempFileWithText(text)
        val trigramSet = createTrigramSet(tempFile, bufferSize = 6)
        assertEquals(
            expectedNumberOfTrigrams,
            trigramSet.size,
            "Unexpected number of trigrams for file with content $text"
        )
    }

    @Test
    fun `Check that for empty file createTrigramSet() created empty trigram set`() {
        val tempFile = createTempFileWithText("")
        val trigramSet = createTrigramSet(tempFile)
        assertTrue(trigramSet.isEmpty(), "Non-empty set for empty file")
    }

    @Test
    fun `Check that for to small buffer size createTrigramSet() throws exception`() {
        val tempFile = createTempFileWithText("")
        assertThrows<IllegalArgumentException> { createTrigramSet(tempFile, bufferSize = 2) }
    }

    @ParameterizedTest
    @MethodSource("dataProviderForMultilineInputsTest")
    fun `Check createTrigramSet(String) for multiline inputs`(input: String) {
        val trigramsForInputWithLfOnly = createTrigramSet(input.replaceFileSeparatorsWithLf()).toSet()
        val trigramsForInput = createTrigramSet(input).toSet()
        assertEquals(trigramsForInputWithLfOnly, trigramsForInput)
    }

    @ParameterizedTest
    @MethodSource("dataProviderForMultilineInputsTest")
    fun `Check createTrigramSet(File) for multiline inputs`(input: String) {
        val tempFile = createTempFileWithText(input)
        val trigramsForInputWithLfOnly = createTrigramSet(tempFile, bufferSize = 6).toSet()
        val trigramsForInput = createTrigramSet(input.replaceFileSeparatorsWithLf()).toSet()
        assertEquals(trigramsForInput, trigramsForInputWithLfOnly)
    }

    @Test
    fun `Check that createTrigramSet() behavior with default buffer size is the same as of string method version`() {
        val text = (1..10 * 1024)
            .map { Random.nextInt(0, alphabet.size) }
            .map(alphabet::get)
            .joinToString("")
        val tempFile = createTempFileWithText(text)
        val trigramsFromFile = createTrigramSet(tempFile).toSet()
        val trigramsFromString = createTrigramSet(text).toSet()
        assertEquals(trigramsFromString, trigramsFromFile)
    }

    private fun assertEquals(expectedSet: Set<Int>, actualSet: Set<Int>) {
        assertEquals(expectedSet.size, actualSet.size, "Unexpected set size")
        assertEquals(expectedSet.size, actualSet.intersect(actualSet).size, "Unexpected set content")
    }
}
