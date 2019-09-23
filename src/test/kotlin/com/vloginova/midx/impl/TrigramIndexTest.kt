package com.vloginova.midx.impl

import com.vloginova.midx.api.Index
import com.vloginova.midx.api.SearchResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

internal class TrigramIndexTest {
    companion object {
        private const val testFilesPath = "/simpleTestFiles"
        private val file = File(TrigramIndexTest::class.java.getResource(testFilesPath).file)
        val index: Index = runBlocking { buildIndexAsync(file).await() }

        @JvmStatic
        fun testDataProvider(): Stream<Pair<String, Array<SearchResult>>> {
            return Stream.of(
                Pair(
                    "r of the", arrayOf(
                        SearchResult("/simpleTestFiles/text1.txt", "The hour of the waning of love has beset us,", 7, 15)
                    )
                ),
                Pair(
                    "ani", arrayOf(
                        SearchResult("/simpleTestFiles/text1.txt", "The hour of the waning of love has beset us,", 17, 20)
                    )
                ),
                Pair(
                    "The hour", arrayOf(
                        SearchResult("/simpleTestFiles/text1.txt", "The hour of the waning of love has beset us,", 0, 8)
                    )
                ),
                Pair(
                    "of", arrayOf(
                        SearchResult("/simpleTestFiles/text1.txt", "The hour of the waning of love has beset us,", 9, 11),
                        SearchResult("/simpleTestFiles/text1.txt", "The hour of the waning of love has beset us,", 23, 25),
                        SearchResult("/simpleTestFiles/text2.txt", "Yellow the leaves of the rowan above us,", 18, 20),
                        SearchResult("/simpleTestFiles/text1.txt", "Let us part, ere the season of passion forget us,", 28, 30)
                    )
                ),
                Pair(
                    "no match", emptyArray()
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
        index.search(searchText) { (fileName, line, startIdx, endIdx) ->
            val simplifiedFileName = fileName.replaceFirst(Regex(".*$testFilesPath"), testFilesPath)
            matches.add(SearchResult(simplifiedFileName, line, startIdx, endIdx))
        }
        return matches
    }
}
