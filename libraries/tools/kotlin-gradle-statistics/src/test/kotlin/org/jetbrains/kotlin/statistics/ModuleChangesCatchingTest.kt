/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.statistics

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.streams.toList

private const val SOURCE_CODE_RELATIVE_PATH =
    "libraries/tools/kotlin-gradle-statistics/src/main/kotlin/org/jetbrains/kotlin/statistics"
private const val BOOLEAN_METRICS_RELATIVE_PATH = "$SOURCE_CODE_RELATIVE_PATH/metrics/BooleanMetrics.kt"
private const val STRING_METRICS_RELATIVE_PATH = "$SOURCE_CODE_RELATIVE_PATH/metrics/StringMetrics.kt"
private const val NUMERICAL_METRICS_RELATIVE_PATH = "$SOURCE_CODE_RELATIVE_PATH/metrics/NumericalMetrics.kt"
private const val VERSION_REGEX_TEMPLATE = "const val VERSION = (\\d+)"
private val OLD_VERSION_REGEX = Regex("-\\s+$VERSION_REGEX_TEMPLATE")
private val NEW_VERSION_REGEX = Regex("\\+\\s+$VERSION_REGEX_TEMPLATE")

/**
 * This class searches for all the changes in kotlin-gradle-statistics
 * and if there is such changes then it requires to upgrade version of connected metrics.
 */
class ModuleChangesCatchingTest {

    @Test
    fun testFindChangesBetweenCommitAndCurrent() {
        val diffResult =
            runGitProcess("git diff --name-only HEAD^ -- ${Paths.get(SOURCE_CODE_RELATIVE_PATH).toAbsolutePath()}")
        val versionChanges =
            runGitProcess(
                "git diff --unified=0 HEAD^ -- " +
                        "$BOOLEAN_METRICS_RELATIVE_PATH $STRING_METRICS_RELATIVE_PATH $NUMERICAL_METRICS_RELATIVE_PATH"
            )

        val hasVersionBeenIncreased =
            hasVersionBeenIncreased(versionChanges.output)

        val changedFiles = diffResult.output.lines().count()
        // NOTE: this test checks only HEAD^1 commit.
        // So it can fail either if you have uncommitted changes or changed version not in the last commit
        assertTrue(
            changedFiles == 0 || hasVersionBeenIncreased,
            """
            There are changes in this files:
            ${diffResult.output}
            But there were no version increased.
            Please, increase VERSION variable value in one of the metrics enum: BooleanMetrics, NumericalMetrics or StringMetric
            See ReadMe.md file of this module for more information.
            """.trimIndent()
        )
    }

    private class ProcessResult(
        val exitCode: Int,
        val output: String,
    )

    private fun runGitProcess(cmd: String): ProcessResult {
        val process = Runtime.getRuntime().exec(cmd)

        val exitCode = process.waitFor()
        val output =
            BufferedReader(InputStreamReader(process.inputStream))
                .lines()
                .toList()
                .joinToString("\n")

        assertEquals(
            0, exitCode,
            """
            git diff --name-only HEAD^ failed with code ${exitCode}
            
            Here is process output: 
            ${output}
            """.trimIndent()
        )

        return ProcessResult(exitCode, output)
    }

    private fun extractOldVersion(line: String): Int {
        return extractVersion(OLD_VERSION_REGEX, line)
    }

    private fun extractNewVersion(line: String): Int {
        return extractVersion(NEW_VERSION_REGEX, line)
    }

    private fun extractVersion(regex: Regex, line: String): Int {

        val matchResult = regex.find(line)
        return matchResult
            ?.groups
            ?.get(1)
            ?.value
            ?.toIntOrNull()
            ?: fail("Can not extract version from git diff")
    }


    private fun hasVersionBeenIncreased(output: String): Boolean {
        var currentVersion: Int? = null
        var newVersion: Int? = null

        output.lines().forEach { line ->
            if (line.matches(OLD_VERSION_REGEX)) {
                currentVersion = extractOldVersion(line)
            } else if (line.matches(NEW_VERSION_REGEX)) {
                newVersion = extractNewVersion(line)
            }
            if (currentVersion != null && newVersion != null) {
                if (currentVersion!! < newVersion!!) {
                    return true
                } else {
                    currentVersion = null
                    newVersion = null
                }
            }
        }
        return false
    }

}