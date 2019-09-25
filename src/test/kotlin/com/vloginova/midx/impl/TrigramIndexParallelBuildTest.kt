package com.vloginova.midx.impl

import com.vloginova.midx.util.collections.TrigramIndexStorage
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import java.io.File
import kotlin.random.Random
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.system.measureTimeMillis

val alphabet = ('a'..'Z').plus('а'..'Я').plus(arrayOf('{', '}', '(', ')', '\n', '\r')).toCharArray()

@Suppress("UNCHECKED_CAST")
class TrigramIndexParallelBuildTest {

    @UseExperimental(ObsoleteCoroutinesApi::class)
    @Test
    fun `Ensure sequential index building produce the same result as parallel`() {
        runBlocking {
            val indexBuiltSequentially =
                buildIndexAsync(rootFolder, newSingleThreadContext("Test")).await() as TrigramIndex
            val indexBuiltInParallel = buildIndexAsync(rootFolder).await() as TrigramIndex

            assertEquals(indexBuiltSequentially, indexBuiltInParallel)
        }
    }

    @Test
    fun `Check build cancellation`() {
        runBlocking {
            val indexBuiltInParallel = buildIndexAsync(rootFolder)
            val cancellationTime = measureTimeMillis { indexBuiltInParallel.cancelAndJoin() }
            assertEquals(true, indexBuiltInParallel.isCancelled)
            assertTrue(cancellationTime < 100)
        }
    }

    private fun assertEquals(expectedIndex: TrigramIndex, actualIndex: TrigramIndex) {
        val field =
            TrigramIndex::class.memberProperties.first { it.name == "indexStorage" } as KProperty1<TrigramIndex, TrigramIndexStorage>
        field.isAccessible = true

        val indexStorageExpected = field.get(expectedIndex)
        val indexStorageActual = field.get(actualIndex)

        assertEquals(indexStorageExpected.size, indexStorageActual.size)
        Assertions.assertAll("Expected match is missed in the actual result", indexStorageExpected.map { entity ->
            Executable {
                assertEquals(entity.value.size, indexStorageActual[entity.key]!!.intersect(entity.value).size)
            }
        })
    }

    companion object {
        private val rootFolder = createTempDir()

        @JvmStatic
        @BeforeAll
        fun generateInputData() {
            val directories = ArrayList<File>()
            directories.add(rootFolder)

            repeat(10) {
                createTempDir(prefix = "Dir$it", directory = directories[Random.nextInt(0, directories.size)])
            }

            repeat(100) {
                val file =
                    createTempFile(prefix = "File$it", directory = directories[Random.nextInt(0, directories.size)])
                val text = (1..Random.nextInt(100, 2 * 1024 * 1024))
                    .map { Random.nextInt(0, alphabet.size) }
                    .map(alphabet::get)
                    .joinToString("")
                file.writeText(text, Charsets.UTF_8)
            }
        }

        @JvmStatic
        @AfterAll
        fun cleanUpInputData() {
            rootFolder.deleteRecursively()
        }
    }
}