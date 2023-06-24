/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental

import org.jetbrains.kotlin.build.report.BuildReporter
import org.jetbrains.kotlin.build.report.ICReporter
import org.jetbrains.kotlin.build.report.RemoteBuildReporter
import org.jetbrains.kotlin.build.report.RemoteReporter
import org.jetbrains.kotlin.cli.common.ExitCode
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
object BuildLogger {
    private var logFileOutputStream:FileOutputStream? = null
    fun init(buildDir: File) {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
        val formattedDate = dateFormat.format(currentDate)
        logFileOutputStream = FileOutputStream(File(buildDir, "$formattedDate-build.log"))
    }

    fun log(message: String) {
        logFileOutputStream?.let {
            try {
                it.write(message.toByteArray())
                it.write("\r\n".toByteArray())
                it.flush()
            } catch (ex:Exception) {
                ex.printStackTrace()
            }
        }
    }
}