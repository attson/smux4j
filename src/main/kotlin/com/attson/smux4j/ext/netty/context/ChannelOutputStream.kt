package com.attson.smux4j.ext.netty.context

import io.netty.channel.Channel
import java.io.OutputStream

class ChannelOutputStream(private val channel: Channel): OutputStream() {
    override fun write(b: Int) {
        val ioBuffer = channel.alloc().ioBuffer()

        ioBuffer.writeByte(b)

        channel.write(ioBuffer)
    }

    override fun flush() {
        channel.flush()
    }

    override fun write(b: ByteArray) {
        val ioBuffer = channel.alloc().ioBuffer()

        ioBuffer.writeBytes(b)

        channel.write(ioBuffer)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        val byteArray = ByteArray(len)

        System.arraycopy(b, off, byteArray, 0, len)

        this.write(byteArray)
    }

    override fun close() {
        channel.close().sync()
    }
}