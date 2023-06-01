/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.native

import org.jetbrains.kotlin.gradle.BaseGradleIT
import org.jetbrains.kotlin.gradle.GradleVersionRequired
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType
import org.jetbrains.kotlin.gradle.transformProjectWithPluginsDsl
import org.junit.Test

class JsSetupConfigurationCacheIT : BaseGradleIT() {
    private val gradleVersion = GradleVersionRequired.FOR_MPP_SUPPORT

    final override fun defaultBuildOptions() =
        super.defaultBuildOptions().copy(
            jsCompilerType = KotlinJsCompilerType.IR,
            configurationCache = true,
            configurationCacheProblems = BaseGradleIT.ConfigurationCacheProblems.FAIL
        )

    @Test
    fun checkNodeJsSetup() {
        val gradleProject = transformProjectWithPluginsDsl("kotlin-js-browser-project", gradleVersion)

        with(gradleProject) {
            build(
                "kotlinUpgradeYarnLock"
            ) {
                assertTasksExecuted(":kotlinUpgradeYarnLock")
                assertContains("Configuration cache entry stored.")
            }

            build("kotlinUpgradeYarnLock") {
                assertTasksUpToDate(":kotlinUpgradeYarnLock")
                assertContains("Reusing configuration cache.")
            }
        }
    }
}