package com.vloginova.midx.impl

import com.vloginova.midx.api.SearchResult
import com.vloginova.midx.assertCollectionEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

@ExperimentalCoroutinesApi
internal class TrigramIndexTest {
    companion object {
        private const val testFilesPath = "/simpleTestFiles"
        private val file = File(TrigramIndexTest::class.java.getResource(testFilesPath).file)
        val index: TrigramIndex = runBlocking { buildIndexAsync(file).await() }

        @Suppress("unused")
        @JvmStatic
        fun testDataProvider(): Stream<Pair<String, Collection<SearchResult>>> {
            return Stream.of(
                Pair(
                    "r of the", listOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            1,
                            7,
                            15
                        )
                    )
                ),
                Pair(
                    "ani", listOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            1,
                            17,
                            20
                        )
                    )
                ),
                Pair(
                    "The hour", listOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            1,
                            0,
                            8
                        )
                    )
                ),
                Pair(
                    "of", listOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            1,
                            9,
                            11
                        ),
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            1,
                            23,
                            25
                        ),
                        SearchResult(
                            File("/simpleTestFiles/text2.txt"),
                            "Yellow the leaves of the rowan above us,",
                            3,
                            18,
                            20
                        ),
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "Let us part, ere the season of passion forget us,",
                            3,
                            28,
                            30
                        )
                    )
                ),
                Pair(
                    "круг,\nНо",
                    listOf(
                        SearchResult(
                            File("/simpleTestFiles/russian/text1.txt"),
                            "Но если сон смыкает сладкий круг,\nНо если пью проклятое вино, –",
                            4,
                            28,
                            36
                        )
                    )
                ),
                Pair(
                    "no match", emptyList()
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
        val expected = testData.second
        val actual = collectMatches(index, testData.first, testFilesPath)
        assertCollectionEquals(expected, actual)
    }
}

@ExperimentalCoroutinesApi
class SmallFilesTrigramIndexTest {

    private lateinit var rootDirectory: File
    private lateinit var tempFile: File

    @BeforeEach
    fun prepareInputData() {
        rootDirectory = createTempDir()
        tempFile = createTempFile(directory = rootDirectory)
    }

    @AfterEach
    fun deleteTestFile() {
        rootDirectory.deleteRecursively()
    }

    @Test
    fun `Search on empty file test`() {
        tempFile.writeText("")
        val index = runBlocking { buildIndexAsync(tempFile).await() }
        val matches = collectMatches(index, "abcd", "/")
        assertTrue(matches.isEmpty(), "Search result on empty file is not empty")
    }

    @Test
    fun `Search on short file test`() {
        tempFile.writeText("ab")
        val index = runBlocking { buildIndexAsync(tempFile).await() }
        val matches = collectMatches(index, "a", "/")

        assertEquals(1, matches.size, "Unexpected number of matches")

        val searchResult = matches.first()
        val expectedSearchResult = SearchResult(searchResult.file, "ab", 1, 0, 1)
        assertEquals(expectedSearchResult, searchResult, "Unexpected search result")
    }

}

private fun collectMatches(index: TrigramIndex, searchText: String, testFilesPath: String): Collection<SearchResult> {
    val matches = ArrayList<SearchResult>()
    runBlocking {
        index.searchAsync(text = searchText) { (file, line, lineNumber, startIdx, endIdx) ->
            val simplifiedFileName = file.path.replaceFirst(Regex(".*$testFilesPath"), testFilesPath)
            matches.add(SearchResult(File(simplifiedFileName), line, lineNumber, startIdx, endIdx))
        }.await()
    }
    return matches
}
