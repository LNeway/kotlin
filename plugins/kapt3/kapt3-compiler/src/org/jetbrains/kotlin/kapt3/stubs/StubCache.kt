/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt3.stubs

import org.jetbrains.kotlin.kapt3.base.util.info
import org.jetbrains.kotlin.kapt3.util.MessageCollectorBackedKaptLogger
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.xml.parsers.SAXParserFactory
import javax.xml.stream.XMLOutputFactory

class StubCache(val moduleName:String): DefaultHandler() {
    private val lastBuildFileSubsInfoMap = mutableMapOf<String, MutableList<Pair<String, String>>>()
    private val fileMd5Map = mutableMapOf<String, String>()

    private val cacheFileDir:String
    var logger: MessageCollectorBackedKaptLogger? = null

    init {
        val cacheDir = File(System.getProperty("user.home"), ".gradle/stubCache/${moduleName.hashCode()}")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        cacheFileDir = cacheDir.absolutePath
    }

    fun getCachePath():String {
        return cacheFileDir + File.separator + "cache.xml"
    }

    fun loadStubsData(path: String) {
        if (!File(path).exists()) {
            logger?.info("the cache file $path not exist, ignore the cache")
            return
        }
        val parserFactory = SAXParserFactory.newInstance()
        val parser = parserFactory.newSAXParser()
        val handler = this
        parser.parse(path, handler)
        logger?.info("[StubCache] ${moduleName} last cache size is ${lastBuildFileSubsInfoMap.size}")
    }

    fun backUpKtFileStubFile(sourceKtFile: String, stubFilePath: String, stubFileDir:String, stubFileName:String, metaFile:File?, pkgDir:String) {
        val currentMD5 = calcFileMD5(sourceKtFile)
        val metaInfo = metaFile?.absolutePath?.replace(stubFileDir + File.separator, "")?: ""
        val pair = Pair(stubFilePath.replace(stubFileDir + File.separator, ""), metaInfo)
        var mutableList = lastBuildFileSubsInfoMap[currentMD5]
        if (mutableList == null) {
            mutableList = mutableListOf()
        }
        mutableList.add(pair)
        lastBuildFileSubsInfoMap[currentMD5] = mutableList
        val backFileDir = File(cacheFileDir, pkgDir)
        if (!backFileDir.exists()) {
            backFileDir.mkdirs()
        }
        val backupFile = File(backFileDir, stubFileName)
        logger?.info("[StubCache] backup  ${backupFile.absolutePath}")
        File(stubFilePath).copyTo(backupFile, true)

        metaFile?.let {
            val backupMetaFile = File(backFileDir, it.name)
            logger?.info("[StubCache] backup  ${backupMetaFile.absolutePath}")
            it.copyTo(backupMetaFile, true)
        }
    }

    fun restoreStubFile(sourceKtFile: String, stubFileOutDir: String) {
        val md5 = calcFileMD5(sourceKtFile)
        val list = lastBuildFileSubsInfoMap[md5]
        list?.forEach {
            val sourceStubFile = cacheFileDir + File.separator + it.first
            val targetFile = File(stubFileOutDir + File.separator + it.first)
            File(sourceStubFile).copyTo(targetFile, true)
            logger?.error("restore $sourceStubFile to ${targetFile.absolutePath}")

            val sourceMetaFile = cacheFileDir + File.separator + it.second
            val targetMetaFile = File(stubFileOutDir + File.separator + it.second)
            File(sourceMetaFile).copyTo(targetMetaFile, true)
        }
    }

    fun saveCacheToDisk(path: String) {
        val factory = XMLOutputFactory.newFactory()
        // 创建 XMLStreamWriter
        val writer = factory.createXMLStreamWriter(File(path).outputStream(), "UTF-8")
        // 开始写入 XML 内容
        writer.apply {
            // 写入 XML 声明
            writeStartDocument("UTF-8", "1.0")
            // 写入根元素
            writeStartElement("cache")
            // 写入子元素

            lastBuildFileSubsInfoMap.forEach { md5, stubList ->
                writeStartElement("file")
                writeAttribute("md5", md5)
                stubList.forEach {
                    writeStartElement("stub")
                    writeAttribute("classPath", it.first)
                    writeAttribute("metaPath", it.second)
                    writeEndElement()
                }
                writeEndElement()
            }
            writeEndElement()
            writeEndDocument()
            close()
        }
    }

    fun hasKtFileCache(filePath:String):Boolean {
        val currentMD5 = calcFileMD5(filePath)
        val list = lastBuildFileSubsInfoMap[currentMD5]
        return !list.isNullOrEmpty()
    }

    private fun calcFileMD5(filePath:String):String {
        if (fileMd5Map.contains(filePath)) {
            return fileMd5Map[filePath]!!
        }

        val md5Digest = MessageDigest.getInstance("MD5")
        val inputStream = FileInputStream(filePath)
        val buffer = ByteArray(8192)
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            md5Digest.update(buffer, 0, bytesRead)
        }

        inputStream.close()

        val md5Bytes = md5Digest.digest()
        val stringBuilder = StringBuilder()

        for (md5Byte in md5Bytes) {
            stringBuilder.append(Integer.toHexString(0xFF and md5Byte.toInt()))
        }
        fileMd5Map[filePath] = stringBuilder.toString()
        return fileMd5Map[filePath]!!
    }

    private var parseKtMd5:String? = null

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        if (qName == "file") {
            val md5Index = attributes.getIndex("md5")
            val md5 = attributes.getValue(md5Index)
            parseKtMd5 = md5
        }

        if (qName == "stub") {
            val classpath = attributes.getValue(attributes.getIndex("classPath"))
            val metaPath = attributes.getValue(attributes.getIndex("metaPath"))
            var list = lastBuildFileSubsInfoMap[parseKtMd5!!]
            if (list == null) {
                list = mutableListOf<Pair<String, String>>()
            }
            list.add(Pair(classpath, metaPath))
            lastBuildFileSubsInfoMap[parseKtMd5!!] = list;
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        super.endElement(uri, localName, qName)
        if (qName == "file") {
            parseKtMd5 = null
        }
    }
}