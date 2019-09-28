package com.vloginova.midx.util

import com.vloginova.midx.api.IOExceptionHandlingStrategy
import com.vloginova.midx.api.IOExceptionHandlingStrategy.Strategy.*
import com.vloginova.midx.api.SearchResult
import com.vloginova.midx.assertCollectionEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.io.IOException
import java.util.stream.Stream

internal class FileUtilsSearchMethodsTest {
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
                            "Autumn is over the long leaves that love us,",
                            1,
                            36,
                            44
                        )
                    )
                ),
                Triple(
                    "r the long l", false, listOf(
                        SearchResult(
                            file,
                            "Autumn is over the long leaves that love us,",
                            1,
                            13,
                            25
                        )
                    )
                ),
                Triple(
                    "us", false, listOf(
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
                    "us,\nand", true, listOf(
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
                    "US,\nand", false, emptyList()
                ),
                Triple(
                    "love us,\n", false, listOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;", 1, 36, 45
                        )
                    )
                ),
                Triple(
                    "love us,\r", false, listOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
                                    "And over the mice in the barley sheaves;", 1, 36, 45
                        )
                    )
                ),
                Triple(
                    "love us,\r\n", false, listOf(
                        SearchResult(
                            file, "Autumn is over the long leaves that love us,\n" +
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
        val matches = file.fullTextSearch(testData.first, testData.second)
        assertCollectionEquals(testData.third, matches)
    }

}

class FileUtilsTryProcessTest {
    private lateinit var file: File

    @BeforeEach
    fun createEmptyTestFile() {
        file = createTempFile()
    }

    @AfterEach
    fun deleteTestFile() {
        file.delete()
    }

    @Test
    fun `Try process returns file content for existing file`() {
        val text = "text"
        file.writeText(text)
        var callbackCallCount = 0
        val bytes = file.tryProcess(IOExceptionHandlingStrategy(IGNORE) { _, _ ->
            callbackCallCount++
        }) {
            file.readBytes()
        }
        assertEquals(0, callbackCallCount, "Unexpected callback call count")
        assertEquals(text, String(bytes ?: ByteArray(0)), "Unexpected file content")
    }

    @Test
    fun `Try process returns null with IGNORE strategy even though recreated`() {
        file.delete()
        var callbackCallCount = 0
        val bytes = file.tryProcess(IOExceptionHandlingStrategy(IGNORE) { _, _ ->
            file.createNewFile()
            callbackCallCount++
        }) {
            file.readBytes()
        }
        assertEquals(1, callbackCallCount, "Unexpected callback call count")
        assertNull(bytes, "Not null was returned for non-existing file")
    }

    @Test
    fun `Try process returns null with RETRY_THEN_IGNORE strategy`() {
        file.delete()
        var callbackCallCount = 0
        val bytes = file.tryProcess(IOExceptionHandlingStrategy(RETRY_THEN_IGNORE) { _, _ -> callbackCallCount++ }) {
            file.readBytes()
        }
        assertEquals(2, callbackCallCount, "Unexpected callback call count")
        assertNull(bytes, "Not null was returned for non-existing file")
    }

    @Test
    fun `Try process reads file with RETRY_THEN_IGNORE strategy when recreated`() {
        file.delete()
        var callbackCallCount = 0
        val bytes = file.tryProcess(IOExceptionHandlingStrategy(RETRY_THEN_IGNORE) { _, _ ->
            file.createNewFile()
            callbackCallCount++
        }) {
            file.readBytes()
        }
        assertEquals(1, callbackCallCount, "Unexpected callback call count")
        assertNotNull(bytes, "Null was returned for existing file")
    }

    @Test
    fun `Try process fails with ABORT strategy even though recreated`() {
        file.delete()
        var callbackCallCount = 0
        assertThrows<IOException> {
            file.tryProcess(IOExceptionHandlingStrategy(ABORT) { _, _ ->
                file.createNewFile()
                callbackCallCount++
            }) {
                file.readBytes()
            }
        }
        assertEquals(1, callbackCallCount, "Unexpected callback call count")
    }

    @Test
    fun `Try process fails with RETRY_THEN_ABORT strategy`() {
        file.delete()
        var callbackCallCount = 0
        assertThrows<IOException> {
            file.tryProcess(IOExceptionHandlingStrategy(RETRY_THEN_ABORT) { _, _ -> callbackCallCount++ }) {
                file.readBytes()
            }
        }
        assertEquals(2, callbackCallCount, "Unexpected callback call count")
    }

    @Test
    fun `Try process reads file with RETRY_THEN_ABORT strategy when recreated`() {
        file.delete()
        var callbackCallCount = 0
        val bytes = file.tryProcess(IOExceptionHandlingStrategy(RETRY_THEN_ABORT) { _, _ ->
            file.createNewFile()
            callbackCallCount++
        }) {
            file.readBytes()
        }
        assertEquals(1, callbackCallCount, "Unexpected callback call count")
        assertNotNull(bytes, "Null was returned for existing file")
    }

}

class FileUtilsMicsTest {

    @Test
    fun `Check walkFiles`() {
        val testDir = "/simpleTestFiles"
        val file = File(FileUtilsMicsTest::class.java.getResource(testDir).file)
        val files = file.walkFiles().toList()
        assertEquals(5, files.count(), "Unexpected number of files in $testDir")
        assertTrue(files.all { it.isFile }, "Non-file is gathered in walkFiles()")
    }

    @Test
    fun `Check hasTextContent() returns true for a text file`() {
        val file = File(FileUtilsMicsTest::class.java.getResource("/simpleTestFiles/text2.txt").file)
        assertTrue(file.hasTextContent(), "Text file was recognized as binary")
    }

    @Test
    fun `Check hasTextContent() returns false for a file with non text MIME type but text content`() {
        val file = File(FileUtilsMicsTest::class.java.getResource("/simpleTestFiles/other/emptyHTML.html").file)
        assertTrue(file.hasTextContent(), "Text file was recognized as binary")
    }

    @Test
    fun `Check hasTextContent() returns false for a binary file`() {
        val file = File(FileUtilsMicsTest::class.java.getResource("/simpleTestFiles/textCompressed.zip").file)
        assertFalse(file.hasTextContent(), "Binary file was recognized as text")
    }

}