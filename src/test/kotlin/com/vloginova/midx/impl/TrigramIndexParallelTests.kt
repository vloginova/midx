package com.vloginova.midx.impl

import com.vloginova.midx.api.IOExceptionHandlingStrategy
import com.vloginova.midx.api.IOExceptionHandlingStrategy.Strategy.*
import com.vloginova.midx.api.SearchResult
import com.vloginova.midx.assertCollectionEquals
import com.vloginova.midx.generateRandomText
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.function.Executable
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.system.measureTimeMillis

@Suppress("UNCHECKED_CAST")
class TrigramIndexParallelBuildTest {

    @UseExperimental(ObsoleteCoroutinesApi::class)
    @Test
    fun `Ensure sequential index building produce the same result as parallel`() {
        runBlocking {
            val indexBuiltSequentially =
                buildIndexAsync(rootDirectory, context = newSingleThreadContext("Test")).await()
            val indexBuiltInParallel = buildIndexAsync(rootDirectory).await()

            assertEquals(indexBuiltSequentially, indexBuiltInParallel)
        }
    }

    @UseExperimental(ObsoleteCoroutinesApi::class)
    @Test
    fun `Ensure sequential index search produce the same result as parallel`() {
        runBlocking {
            val index = buildIndexAsync(rootDirectory).await()
            val searchText = generateRandomText(9)

            val sequentialSearchResult = ArrayList<SearchResult>()
            index.searchAsync(searchText, context = newSingleThreadContext("Test")) {
                sequentialSearchResult.add(it)
            }.await()

            val parallelSearchResult = ArrayList<SearchResult>()
            index.searchAsync(searchText) {
                parallelSearchResult.add(it)
            }.await()

            assertCollectionEquals(sequentialSearchResult, parallelSearchResult)
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
        assertAll("Expected entity is missed in the actual storage", indexStorageExpected.map { entity ->
            Executable {
                assertEquals(entity.value.size, indexStorageActual[entity.key]!!.intersect(entity.value).size)
            }
        })
    }

    companion object {
        private lateinit var rootDirectory: File

        @JvmStatic
        @BeforeAll
        fun prepareInputData() {
            rootDirectory = generateInputData()
        }

        @JvmStatic
        @AfterAll
        fun deleteInputData() {
            rootDirectory.deleteRecursively()
        }
    }
}

@ExperimentalCoroutinesApi
class IOExceptionHandlingStrategyTest {

    private lateinit var rootDirectory: File

    @BeforeEach
    fun prepareInputData() {
        rootDirectory = generateInputData(folderNumber = 1)
    }

    @AfterEach
    fun deleteTestFile() {
        rootDirectory.deleteRecursively()
    }

    @Test
    fun `Check built is successful without exception when input files are cleaned up for IGNORE strategy`() {
        val counter = AtomicInteger()
        runBlocking {
            val indexBuiltInParallel = buildIndexAsync(rootDirectory, IOExceptionHandlingStrategy(IGNORE) { _, _ ->
                counter.incrementAndGet()
            })
            delayAndCleanUp(rootDirectory)
            indexBuiltInParallel.await()
        }
        assertTrue(counter.get() > 0, "Some files should have been failed to process")
    }

    @Test
    fun `Check build is successful when input files are cleaned up for RETRY_THEN_IGNORE strategy`() {
        val counter = AtomicInteger()
        runBlocking {
            val indexBuiltInParallel =
                buildIndexAsync(rootDirectory, IOExceptionHandlingStrategy(RETRY_THEN_IGNORE) { _, _ ->
                    counter.incrementAndGet()
                })
            delayAndCleanUp(rootDirectory)
            indexBuiltInParallel.await()
        }
        assertTrue(counter.get() > 0, "Some files should have been failed to process")
    }

    @Test
    fun `Check build is successful when input files are cleaned up and restored for RETRY_THEN_IGNORE strategy`() {
        val counter = AtomicInteger()
        runBlocking {
            val indexBuiltInParallel =
                buildIndexAsync(rootDirectory, IOExceptionHandlingStrategy(RETRY_THEN_IGNORE) { file, _ ->
                    counter.incrementAndGet()
                    recreateFile(file)
                })
            delayAndCleanUp(rootDirectory)
            indexBuiltInParallel.await()
        }
        assertTrue(counter.get() > 0, "Some files should have been failed to process")
    }

    @Test
    fun `Check build is cancelled when input files are cleaned up for ABORT strategy`() {
        runBlocking {
            val indexBuiltInParallel = buildIndexAsync(rootDirectory, IOExceptionHandlingStrategy(ABORT) { _, _ -> })
            delayAndCleanUp(rootDirectory)
            assertThrows<IOException> { runBlocking { indexBuiltInParallel.await() } }
        }
    }

    @Test
    fun `Check build is cancelled when input files are cleaned up for RETRY_THEN_ABORT strategy`() {
        runBlocking {
            val indexBuiltInParallel =
                buildIndexAsync(rootDirectory, IOExceptionHandlingStrategy(RETRY_THEN_ABORT) { _, _ -> })
            delayAndCleanUp(rootDirectory)
            assertThrows<IOException> { runBlocking { indexBuiltInParallel.await() } }
        }
    }

    @Test
    fun `Check build is not cancelled when input files are cleaned up and restored for RETRY_THEN_ABORT strategy`() {
        val counter = AtomicInteger()
        runBlocking {
            val indexBuiltInParallel =
                buildIndexAsync(rootDirectory, IOExceptionHandlingStrategy(RETRY_THEN_ABORT) { file, _ ->
                    recreateFile(file)
                    counter.incrementAndGet()
                })
            delayAndCleanUp(rootDirectory)
            indexBuiltInParallel.await()
        }
        assertTrue(counter.get() > 0, "Some files should have been failed to process")
    }

    private fun recreateFile(file: File) {
        file.parentFile.mkdirs()
        file.createNewFile()
    }

    private suspend fun delayAndCleanUp(rootDirectory: File) {
        // Wait a little so that processing is in progress
        delay(100L)
        cleanUpInputData(rootDirectory)
    }
}

private fun generateInputData(
    folderNumber: Int = 10,
    fileNumber: Int = 100
): File {
    val rootDirectory = createTempDir()
    try {
        val directories = ArrayList<File>()
        directories.add(rootDirectory)

        repeat(folderNumber) {
            val dir = createTempDir(prefix = "Dir$it", directory = directories[Random.nextInt(0, directories.size)])
            directories.add(dir)
        }

        repeat(fileNumber) {
            val file =
                createTempFile(prefix = "File$it", directory = directories[Random.nextInt(0, directories.size)])
            val text = generateRandomText(Random.nextInt(100, 2 * 1024 * 1024))
            file.writeText(text)
        }
    } catch (e: Throwable) {
        cleanUpInputData(rootDirectory)
        throw e
    }

    return rootDirectory
}

private fun cleanUpInputData(directory: File) {
    directory.deleteRecursively()
}
