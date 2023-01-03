package com.attson.smux4j.ext.io

import java.io.DataOutput
import java.io.DataOutputStream
import java.io.OutputStream

// DataInputStream
// to get at the low-level read methods of
class LittleEndianDataOutputStream(private val output: OutputStream) : OutputStream(), DataOutput {
    // to get at high level readFully methods of
    private val d: DataOutputStream

    // InputStream
    private val w: ByteArray // work array for buffering input

    init {
        d = DataOutputStream(output)
        w = ByteArray(8)
    }

    override fun write(b: Int) {
        output.write(b)
    }

    override fun flush() {
        output.flush()
    }

    override fun write(b: ByteArray) {
        output.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        output.write(b, off, len)
    }

    override fun writeBoolean(v: Boolean) {
        d.writeBoolean(v)
    }

    override fun writeByte(v: Int) {
        d.writeByte(v)
    }

    override fun writeShort(v: Int) {
        w[0] = v.toByte()
        w[1] = (v ushr 8).toByte()

        write(w, 0, 2)
    }

    override fun writeChar(v: Int) {
        w[0] = v.toByte()
        w[1] = (v ushr 8).toByte()

        write(w, 0, 2)
    }

    override fun writeInt(v: Int) {
        w[0] = v.toByte()
        w[1] = (v ushr 8).toByte()
        w[2] = (v ushr 16).toByte()
        w[3] = (v ushr 24).toByte()

        write(w, 0, 4)
    }

    override fun writeLong(v: Long) {
        w[0] = v.toByte()
        w[1] = (v ushr 8).toByte()
        w[2] = (v ushr 16).toByte()
        w[3] = (v ushr 24).toByte()
        w[4] = (v ushr 32).toByte()
        w[5] = (v ushr 40).toByte()
        w[6] = (v ushr 48).toByte()
        w[7] = (v ushr 56).toByte()

        write(w, 0, 8)
    }

    override fun writeFloat(v: Float) {
        d.writeFloat(v)
    }

    override fun writeDouble(v: Double) {
        d.writeDouble(v)
    }

    override fun writeBytes(s: String) {
        d.writeBytes(s)
    }

    override fun writeChars(s: String) {
        d.writeChars(s)
    }

    override fun writeUTF(s: String) {
        d.writeUTF(s)
    }
}