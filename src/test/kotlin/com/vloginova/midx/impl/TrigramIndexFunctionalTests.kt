package com.vloginova.midx.impl

import com.vloginova.midx.api.Index
import com.vloginova.midx.api.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

@ExperimentalCoroutinesApi
internal class TrigramIndexTest {
    companion object {
        private const val testFilesPath = "/simpleTestFiles"
        private val file = File(TrigramIndexTest::class.java.getResource(testFilesPath).file)
        val index: Index = runBlocking { buildIndexAsync(file).await() }

        @Suppress("unused")
        @JvmStatic
        fun testDataProvider(): Stream<Pair<String, Array<SearchResult>>> {
            return Stream.of(
                Pair(
                    "r of the", arrayOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            7,
                            15
                        )
                    )
                ),
                Pair(
                    "ani", arrayOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            17,
                            20
                        )
                    )
                ),
                Pair(
                    "The hour", arrayOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            0,
                            8
                        )
                    )
                ),
                Pair(
                    "of", arrayOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            9,
                            11
                        ),
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            23,
                            25
                        ),
                        SearchResult(
                            File("/simpleTestFiles/text2.txt"),
                            "Yellow the leaves of the rowan above us,",
                            18,
                            20
                        ),
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "Let us part, ere the season of passion forget us,",
                            28,
                            30
                        )
                    )
                ),
                Pair(
                    "круг,\nНо",
                    arrayOf(
                        SearchResult(
                            File("/simpleTestFiles/russian/text1.txt"),
                            "Но если сон смыкает сладкий круг,\nНо если пью проклятое вино, –",
                            28,
                            36
                        )
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
        val matches = collectMatches(index, testData.first, testFilesPath)
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
}

@ExperimentalCoroutinesApi
class SmallFilesTrigramIndexTest {

    @Test
    fun `Search on empty file test`() {
        val tempFile = createTempFileInDirectoryWithContent("")
        val index = runBlocking { buildIndexAsync(tempFile).await() }
        val matches = collectMatches(index, "abcd", "/")
        assertTrue(matches.isEmpty(), "Search result on empty file is not empty")
    }

    @Test
    fun `Search on short file test`() {
        val tempFile = createTempFileInDirectoryWithContent("ab")
        val index = runBlocking { buildIndexAsync(tempFile).await() }
        val matches = collectMatches(index, "a", "/")

        assertEquals(1, matches.size, "Unexpected number of matches")

        val searchResult = matches.first()
        val expectedSearchResult = SearchResult(searchResult.file, "ab", 0, 1)
        assertEquals(expectedSearchResult, searchResult, "Unexpected search result")
    }

    private fun createTempFileInDirectoryWithContent(content: String): File {
        val tempDirectory = createTempDir()
        tempDirectory.deleteOnExit()
        val tempFile = createTempFile(directory = tempDirectory)
        tempFile.writeText(content)
        tempFile.deleteOnExit()
        return tempFile
    }

}

private fun collectMatches(index: Index, searchText: String, testFilesPath: String): Collection<SearchResult> {
    val matches = ArrayList<SearchResult>()
    index.search(searchText) { (file, line, startIdx, endIdx) ->
        val simplifiedFileName = file.path.replaceFirst(Regex(".*$testFilesPath"), testFilesPath)
        matches.add(SearchResult(File(simplifiedFileName), line, startIdx, endIdx))
    }
    return matches
}
