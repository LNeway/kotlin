/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt3.stubs

object StubCacheManager {

    private val map = mutableMapOf<String, StubCache>();

    fun getStubCacheByModuleName(module: String): StubCache {
        var cache = map[module]
        if (cache == null) {
            cache = StubCache()
            map[module] = cache
        }
        return cache
    }
}