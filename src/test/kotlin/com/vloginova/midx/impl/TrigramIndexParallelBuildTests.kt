package com.vloginova.midx.impl

import com.vloginova.midx.generateRandomText
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.system.measureTimeMillis

@ExperimentalCoroutinesApi
@Suppress("UNCHECKED_CAST")
class TrigramIndexParallelBuildTest {

    @UseExperimental(ObsoleteCoroutinesApi::class)
    @Test
    fun `Ensure sequential index building produce the same result as parallel`() {
        runBlocking {
            val indexBuiltSequentially =
                buildIndexAsync(rootDirectory, newSingleThreadContext("Test")).await()
            val indexBuiltInParallel = buildIndexAsync(rootDirectory).await()

            assertEquals(indexBuiltSequentially, indexBuiltInParallel)
        }
    }

    @Test
    fun `Check build cancellation`() {
        runBlocking {
            val indexBuiltInParallel = buildIndexAsync(rootDirectory)
            val cancellationTime = measureTimeMillis { indexBuiltInParallel.cancelAndJoin() }
            assertEquals(true, indexBuiltInParallel.isCancelled, "Build was completed before cancellation")
            assertTrue(cancellationTime < 100, "Cancellation was too long")
        }
    }

    private fun assertEquals(expectedIndex: TrigramIndex, actualIndex: TrigramIndex) {
        val field =
            TrigramIndex::class.memberProperties.first { it.name == "indexStorage" } as KProperty1<TrigramIndex, TrigramIndexStorage>
        field.isAccessible = true

        val indexStorageExpected = field.get(expectedIndex)
        val indexStorageActual = field.get(actualIndex)

        assertEquals(indexStorageExpected.size, indexStorageActual.size, "Size of storages are differ")
        Assertions.assertAll("Expected entity is missed in the actual storage", indexStorageExpected.map { entity ->
            Executable {
                assertEquals(entity.value.size, indexStorageActual[entity.key]!!.intersect(entity.value).size)
            }
        })
    }

    companion object {
        private val rootDirectory = createTempDir()

        @JvmStatic
        @BeforeAll
        fun generateInputData() {
            generateInputData(rootDirectory)
        }
    }
}

@ExperimentalCoroutinesApi
class ExceptionHandlingTrigramIndexTest {
    @Test
    fun `Check index is build without exception when input files are cleaned up`() {
        val rootDirectory = createTempDir()
        generateInputData(rootDirectory)
        val counter = AtomicInteger()
        runBlocking {
            val indexBuiltInParallel = buildIndexAsync(rootDirectory = rootDirectory, handleUnprocessedFile = {
                counter.incrementAndGet()
            })
            delay(100L)
            cleanUpInputData(rootDirectory)
            indexBuiltInParallel.await()
        }
        assertTrue(counter.get() < fileNumber, "Some files should have been processed")
    }
}

private const val fileNumber = 100
private const val folderNumber = 10

private fun generateInputData(rootDirectory: File) {
    val directories = ArrayList<File>()
    directories.add(rootDirectory)

    repeat(folderNumber) {
        val dir = createTempDir(prefix = "Dir$it", directory = directories[Random.nextInt(0, directories.size)])
        dir.deleteOnExit()
    }

    repeat(fileNumber) {
        val file =
            createTempFile(prefix = "File$it", directory = directories[Random.nextInt(0, directories.size)])
        file.deleteOnExit()
        val text = generateRandomText(Random.nextInt(100, 2 * 1024 * 1024))
        file.writeText(text, Charsets.UTF_8)
    }
}

private fun cleanUpInputData(directory: File) {
    directory.deleteRecursively()
}
