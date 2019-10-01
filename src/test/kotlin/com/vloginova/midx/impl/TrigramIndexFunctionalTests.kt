package com.vloginova.midx.impl

import com.vloginova.midx.api.IOExceptionHandlers.ABORT
import com.vloginova.midx.api.SearchResult
import com.vloginova.midx.assertCollectionEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream

@ExperimentalCoroutinesApi
internal class TrigramIndexTest {
    companion object {
        private const val testFilesPath = "/simpleTestFiles"
        private val file = File(TrigramIndexTest::class.java.getResource(testFilesPath).file)
        val index: TrigramIndex = runBlocking { buildIndex(listOf(file)) }

        @Suppress("unused")
        @JvmStatic
        fun testDataProvider(): Stream<Triple<String, Boolean, Collection<SearchResult>>> {
            return Stream.of(
                Triple(
                    "r of the", false, listOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            1,
                            7,
                            15
                        )
                    )
                ),
                Triple(
                    "r OF the", true, listOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            1,
                            7,
                            15
                        )
                    )
                ),
                Triple(
                    "r OF the", false, emptyList()
                ),
                Triple(
                    "ani", false, listOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            1,
                            17,
                            20
                        )
                    )
                ),
                Triple(
                    "The hour", false, listOf(
                        SearchResult(
                            File("/simpleTestFiles/text1.txt"),
                            "The hour of the waning of love has beset us,",
                            1,
                            0,
                            8
                        )
                    )
                ),
                Triple(
                    "of", false, listOf(
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
                Triple(
                    "круг,\nНо", false, listOf(
                        SearchResult(
                            File("/simpleTestFiles/russian/text1.txt"),
                            "Но если сон смыкает сладкий круг,\nНо если пью проклятое вино, –",
                            4,
                            28,
                            36
                        )
                    )
                ),
                Triple(
                    "КРУГ,\nно", true, listOf(
                        SearchResult(
                            File("/simpleTestFiles/russian/text1.txt"),
                            "Но если сон смыкает сладкий круг,\nНо если пью проклятое вино, –",
                            4,
                            28,
                            36
                        )
                    )
                ),
                Triple(
                    "КРУГ,\nно", false, emptyList()
                ),
                Triple(
                    "no match", false, emptyList()
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
        val expected = testData.third
        val actual = collectMatches(index, testData.first, testData.second, testFilesPath)
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
        val index = runBlocking { buildIndex(listOf(tempFile)) }
        val matches = collectMatches(index, "abcd", false, "/")
        assertTrue(matches.isEmpty(), "Search result on empty file is not empty")
    }

    @Test
    fun `Search on short file test`() {
        tempFile.writeText("ab")
        val index = runBlocking { buildIndex(listOf(tempFile)) }
        val matches = collectMatches(index, "a", false, "/")

        assertEquals(1, matches.size, "Unexpected number of matches")

        val searchResult = matches.first()
        val expectedSearchResult = SearchResult(searchResult.file, "ab", 1, 0, 1)
        assertEquals(expectedSearchResult, searchResult, "Unexpected search result")
    }

}

class IOExceptionHandlerTest {

    @Test
    fun `Check built is successful without exception when input files are cleaned up when ignoring exception`() {
        val counter = AtomicInteger()
        runBlocking {
            buildIndex(listOf(File("DO/NOT/EXIST")), { _, _ ->
                counter.incrementAndGet()
            })
        }
        assertTrue(counter.get() > 0, "Some files should have been failed to process")
    }

    @Test
    fun `Check build is cancelled when input files are cleaned up for ABORT_DO_NOTHING`() {
        runBlocking {
            assertThrows<IOException> { runBlocking { buildIndex(listOf(File("DO/NOT/EXIST")), ABORT) } }
        }
    }

}

private fun collectMatches(
    index: TrigramIndex,
    searchText: String,
    ignoreCase: Boolean,
    testFilesPath: String
): Collection<SearchResult> {
    return runBlocking {
        index.search(searchText, ignoreCase)
            .map { (file, line, lineNumber, startIdx, endIdx) ->
                val simplifiedFileName = file.path.replaceFirst(Regex(".*$testFilesPath"), testFilesPath)
                SearchResult(File(simplifiedFileName), line, lineNumber, startIdx, endIdx)
            }.toList()
    }
}
