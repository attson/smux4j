package com.attson.smux4j.ext.io

import java.io.InputStream
import java.util.*
import kotlin.collections.ArrayList

class ByteArraysInputStream(private val bytes: ArrayList<ByteArray>, private var readable: Int) : InputStream() {
    private var index: Int = 0

    private var offset: Int = 0

    private var markIndex: Int = 0

    private var markOffset: Int = 0

    private var markReadable: Int = 0

    fun clearRead() {
        if (readable == 0) {
            bytes.clear()
            return
        }

        // 从头部移除
        for (i in 0 until index) {
            bytes.removeFirst()
        }
        index = 0

        if (bytes[index].size - offset > 0) {
            val byteArray = ByteArray(bytes[index].size - offset)

            System.arraycopy(bytes[index], offset, byteArray, 0, byteArray.size)

            bytes[index] = byteArray
        } else {
            bytes.removeFirst()
        }

        offset = 0
    }

    override fun read(): Int {
        val startIndex = index

        for (i in startIndex until bytes.size) {
            // index move

            if (bytes[i].size - offset > 0) {
                val toInt = bytes[i][offset++].toInt()

                readable -= 1

                return toInt
            }

            offset = 0
            index ++
        }

        return -1
    }

    override fun available(): Int {
        return readable
    }

    override fun read(b: ByteArray): Int {
        return super.read(b)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        Objects.checkFromIndexSize(off, len, b.size)

        if (readable <= 0) {
            return -1
        }

        var n = 0

        val startIndex = index

        for (i in startIndex until bytes.size) {
            if (bytes[i].size - offset > 0) {
                val copyLen = (len - n).coerceAtMost(bytes[i].size - offset)

                System.arraycopy(bytes[i], offset, b, off + n, copyLen)

                n += copyLen

                readable -= copyLen

                if (n == len) {
                    offset += copyLen
                    break
                }
            }

            offset = 0
            index ++
        }

        return n
    }

    override fun readAllBytes(): ByteArray {
        return super.readAllBytes()
    }

    override fun reset() {
        this.index = this.markIndex
        this.offset = this.markOffset
        this.readable = this.markReadable
    }

    override fun readNBytes(len: Int): ByteArray {
        return super.readNBytes(len)
    }

    override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int {
        return super.readNBytes(b, off, len)
    }

    override fun mark(readlimit: Int) {
        this.markIndex = this.index
        this.markOffset = this.offset
        this.markReadable = this.readable
    }

    override fun markSupported(): Boolean {
        return true
    }
}