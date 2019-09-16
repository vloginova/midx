package com.vloginova.midx.impl

import com.vloginova.midx.api.Index
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

internal abstract class AbstractIndexFunctionalTest {
    companion object {
        private const val testFilesPath = "/simpleTestFiles"
        lateinit var index: Index

        fun buildIndex() {
            index.build(File(AbstractIndexFunctionalTest::class.java.getResource(testFilesPath).file))
        }

        @JvmStatic
        fun testDataProvider(): Stream<Pair<String, Array<Triple<String, String, Int>>>> {
            return Stream.of(
                Pair(
                    "r of the", arrayOf(
                        Triple("/simpleTestFiles/text1.txt", "The hour of the waning of love has beset us,", 7)
                    )
                ),
                Pair(
                    "ani", arrayOf(
                        Triple("/simpleTestFiles/text1.txt", "The hour of the waning of love has beset us,", 17)
                    )
                ),
                Pair(
                    "The hour", arrayOf(
                        Triple("/simpleTestFiles/text1.txt", "The hour of the waning of love has beset us,", 0)
                    )
                ),
                Pair(
                    "of", arrayOf(
                        Triple("/simpleTestFiles/text1.txt", "The hour of the waning of love has beset us,", 9),
                        Triple("/simpleTestFiles/text1.txt", "The hour of the waning of love has beset us,", 23),
                        Triple("/simpleTestFiles/text2.txt", "Yellow the leaves of the rowan above us,", 18),
                        Triple("/simpleTestFiles/text1.txt", "Let us part, ere the season of passion forget us,", 28)
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
    fun testSearchResult(testData: Pair<String, Array<Triple<String, String, Int>>>) {
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

    private fun collectMatches(searchText: String): Collection<Triple<String, String, Int>> {
        val matches = ArrayList<Triple<String, String, Int>>()
        index.search(searchText) { fileName, line, startIdx ->
            val simplifiedFileName = fileName.replaceFirst(Regex(".*$testFilesPath"), testFilesPath)
            matches.add(Triple(simplifiedFileName, line, startIdx))
        }
        return matches
    }
}

internal class SimpleIndexFunctionalTestImpl : AbstractIndexFunctionalTest() {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initializeAndBuildIndex() {
            index = SimpleIndex()
            buildIndex()
        }
    }
}

internal class TrigramIndexFunctionalTestImpl : AbstractIndexFunctionalTest() {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initializeAndBuildIndex() {
            index = TrigramIndex()
            buildIndex()
        }
    }
}
