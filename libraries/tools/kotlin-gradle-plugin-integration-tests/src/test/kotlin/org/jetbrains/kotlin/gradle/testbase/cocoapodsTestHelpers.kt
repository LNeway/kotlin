/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.testbase

import org.jetbrains.kotlin.gradle.util.replaceText
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

val String.normalizeCocoapadsFrameworkName: String
    get() = replace('-', '_')

enum class ImportMode(val directive: String) {
    FRAMEWORKS("use_frameworks!"),
    MODULAR_HEADERS("use_modular_headers!")
}

/**
 * Configures the usage of a custom framework name for a subproject in [this] test project.
 *
 * @param subprojectName The name of the subproject for which the framework name should be changed.
 * @param frameworkName The custom framework name to be used for the subproject.
 * @param iosAppLocation The optional relative location of the iOS app directory within [this] test project.
 */
fun TestProject.useCustomFrameworkName(
    subprojectName: String,
    frameworkName: String,
    iosAppLocation: String? = null,
) {
    // Change the name at the Gradle side.
    subProject(subprojectName)
        .buildGradleKts
        .addFrameworkBlock("baseName = \"$frameworkName\"")

    // Change swift sources import if needed.
    if (iosAppLocation != null) {
        projectPath
            .resolve(iosAppLocation)
            .resolve("ios-app/ViewController.swift")
            .replaceText(
                "import ${subprojectName.normalizeCocoapadsFrameworkName}",
                "import $frameworkName"
            )
    }
}

/**
 * Prepares the Podfile for an iOS app in the [TestProject]
 *
 * @param iosAppLocation The relative location of the iOS app directory within the [TestProject]
 * @param mode The [ImportMode] to be set for the Podfile.
 *
 */
fun TestProject.preparePodfile(iosAppLocation: String, mode: ImportMode) {
    val iosAppDir = projectPath.resolve(iosAppLocation)

    // Set import mode for Podfile.
    iosAppDir.resolve("Podfile")
        .takeIf { it.exists() }
        ?.replaceText(podfileImportDirectivePlaceholder, mode.directive)
}

/**
 * Wraps the given string into a specRepos block and adds this block to the end of the [this] path.
 *
 * @param specRepo The code to be wrapped with the Cocoapods block.
 */

fun Path.addSpecRepo(specRepo: String) = addCocoapodsBlock("url(\"$specRepo\")".wrapIntoBlock("specRepos"))

/**
 * Wraps the given string into a Cocoapods block and adds this block to the end of the [this] path.
 *
 * @param str The code to be wrapped with the Cocoapods block.
 */
fun Path.addCocoapodsBlock(str: String) = addKotlinBlock(str.wrapIntoBlock("cocoapods"))

/**
 * Wraps the given string into a Kotlin block and adds this block to the end of the [this] path.
 *
 * @param str The code to be wrapped with the Cocoapods block.
 */
fun Path.addKotlinBlock(str: String) = appendLine(str.wrapIntoBlock("kotlin"))

/**
 * Wraps the given string into a Framework block and adds this block to the end of the [this] path.
 *
 * @param str The code to be wrapped with the Cocoapods block.
 */
fun Path.addFrameworkBlock(str: String) = addCocoapodsBlock(str.wrapIntoBlock("framework"))


/**
 * Adds a Cocoapods dependency to [this] build script.
 *
 * @param podName The name of the Cocoapods dependency to be added.
 * @param configuration The optional configuration string for the Cocoapods dependency.
 */
fun Path.addPod(podName: String, configuration: String? = null) {
    val pod = "pod(\"$podName\")"
    val podBlock = configuration?.wrapIntoBlock(pod) ?: pod
    addCocoapodsBlock(podBlock)
}

/**
 * Removes a Cocoapods dependency from [this] build script.
 *
 * @param podName The name of the Cocoapods dependency to be removes.
 */
fun Path.removePod(podName: String) {
    val text = readText()
    val begin = text.indexOf("""pod("$podName")""")
    require(begin != -1) { "Pod doesn't exist in file" }
    var index = begin + """pod("$podName")""".length - 1
    if (text.indexOf("""pod("$podName") {""", startIndex = begin) != -1) {
        index += 2
        var bracket = 1
        while (bracket != 0) {
            if (text[++index] == '{') {
                bracket++
            } else if (text[index] == '}') {
                bracket--
            }
        }
    }
    writeText(text.removeRange(begin..index))
}

private fun Path.appendLine(s: String) = appendText("\n$s")

private val podfileImportDirectivePlaceholder = "<import_mode_directive>"