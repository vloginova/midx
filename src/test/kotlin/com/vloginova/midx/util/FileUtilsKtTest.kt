package com.vloginova.midx.util

import com.vloginova.midx.api.SearchResult
import com.vloginova.midx.assertCollectionEquals
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
        fun testDataProvider(): Stream<Pair<String, Collection<SearchResult>>> {
            return Stream.of(
                Pair(
                    "Autumn is", listOf(
                        SearchResult(file, "Autumn is over the long leaves that love us,", 1, 0, 9)
                    )
                ),
                Pair(
                    "love us,", listOf(
                        SearchResult(
                            file,
                            "Autumn is over the long leaves that love us,",
                            1,
                            36,
                            44
                        )
                    )
                ),
                Pair(
                    "r the long l", listOf(
                        SearchResult(
                            file,
                            "Autumn is over the long leaves that love us,",
                            1,
                            13,
                            25
                        )
                    )
                ),
                Pair(
                    "us", listOf(
                        SearchResult(
                            file,
                            "Autumn is over the long leaves that love us,",
                            1,
                            41,
                            43
                        ),
                        SearchResult(file, "Yellow the leaves of the rowan above us,", 3, 37, 39)
                    )
                ),
                Pair(
                    "sheaves;\nYellow", listOf(
                        SearchResult(
                            file, "And over the mice in the barley sheaves;\n" +
                                    "Yellow the leaves of the rowan above us,", 2, 32, 47
                        )
                    )
                ),
                Pair(
                    "us,\nAnd", listOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;", 1, 41, 48
                        ),
                        SearchResult(
                            file, "Yellow the leaves of the rowan above us,\n" +
                                    "And yellow the wet wild-strawberry leaves.", 3, 37, 44
                        )
                    )
                ),
                Pair(
                    "love us,\n", listOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;", 1, 36, 45
                        )
                    )
                ),
                Pair(
                    "love us,\r", listOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;", 1, 36, 45
                        )
                    )
                ),
                Pair(
                    "love us,\r\n", listOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;", 1, 36, 45
                        )
                    )
                ),
                Pair(
                    "love us,\n\n", emptyList()
                ),
                Pair(
                    "love us,\r\r", emptyList()
                ),
                Pair(
                    "no match", emptyList()
                ),
                Pair(
                    "no\nmatch", emptyList()
                ),
                Pair(
                    "", emptyList()
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    fun testSearchResult(testData: Pair<String, Collection<SearchResult>>) {
        val matches = file.fullTextSearch(testData.first)
        assertCollectionEquals(testData.second, matches)
    }

}