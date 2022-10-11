import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
    }
}

val unimplementedNativeBuiltIns =
    (file("$rootDir/core/builtins/native/kotlin/").list().toSortedSet() - file("$rootDir/libraries/stdlib/js-ir/builtins/").list())
        .map { "core/builtins/native/kotlin/$it" }

// Required to compile native builtins with the rest of runtime
val builtInsHeader = """@file:Suppress(
    "NON_ABSTRACT_FUNCTION_WITH_NO_BODY",
    "MUST_BE_INITIALIZED_OR_BE_ABSTRACT",
    "EXTERNAL_TYPE_EXTENDS_NON_EXTERNAL_TYPE",
    "PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED",
    "WRONG_MODIFIER_TARGET",
    "UNUSED_PARAMETER"
)
"""

val commonMainSources by task<Sync> {
    dependsOn(":prepare:build.version:writeStdlibVersion")

    val sources = listOf(
        "libraries/stdlib/common/src/",
        "libraries/stdlib/src/kotlin/",
        "libraries/stdlib/unsigned/"
    )

    sources.forEach { path ->
        from("$rootDir/$path") {
            into(path.dropLastWhile { it != '/' })
        }
    }

    into("$buildDir/commonMainSources")
}

val jsMainSources by task<Sync> {
    val sources = listOf(
        "core/builtins/src/kotlin/",
        "libraries/stdlib/js/src/",
        "libraries/stdlib/js/runtime/",

        "libraries/stdlib/native-wasm/src/kotlin/collections/HashMap.kt",
        "libraries/stdlib/native-wasm/src/kotlin/collections/HashSet.kt",
        "libraries/stdlib/native-wasm/src/kotlin/native/FreezingIsDeprecated.kt",

        "libraries/stdlib/wasm/stubs/native"
    ) + unimplementedNativeBuiltIns

    val excluded = listOf(
        // stdlib/js/src/generated is used exclusively for current `js-v1` backend.
        "libraries/stdlib/js/src/generated/**",
        "libraries/stdlib/js/src/kotlin/browser",
        "libraries/stdlib/js/src/kotlin/dom",
        "libraries/stdlib/js/src/org.w3c",
        "libraries/stdlib/js/src/kotlinx",

        // JS-specific optimized version of emptyArray() already defined
        "core/builtins/src/kotlin/ArrayIntrinsics.kt",

        "libraries/stdlib/src/kotlin/annotations/NativeAnnotations.kt",
        "libraries/stdlib/src/kotlin/annotations/NativeConcurrentAnnotations.kt",

        "libraries/stdlib/js/src/kotlin/collections/HashMap.kt",
        "libraries/stdlib/js/src/kotlin/collections/LinkedHashMap.kt",
        "libraries/stdlib/js/src/kotlin/collections/HashSet.kt",
        "libraries/stdlib/js/src/kotlin/collections/LinkedHashSet.kt"
    )

    sources.forEach { path ->
        from("$rootDir/$path") {
            into(path.dropLastWhile { it != '/' })
            excluded.filter { it.startsWith(path) }.forEach {
                exclude(it.substring(path.length))
            }
        }
    }

    into("$buildDir/jsMainSources")

    val unimplementedNativeBuiltIns = unimplementedNativeBuiltIns
    val buildDir = buildDir
    val builtInsHeader = builtInsHeader
    doLast {
        unimplementedNativeBuiltIns.forEach { path ->
            val file = File("$buildDir/jsMainSources/$path")
            val sourceCode = builtInsHeader + file.readText()
            file.writeText(sourceCode)
        }
    }
}

val commonTestSources by task<Sync> {
    val sources = listOf(
        "libraries/stdlib/test/",
        "libraries/stdlib/common/test/"
    )

    sources.forEach { path ->
        from("$rootDir/$path") {
            into(path.dropLastWhile { it != '/' })
        }
    }

    into("$buildDir/commonTestSources")
}

val jsTestSources by task<Sync> {
    from("$rootDir/libraries/stdlib/js/test/")
    into("$buildDir/jsTestSources")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(files(commonMainSources.map { it.destinationDir }))
        }
        val jsMain by getting {
            kotlin.srcDir(files(jsMainSources.map { it.destinationDir }))
            kotlin.srcDir("builtins")
            kotlin.srcDir("runtime")
            kotlin.srcDir("src")
        }
        val commonTest by getting {
            dependencies {
                api(project(":kotlin-test:kotlin-test-js-ir"))
            }
            kotlin.srcDir(files(commonTestSources.map { it.destinationDir }))
        }
        val jsTest by getting {
            dependencies {
                api(project(":kotlin-test:kotlin-test-js-ir"))
            }
            kotlin.srcDir(files(jsTestSources.map { it.destinationDir }))
        }
    }
}

tasks.withType<KotlinCompile<*>>().configureEach {
    kotlinOptions.freeCompilerArgs += listOf(
        "-Xallow-kotlin-package",
        "-opt-in=kotlin.ExperimentalMultiplatform",
        "-opt-in=kotlin.contracts.ExperimentalContracts",
        "-opt-in=kotlin.RequiresOptIn",
        "-opt-in=kotlin.ExperimentalUnsignedTypes",
        "-opt-in=kotlin.ExperimentalStdlibApi",
        "-opt-in=kotlin.io.encoding.ExperimentalEncodingApi",
        "-XXLanguage:+RangeUntilOperator",
    )
}

val compileKotlinJs by tasks.existing(KotlinCompile::class) {
    kotlinOptions.freeCompilerArgs += "-Xir-module-name=kotlin"

    if (!kotlinBuildProperties.disableWerror) {
        kotlinOptions.allWarningsAsErrors = true
    }
}

val compileTestKotlinJs by tasks.existing(KotlinCompile::class) {
    val sources: FileCollection = kotlin.sourceSets["commonTest"].kotlin
    doFirst {
        // Note: common test sources are copied to the actual source directory by commonMainSources task,
        // so can't do this at configuration time:
        kotlinOptions.freeCompilerArgs += "-Xcommon-sources=${sources.joinToString(",")}"
    }
}

val packFullRuntimeKLib by tasks.registering(Jar::class) {
    dependsOn(compileKotlinJs)
    from(buildDir.resolve("classes/kotlin/js/main"))
    destinationDirectory.set(rootProject.buildDir.resolve("js-ir-runtime"))
    archiveFileName.set("full-runtime.klib")
}

val sourcesJar by tasks.registering(Jar::class) {
    dependsOn(jsMainSources)
    val jsMainSourcesDir = jsMainSources.get().destinationDir
    archiveClassifier.set("sources")
    includeEmptyDirs = false
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from("${rootDir}/core/builtins/native/kotlin") {
        into("kotlin")
        include("Comparable.kt")
        include("Enum.kt")
    }
    from("$jsMainSourcesDir/core/builtins/native") {
        exclude("kotlin/Comparable.kt")
    }
    from("$jsMainSourcesDir/core/builtins/src")
    from("$jsMainSourcesDir/libraries/stdlib/js/src")
    from("builtins") {
        into("kotlin")
        exclude("Enum.kt")
    }
    from("runtime") {
        into("runtime")
    }
    from("src") {
        include("**/*.kt")
    }
}

