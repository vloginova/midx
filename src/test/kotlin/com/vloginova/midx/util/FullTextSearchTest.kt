package com.vloginova.midx.util

import com.vloginova.midx.api.SearchResult
import com.vloginova.midx.assertCollectionEquals
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

class FileUtilsSearchMethodsTest {
    companion object {
        private const val testFilePath = "/simpleTestFiles/text2.txt"
        val file = File(FileUtilsSearchMethodsTest::class.java.getResource(testFilePath).file)

        @Suppress("unused")
        @JvmStatic
        fun testDataProvider(): Stream<Triple<String, Boolean, Collection<SearchResult>>> {
            return Stream.of(
                Triple(
                    "Autumn is", false, listOf(
                        SearchResult(file, "Autumn is over the long leaves that love us,", 1, 0, 9)
                    )
                ),
                Triple(
                    "autumn is", true, listOf(
                        SearchResult(file, "Autumn is over the long leaves that love us,", 1, 0, 9)
                    )
                ),
                Triple(
                    "autumn is", false, emptyList()
                ),
                Triple(
                    "love us,", false, listOf(
                        SearchResult(
                            file,
                            "Autumn is over the long leaves that love us,", 1, 36, 44
                        )
                    )
                ),
                Triple(
                    "r the long l", false, listOf(
                        SearchResult(
                            file,
                            "Autumn is over the long leaves that love us,", 1, 13, 25
                        )
                    )
                ),
                Triple(
                    "us", false, listOf(
                        SearchResult(
                            file,
                            "Autumn is over the long leaves that love us,", 1, 41, 43
                        ),
                        SearchResult(file, "Yellow the leaves of the rowan above us,", 3, 37, 39)
                    )
                ),
                Triple(
                    "sheaves;\nYellow", false, listOf(
                        SearchResult(
                            file, "And over the mice in the barley sheaves;\n" +
                                    "Yellow the leaves of the rowan above us,", 2, 32, 47
                        )
                    )
                ),
                Triple(
                    "us,\nAnd", false, listOf(
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
                Triple(
                    "long leaves that love us,\n" +
                            "And over the mice in the barley sheaves;\n" +
                            "Yellow the leaves of",
                    false, listOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;\n" +
                                    "Yellow the leaves of the rowan above us,", 1, 19, 106
                        )
                    )
                ),
                Triple(
                    "love us,\n", false, listOf(
                        SearchResult(
                            file,
                            "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;", 1, 36, 45
                        )
                    )
                ),
                Triple(
                    "love us,\n\n", false, emptyList()
                ),
                Triple(
                    "love us,\r\r", false, emptyList()
                ),
                Triple(
                    "no match", false, emptyList()
                ),
                Triple(
                    "no\nmatch", false, emptyList()
                ),
                Triple(
                    "", false, emptyList()
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    fun testSearchResult(testData: Triple<String, Boolean, Collection<SearchResult>>) {
        val matches = runBlocking { file.searchFulltext(testData.first, testData.second).toList() }
        assertCollectionEquals(testData.third, matches)
    }

}