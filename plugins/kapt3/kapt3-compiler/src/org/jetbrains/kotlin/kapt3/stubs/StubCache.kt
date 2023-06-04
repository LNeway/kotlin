/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt3.stubs

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.xml.parsers.SAXParserFactory
import javax.xml.stream.XMLOutputFactory

class StubCache : DefaultHandler() {
    private val lastBuildFileMD5Map = mutableMapOf<String, String>()
    private val lastBuildFileSubsInfoMap = mutableMapOf<String, MutableList<Pair<String, String>>>()

    fun getCachePath(moduleName:String):String {
        val cacheDir = File(System.getProperty("user.home"), ".gradle/stubCache/${moduleName.hashCode()}")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        println("$moduleName back up dir is ${cacheDir.absolutePath}")
        return cacheDir.absolutePath + File.separator + "cache.xml"
    }

    fun loadStubsData(path: String) {
        if (!File(path).exists()) {
            println("the cache file $path not exist, ignore the cache")
            return
        }
        val parserFactory = SAXParserFactory.newInstance()
        val parser = parserFactory.newSAXParser()
        val handler = this
        parser.parse(path, handler)
    }

    private val currentFileSubsInfoMap = mutableMapOf<String, MutableList<Pair<String, String>>>()
    private val currentFileMD5Map = mutableMapOf<String, String>()

    fun backUpKtFileStubFile(filePath: String, stubFilePath: String) {
        val currentMD5 = calcFileMD5(filePath)
        currentFileMD5Map[filePath] = currentMD5
        val pair = Pair(stubFilePath, "")
        var mutableList = currentFileSubsInfoMap[filePath]
        if (mutableList == null) {
            mutableList = mutableListOf()
        }
        mutableList.add(pair)
        currentFileSubsInfoMap[filePath] = mutableList
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
            currentFileMD5Map.forEach { filePath, md5 ->
                writeStartElement("file")
                writeAttribute("path", filePath)
                writeAttribute("md5", md5)
                val classFileInfo =  currentFileSubsInfoMap[filePath]
                assert(classFileInfo!!.isNotEmpty())
                classFileInfo.forEach {
                    writeStartElement("stub")
                    writeAttribute("classPath", it.first)
                    writeAttribute("metaPath", it.second)
                    writeEndElement()
                }
                writeEndElement()
            }
            // 写入结束标签
            writeEndElement()
            // 结束 XML 写入
            writeEndDocument()
            // 关闭 XMLStreamWriter
            close()
        }
    }

    fun hasKtFileCache(filePath:String):Boolean {
        val currentMD5 = calcFileMD5(filePath)
        return lastBuildFileMD5Map[filePath] == currentMD5
    }

    fun getStubFilesByKtFile(filePath:String):List<Pair<String, String>> {
        return lastBuildFileSubsInfoMap.get(filePath)?: emptyList()
    }

    private fun calcFileMD5(filePath:String):String {
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
        return stringBuilder.toString()
    }

    private var parseKtPath:String? = null

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        if (qName == "file") {
            val md5Index = attributes.getIndex("md5")
            val md5 = attributes.getValue(md5Index)
            val path = attributes.getValue(attributes.getIndex("path"))
            lastBuildFileMD5Map[path] = md5
            parseKtPath = path
        }

        if (qName == "stub") {
            val classpath = attributes.getValue( attributes.getIndex("classPath"))
            val metaPath = attributes.getValue( attributes.getIndex("metaPath"))
            var list = lastBuildFileSubsInfoMap[parseKtPath!!]
            if (list == null) {
                list = mutableListOf<Pair<String, String>>()
            }
            list.add(Pair(classpath, metaPath))
            lastBuildFileSubsInfoMap[parseKtPath!!] = list;
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        super.endElement(uri, localName, qName)
        if (qName == "file") {
            parseKtPath = null
        }
    }
}