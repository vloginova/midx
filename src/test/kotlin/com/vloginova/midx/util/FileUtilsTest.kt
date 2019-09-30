package com.vloginova.midx.util

import com.vloginova.midx.api.ABORT_DO_NOTHING
import com.vloginova.midx.api.IGNORE_DO_NOTHING
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.io.IOException

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
        val bytes = file.tryProcess(IGNORE_DO_NOTHING) {
            file.readBytes()
        }
        assertEquals(text, String(bytes ?: ByteArray(0)), "Unexpected file content")
    }

    @Test
    fun `Try process returns null with IGNORE_DO_NOTHING`() {
        file.delete()
        val bytes = file.tryProcess(IGNORE_DO_NOTHING) {
            file.readBytes()
        }
        assertNull(bytes, "Not null was returned for non-existing file")
    }

    @Test
    fun `Try process fails with ABORT_DO_NOTHING`() {
        file.delete()
        assertThrows<IOException> {
            file.tryProcess(ABORT_DO_NOTHING) {
                file.readBytes()
            }
        }
    }

}

class FileUtilsMicsTest {

    @Test
    fun `Check walkFiles`() {
        val testDir = "/simpleTestFiles"
        val fileOther = File(FileUtilsMicsTest::class.java.getResource("$testDir/other").file)
        val fileRussian = File(FileUtilsMicsTest::class.java.getResource("$testDir/russian").file)
        val text1 = File(FileUtilsMicsTest::class.java.getResource("$testDir/text1.txt").file)
        val text2 = File(FileUtilsMicsTest::class.java.getResource("$testDir/text2.txt").file)
        val textCompressed = File(FileUtilsMicsTest::class.java.getResource("$testDir/textCompressed.zip").file)
        val files = listOf(fileOther, fileRussian, text1, text2, textCompressed).walkFiles(ABORT_DO_NOTHING).toList()
        assertEquals(5, files.count(), "Unexpected number of files in $testDir")
        assertTrue(files.all { it.isFile }, "Non-file is gathered in walkFiles()")
    }

    @Test
    fun `Check walkFiles throws exception on non-existing path with ABORT_DO_NOTHING`() {
        val file = File("DO/NOT/EXIST")
        assertThrows<IOException> { listOf(file).walkFiles(ABORT_DO_NOTHING).toList() }
    }

    @Test
    fun `Check walkFiles do not throw exception on non-existing path with IGNORE_DO_NOTHING`() {
        val file = File("DO/NOT/EXIST")
        listOf(file).walkFiles(IGNORE_DO_NOTHING).toList()
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