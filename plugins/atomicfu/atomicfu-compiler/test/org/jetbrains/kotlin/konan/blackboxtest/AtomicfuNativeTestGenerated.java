/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.junit.jupiter.api.Tag;
import org.jetbrains.kotlin.konan.blackboxtest.support.group.UseStandardTestCaseGroupProvider;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.GenerateNativeTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("plugins/atomicfu/atomicfu-compiler/testData/nativeBox")
@TestDataPath("$PROJECT_ROOT")
@Tag("codegen")
@UseStandardTestCaseGroupProvider()
public class AtomicfuNativeTestGenerated extends AbstractNativeBlackBoxTest {
    @Test
    public void testAllFilesPresentInBox() throws Exception {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("plugins/atomicfu/atomicfu-compiler/testData/nativeBox"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.NATIVE, true);
    }

    @Test
    @TestMetadata("ArithmeticTest.kt")
    public void testArithmeticTest() throws Exception {
        runTest("plugins/atomicfu/atomicfu-compiler/testData/nativeBox/ArithmeticTest.kt");
    }
}
