/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin.services

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.*
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.FirScriptConfiguratorExtension
import org.jetbrains.kotlin.fir.builder.FirScriptConfiguratorExtension.Factory
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.utils.SCRIPT_SPECIAL_NAME_STRING
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirQualifierPartImpl
import org.jetbrains.kotlin.fir.types.impl.FirTypeArgumentListImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.scripting.definitions.annotationsForSamWithReceivers
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration


class FirScriptConfiguratorExtensionImpl(
    session: FirSession,
    // TODO: left here because it seems it will be needed soon, remove supression if used or remove the param if it is not the case
    @Suppress("UNUSED_PARAMETER") hostConfiguration: ScriptingHostConfiguration,
) : FirScriptConfiguratorExtension(session) {

    @OptIn(SymbolInternals::class)
    override fun FirScriptBuilder.configure(fileBuilder: FirFileBuilder) {
        val sourceFile = fileBuilder.sourceFile ?: return

        withConfigurationIfAny(sourceFile) { configuration ->
            // TODO: rewrite/extract decision logic for clarity
            configuration[ScriptCompilationConfiguration.baseClass]?.let { baseClass ->
                val baseClassFqn = FqName.fromSegments(baseClass.typeName.split("."))
                contextReceivers.add(buildContextReceiverWithFqName(baseClassFqn, Name.special(SCRIPT_SPECIAL_NAME_STRING)))

                val baseClassSymbol =
                    session.dependenciesSymbolProvider.getClassLikeSymbolByClassId(ClassId(baseClassFqn.parent(), baseClassFqn.shortName()))
                            as? FirRegularClassSymbol
                if (baseClassSymbol != null) {
                    // assuming that if base class will be unresolved, the error will be reported on the contextReceiver
                    baseClassSymbol.fir.primaryConstructorIfAny(session)?.fir?.valueParameters?.forEach { baseCtorParameter ->
                        parameters.add(
                            buildProperty {
                                moduleData = session.moduleData
                                origin = FirDeclarationOrigin.ScriptCustomization.Default
                                // TODO: copy type parameters?
                                returnTypeRef = baseCtorParameter.returnTypeRef
                                name = baseCtorParameter.name
                                symbol = FirPropertySymbol(name)
                                status = FirDeclarationStatusImpl(Visibilities.Local, Modality.FINAL)
                                isLocal = true
                                isVar = false
                            }
                        )
                    }
                }
            }
            configuration[ScriptCompilationConfiguration.implicitReceivers]?.forEach { implicitReceiver ->
                contextReceivers.add(buildContextReceiverWithFqName(FqName.fromSegments(implicitReceiver.typeName.split("."))))
            }
            configuration[ScriptCompilationConfiguration.providedProperties]?.forEach { propertyName, propertyType ->
                val typeRef = buildUserTypeRef {
                    isMarkedNullable = propertyType.isNullable
                    propertyType.typeName.split(".").forEach {
                        qualifier.add(FirQualifierPartImpl(null, Name.identifier(it), FirTypeArgumentListImpl(null)))
                    }
                }
                parameters.add(
                    buildProperty {
                        moduleData = session.moduleData
                        origin = FirDeclarationOrigin.ScriptCustomization.Default
                        returnTypeRef = typeRef
                        name = Name.identifier(propertyName)
                        symbol = FirPropertySymbol(name)
                        status = FirDeclarationStatusImpl(Visibilities.Local, Modality.FINAL)
                        isLocal = true
                        isVar = false
                    }
                )
            }
            configuration[ScriptCompilationConfiguration.annotationsForSamWithReceivers]?.forEach {
                _knownAnnotationsForSamWithReceiver.add(it.typeName)
            }

            configuration[ScriptCompilationConfiguration.defaultImports]?.forEach { defaultImport ->
                val trimmed = defaultImport.trim()
                val endsWithStar = trimmed.endsWith("*")
                val stripped = if (endsWithStar) trimmed.substring(0, trimmed.length - 2) else trimmed
                val fqName = FqName.fromSegments(stripped.split("."))
                fileBuilder.imports += buildImport {
                    fileBuilder.sourceFile?.project()?.let {
                        val dummyElement = KtPsiFactory(it, markGenerated = true).createColon()
                        source = KtFakeSourceElement(dummyElement, KtFakeSourceElementKind.ImplicitImport)
                    }
                    importedFqName = fqName
                    isAllUnder = endsWithStar
                }
            }

            configuration[ScriptCompilationConfiguration.annotationsForSamWithReceivers]?.forEach {
                _knownAnnotationsForSamWithReceiver.add(it.typeName)
            }

            configuration[ScriptCompilationConfiguration.resultField]?.takeIf { it.isNotBlank() }?.let { resultFieldName ->
                val lastExpression = statements.lastOrNull()
                if (lastExpression != null && lastExpression is FirExpression) {
                    statements.removeAt(statements.size - 1)
                    statements.add(
                        buildProperty {
                            this.name = Name.identifier(resultFieldName)
                            this.symbol = FirPropertySymbol(this.name)
                            source = lastExpression.source
                            moduleData = session.moduleData
                            origin = FirDeclarationOrigin.ScriptCustomization.ResultProperty
                            initializer = lastExpression
                            returnTypeRef = lastExpression.typeRef
                            getter = FirDefaultPropertyGetter(
                                lastExpression.source,
                                session.moduleData,
                                FirDeclarationOrigin.ScriptCustomization.ResultProperty,
                                lastExpression.typeRef,
                                Visibilities.Public,
                                this.symbol,
                            )
                            status = FirDeclarationStatusImpl(Visibilities.Public, Modality.FINAL)
                            isLocal = false
                            isVar = false
                        }.also {
                            resultPropertyName = it.name
                        }
                    )
                }
            }
        }
    }

    private fun withConfigurationIfAny(file: KtSourceFile, body: (ScriptCompilationConfiguration) -> Unit) {
        val configuration = session.scriptDefinitionProviderService?.let { providerService ->
            val sourceCode = file.toSourceCode()
            val ktFile = sourceCode?.originalKtFile()
            with(providerService) {
                ktFile?.let { configurationFor(it) }
                    ?: sourceCode?.let { configurationFor(it) }
                    ?: defaultConfiguration()
            }
        }

        configuration?.let { body.invoke(it) }
    }

    private fun buildContextReceiverWithFqName(classFqn: FqName, customName: Name? = null) =
        buildContextReceiver {
            typeRef = buildUserTypeRef {
                isMarkedNullable = false
                qualifier.addAll(
                    classFqn.pathSegments().map {
                        FirQualifierPartImpl(null, it, FirTypeArgumentListImpl(null))
                    }
                )
            }
            if (customName != null) {
                customLabelName = customName
            }
        }

    private val _knownAnnotationsForSamWithReceiver = hashSetOf<String>()

    internal val knownAnnotationsForSamWithReceiver: Set<String>
        get() = _knownAnnotationsForSamWithReceiver

    companion object {
        fun getFactory(hostConfiguration: ScriptingHostConfiguration): Factory {
            return Factory { session -> FirScriptConfiguratorExtensionImpl(session, hostConfiguration) }
        }
    }
}

private fun KtSourceFile.project(): Project? = (toSourceCode() as? KtFileScriptSource)?.ktFile?.project

private fun SourceCode.originalKtFile(): KtFile =
    (this as? KtFileScriptSource)?.ktFile?.originalFile as? KtFile
        ?: error("only PSI scripts are supported at the moment")

private fun FirScriptDefinitionProviderService.configurationFor(file: KtFile): ScriptCompilationConfiguration? =
    configurationProvider?.getScriptConfigurationResult(file)?.valueOrNull()?.configuration

private fun FirScriptDefinitionProviderService.configurationFor(sourceCode: SourceCode): ScriptCompilationConfiguration? =
    definitionProvider?.findDefinition(sourceCode)?.compilationConfiguration

private fun FirScriptDefinitionProviderService.defaultConfiguration(): ScriptCompilationConfiguration? =
    definitionProvider?.getDefaultDefinition()?.compilationConfiguration

