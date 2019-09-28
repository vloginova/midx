package com.vloginova.midx.impl

import com.vloginova.midx.alphabet
import com.vloginova.midx.assertCollectionEquals
import com.vloginova.midx.util.replaceFileSeparatorsWithLf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.random.Random

private const val palindrome = "I did did I"
private val palindromeTrigrams = mapOf(
    "I d" to 6889572,
    " di" to 2122857,
    "did" to 6580580,
    "id " to 6906912,
    "d d" to 6561892,
    "d I" to 6561897
)

open class TrigramSetTest {

    companion object {
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

}

class TrigramSetFromStringTest : TrigramSetTest() {

    @Test
    fun `Check that each 3-char text key from palindromeTrigrams has expected trigram value`() {
        assertAll(palindromeTrigrams.map {
            Executable {
                assertEquals(it.value, TrigramSet.from(it.key).first(), "Trigram for ${it.key} has unexpected value")
            }
        })
    }

    @Test
    fun `Check that expected trigram set is created for palindrome`() {
        val trigramSet = TrigramSet.from(palindrome).toSet()
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
        assertEquals(TrigramSet.from("IaB").first(), TrigramSet.from("iAb").first())
    }

    @ParameterizedTest
    @MethodSource("dataProviderForCheckNumberOfTrigramsTest")
    fun `Check that each input text has expected number of trigrams`(testData: Pair<String, Int>) {
        val text = testData.first
        val expectedNumberOfTrigrams = testData.second
        val trigramSet = TrigramSet.from(text)
        assertEquals(expectedNumberOfTrigrams, trigramSet.size, "Unexpected number of trigrams for $text")
    }

    @Test
    fun `Check that for empty string TrigramSet from() created empty trigram set`() {
        val trigramSet = TrigramSet.from("")
        assertTrue(trigramSet.isEmpty(), "Non-empty set for empty input")
    }

    @ParameterizedTest
    @MethodSource("dataProviderForMultilineInputsTest")
    fun `Check TrigramSet from(String) for multiline inputs`(input: String) {
        val trigramsForInputWithLfOnly = TrigramSet.from(input.replaceFileSeparatorsWithLf()).toSet()
        val trigramsForInput = TrigramSet.from(input).toSet()
        assertCollectionEquals(trigramsForInputWithLfOnly, trigramsForInput)
    }
}

class TrigramSetFromFileTest : TrigramSetTest() {
    private lateinit var file: File

    @BeforeEach
    fun createEmptyTestFile() {
        file = createTempFile()
    }

    @AfterEach
    fun deleteTestFile() {
        file.delete()
    }

    @ParameterizedTest
    @MethodSource("dataProviderForCheckNumberOfTrigramsTest")
    fun `Check that each input file has expected number of trigrams`(testData: Pair<String, Int>) {
        val text = testData.first
        val expectedNumberOfTrigrams = testData.second
        file.writeText(text)
        val trigramSet = TrigramSet.from(file, bufferSize = 6)
        assertEquals(
            expectedNumberOfTrigrams,
            trigramSet.size,
            "Unexpected number of trigrams for file with content $text"
        )
    }

    @Test
    fun `Check that for empty file TrigramSet from() created empty trigram set`() {
        file.writeText("")
        val trigramSet = TrigramSet.from(file)
        assertTrue(trigramSet.isEmpty(), "Non-empty set for empty file")
    }

    @Test
    fun `Check that for to small buffer size TrigramSet from() throws exception`() {
        file.writeText("")
        assertThrows<IllegalArgumentException> { TrigramSet.from(file, bufferSize = 2) }
    }

    @ParameterizedTest
    @MethodSource("dataProviderForMultilineInputsTest")
    fun `Check TrigramSet from(File) for multiline inputs`(input: String) {
        file.writeText(input)
        val trigramsForInputWithLfOnly = TrigramSet.from(file, bufferSize = 6).toSet()
        val trigramsForInput = TrigramSet.from(input.replaceFileSeparatorsWithLf()).toSet()
        assertCollectionEquals(trigramsForInput, trigramsForInputWithLfOnly)
    }

    @Test
    fun `Check that TrigramSet from(File) behavior with default buffer size is the same as of string method version`() {
        val text = (1..10 * 1024)
            .map { Random.nextInt(0, alphabet.size) }
            .map(alphabet::get)
            .joinToString("")
        file.writeText(text)
        val trigramsFromFile = TrigramSet.from(file).toSet()
        val trigramsFromString = TrigramSet.from(text).toSet()
        assertCollectionEquals(trigramsFromString, trigramsFromFile)
    }
}

private fun TrigramSet.toSet(): Set<Int> {
    val set = HashSet<Int>()
    for (value in this) {
        set.add(value)
    }
    return set
}
