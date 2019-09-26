package com.vloginova.midx.util

import com.vloginova.midx.api.SearchResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

internal class FileUtilsKtTest {
    companion object {
        private const val testFilePath = "/simpleTestFiles/text2.txt"
        val file = File(FileUtilsKtTest::class.java.getResource(testFilePath).file)

        init {
            file.deleteOnExit()
        }

        @Suppress("unused")
        @JvmStatic
        fun testDataProvider(): Stream<Pair<String, Array<SearchResult>>> {
            return Stream.of(
                Pair(
                    "Autumn is", arrayOf(
                        SearchResult(file, "Autumn is over the long leaves that love us,", 0, 9)
                    )
                ),
                Pair(
                    "love us,", arrayOf(
                        SearchResult(
                            file,
                            "Autumn is over the long leaves that love us,",
                            36,
                            44
                        )
                    )
                ),
                Pair(
                    "r the long l", arrayOf(
                        SearchResult(
                            file,
                            "Autumn is over the long leaves that love us,",
                            13,
                            25
                        )
                    )
                ),
                Pair(
                    "us", arrayOf(
                        SearchResult(
                            file,
                            "Autumn is over the long leaves that love us,",
                            41,
                            43
                        ),
                        SearchResult(file, "Yellow the leaves of the rowan above us,", 37, 39)
                    )
                ),
                Pair(
                    "sheaves;\nYellow", arrayOf(
                        SearchResult(
                            file, "And over the mice in the barley sheaves;\n" +
                                    "Yellow the leaves of the rowan above us,", 32, 47
                        )
                    )
                ),
                Pair(
                    "us,\nAnd", arrayOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;", 41, 48
                        ),
                        SearchResult(
                            file, "Yellow the leaves of the rowan above us,\n" +
                                    "And yellow the wet wild-strawberry leaves.", 37, 44
                        )
                    )
                ),
                Pair(
                    "love us,\n", arrayOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;", 36, 45
                        )
                    )
                ),
                Pair(
                    "love us,\r", arrayOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;", 36, 45
                        )
                    )
                ),
                Pair(
                    "love us,\r\n", arrayOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;", 36, 45
                        )
                    )
                ),
                Pair(
                    "love us,\n\n", emptyArray()
                ),
                Pair(
                    "love us,\r\r", emptyArray()
                ),
                Pair(
                    "no match", emptyArray()
                ),
                Pair(
                    "no\nmatch", emptyArray()
                ),
                Pair(
                    "", emptyArray()
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    fun testSearchResult(testData: Pair<String, Array<SearchResult>>) {
        val matches = collectMatches(testData.first)
        assertAll("Expected match is missed in the actual result", testData.second.map { match ->
            Executable {
                assertTrue(
                    matches.contains(
                        match
                    ),
                    "Match is absent: $match"
                )
            }
        })
        assertEquals(
            testData.second.size,
            matches.size,
            "Expected number of matches differs from the actual result"
        )
    }

    private fun collectMatches(searchText: String): Collection<SearchResult> {
        val matches = ArrayList<SearchResult>()
        file.fullTextSearch(searchText) { matches.add(it) }
        return matches
    }
}