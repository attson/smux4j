package com.attson.smux4j.ext.io

import java.io.DataInput
import java.io.DataInputStream
import java.io.InputStream

// DataInputStream
// to get at the low-level read methods of
class LittleEndianDataInputStream(private val input: InputStream) : InputStream(), DataInput {
    // to get at high level readFully methods of
    private val d: DataInputStream

    // InputStream
    private val w: ByteArray // work array for buffering input

    init {
        d = DataInputStream(input)
        w = ByteArray(8)
    }

    override fun readShort(): Short {
        d.readFully(w, 0, 2)
        return (w[1].toInt() and 0xff shl 8 or
                (w[0].toInt() and 0xff)).toShort()
    }

    /**
     * Note, returns int even though it reads a short.
     */

    override fun readUnsignedShort(): Int {
        d.readFully(w, 0, 2)
        return w[1].toInt() and 0xff shl 8 or
                (w[0].toInt() and 0xff)
    }

    /**
     * like DataInputStream.readChar except little endian.
     */

    override fun readChar(): Char {
        d.readFully(w, 0, 2)
        return (w[1].toInt() and 0xff shl 8 or
                (w[0].toInt() and 0xff)).toChar()
    }

    /**
     * like DataInputStream.readInt except little endian.
     */

    override fun readInt(): Int {
        d.readFully(w, 0, 4)
        return w[3].toInt() shl 24 or (
                w[2].toInt() and 0xff shl 16) or (
                w[1].toInt() and 0xff shl 8) or
                (w[0].toInt() and 0xff)
    }

    /**
     * like DataInputStream.readLong except little endian.
     */

    override fun readLong(): Long {
        d.readFully(w, 0, 8)
        return w[7].toLong() shl 56 or (
                (w[6].toInt() and 0xff).toLong() shl 48) or (
                (w[5].toInt() and 0xff).toLong() shl 40) or (
                (w[4].toInt() and 0xff).toLong() shl 32) or (
                (w[3].toInt() and 0xff).toLong() shl 24) or (
                (w[2].toInt() and 0xff).toLong() shl 16) or (
                (w[1].toInt() and 0xff).toLong() shl 8) or (w[0].toInt() and 0xff).toLong()
    }


    override fun readFloat(): Float {
        return readInt().toFloat()
    }


    override fun readDouble(): Double {
        return readLong().toDouble()
    }


    override fun readFully(b: ByteArray) {
        d.readFully(b, 0, b.size)
    }


    override fun readFully(b: ByteArray, off: Int, len: Int) {
        d.readFully(b, off, len)
    }

    override fun skipBytes(n: Int): Int {
        return d.skipBytes(n)
    }

    override fun readBoolean(): Boolean {
        return d.readBoolean()
    }

    override fun readByte(): Byte {
        return d.readByte()
    }

    override fun readUnsignedByte(): Int {
        return d.readUnsignedByte()
    }

    @Deprecated("")
    override fun readLine(): String {
        return d.readLine()
    }

    override fun read(): Int {
        return input.read()
    }

    override fun readUTF(): String {
        return d.readUTF()
    }

    override fun available(): Int {
        return input.available()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return input.read(b, off, len)
    }

    override fun readAllBytes(): ByteArray {
        return input.readAllBytes()
    }

    override fun readNBytes(len: Int): ByteArray {
        return input.readNBytes(len)
    }

    override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int {
        return input.readNBytes(b, off, len)
    }

    override fun read(b: ByteArray): Int {
        return input.read(b)
    }

    override fun mark(readlimit: Int) {
        return input.mark(readlimit)
    }

    override fun markSupported(): Boolean {
        return input.markSupported()
    }

    override fun reset() {
        return input.reset()
    }

    override fun close() {
        input.close()
    }
}