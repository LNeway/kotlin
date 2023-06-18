/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.psi.KtFile;

import java.util.HashMap;
import java.util.Map;

public class KotlinParser implements PsiParser {


    private static Map<String, ASTNode> nodeMap = new HashMap();

    public KotlinParser(Project project) {
    }

    @Override
    @NotNull
    public ASTNode parse(@NotNull IElementType iElementType, @NotNull PsiBuilder psiBuilder) {
        throw new IllegalStateException("use another parse");
    }

    // we need this method because we need psiFile
    @NotNull
    public ASTNode parse(IElementType iElementType, PsiBuilder psiBuilder, PsiFile psiFile) {
        String path = null;
        if (psiFile.getVirtualFile() != null) {
            path = psiFile.getVirtualFile().getPath();
            if (nodeMap.containsKey(path)) {
                System.out.println("[TimeTest] " + psiFile.getName() + "hit cache");
                return nodeMap.get(path);
            }
        }

        long startTime = System.currentTimeMillis();
        KotlinParsing ktParsing = KotlinParsing.createForTopLevel(new SemanticWhitespaceAwarePsiBuilderImpl(psiBuilder));
        String extension = FileUtilRt.getExtension(psiFile.getName());
        if (extension.isEmpty() || extension.equals(KotlinFileType.EXTENSION) || (psiFile instanceof KtFile && ((KtFile) psiFile).isCompiled())) {
            ktParsing.parseFile();
        }
        else {
            ktParsing.parseScript();
        }
        System.out.println("[TimeTest] " + psiFile.getName() + "parse cost " + (System.currentTimeMillis() - startTime));
        if (path != null) {
            nodeMap.put(path, psiBuilder.getTreeBuilt());
            return nodeMap.get(path);
        } else {
           return psiBuilder.getTreeBuilt();
        }
    }

    @NotNull
    public static ASTNode parseTypeCodeFragment(PsiBuilder psiBuilder) {
        KotlinParsing ktParsing = KotlinParsing.createForTopLevel(new SemanticWhitespaceAwarePsiBuilderImpl(psiBuilder));
        ktParsing.parseTypeCodeFragment();
        return psiBuilder.getTreeBuilt();
    }

    @NotNull
    public static ASTNode parseExpressionCodeFragment(PsiBuilder psiBuilder) {
        KotlinParsing ktParsing = KotlinParsing.createForTopLevel(new SemanticWhitespaceAwarePsiBuilderImpl(psiBuilder));
        ktParsing.parseExpressionCodeFragment();
        return psiBuilder.getTreeBuilt();
    }

    @NotNull
    public static ASTNode parseBlockCodeFragment(PsiBuilder psiBuilder) {
        KotlinParsing ktParsing = KotlinParsing.createForTopLevel(new SemanticWhitespaceAwarePsiBuilderImpl(psiBuilder));
        ktParsing.parseBlockCodeFragment();
        return psiBuilder.getTreeBuilt();
    }

    @NotNull
    public static ASTNode parseLambdaExpression(PsiBuilder psiBuilder) {
        KotlinParsing ktParsing = KotlinParsing.createForTopLevel(new SemanticWhitespaceAwarePsiBuilderImpl(psiBuilder));
        ktParsing.parseLambdaExpression();
        return psiBuilder.getTreeBuilt();
    }

    @NotNull
    public static ASTNode parseBlockExpression(PsiBuilder psiBuilder) {
        KotlinParsing ktParsing = KotlinParsing.createForTopLevel(new SemanticWhitespaceAwarePsiBuilderImpl(psiBuilder));
        ktParsing.parseBlockExpression();
        return psiBuilder.getTreeBuilt();
    }
}
