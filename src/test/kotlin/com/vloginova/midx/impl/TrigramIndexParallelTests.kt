package com.vloginova.midx.impl

import com.vloginova.midx.assertCollectionEquals
import com.vloginova.midx.generateRandomText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import java.io.File
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
                buildIndexAsync(listOf(rootDirectory), context = newSingleThreadContext("Test")).await()
            val indexBuiltInParallel = buildIndexAsync(listOf(rootDirectory)).await()

            assertEquals(indexBuiltSequentially, indexBuiltInParallel)
        }
    }

    @UseExperimental(ObsoleteCoroutinesApi::class)
    @Test
    fun `Ensure sequential index search produce the same result as parallel`() {
        runBlocking {
            val index = buildIndexAsync(listOf(rootDirectory)).await()
            val searchText = generateRandomText(9)

            val sequentialSearchResult = index.search(searchText, context = newSingleThreadContext("Test")).toList()
            val parallelSearchResult = index.search(searchText).toList()

            assertCollectionEquals(sequentialSearchResult, parallelSearchResult)
        }
    }

    @Test
    fun `Check build cancellation`() {
        runBlocking {
            val indexBuiltInParallel = buildIndexAsync(listOf(rootDirectory))
            delay(10)
            val cancellationTime = measureTimeMillis { indexBuiltInParallel.cancelAndJoin() }
            assertEquals(true, indexBuiltInParallel.isCancelled, "Build was completed before cancellation")
            assertTrue(cancellationTime < 200, "Cancellation was too long: $cancellationTime")
        }
    }

    private fun assertEquals(expectedIndex: TrigramIndex, actualIndex: TrigramIndex) {
        val indexStorageField = TrigramIndex::class.memberProperties
            .first { it.name == "indexStorage" } as KProperty1<TrigramIndex, TrigramIndexStorage>
        indexStorageField.isAccessible = true

        val partitionsField = TrigramIndexStorage::class.memberProperties
            .first { it.name == "partitions" } as KProperty1<TrigramIndexStorage, Collection<TrigramIndexStoragePartition>>
        partitionsField.isAccessible = true

        val partitionsExpected = partitionsField(indexStorageField.get(expectedIndex))
        val partitionsActual = partitionsField(indexStorageField.get(actualIndex))

        val mergedExpected = mergePartitions(partitionsExpected)
        val mergedActual = mergePartitions(partitionsActual)

        assertEquals(mergedExpected.size, mergedActual.size, "Size of storages are differ")
        assertAll("Expected entity is missed in the actual storage", mergedExpected.map { entity ->
            Executable {
                assertEquals(entity.value.size, mergedActual[entity.key]!!.intersect(entity.value).size)
            }
        })
    }

    private fun mergePartitions(partitions: Collection<TrigramIndexStoragePartition>): HashMap<Int, ArrayList<File>> {
        val result = HashMap<Int, ArrayList<File>>()
        partitions.forEach { partition ->
            for ((trigram, files) in partition) {
                result.computeIfAbsent(trigram) { ArrayList() }.addAll(files)
            }
        }
        return result
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
