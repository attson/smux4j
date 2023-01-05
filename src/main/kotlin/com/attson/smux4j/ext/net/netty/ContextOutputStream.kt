package com.attson.smux4j.ext.net.netty

import io.netty.channel.ChannelHandlerContext
import java.io.OutputStream

class ContextOutputStream(private val ctx: ChannelHandlerContext): OutputStream() {
    override fun toString(): String {
        return ctx.channel().id().asLongText()
    }

    override fun write(b: Int) {
        val ioBuffer = ctx.alloc().ioBuffer()

        ioBuffer.writeByte(b)

        ctx.write(ioBuffer)
    }

    override fun flush() {
        ctx.flush()
    }

    override fun write(b: ByteArray) {
        val ioBuffer = ctx.alloc().ioBuffer()

        ioBuffer.writeBytes(b)

        ctx.write(ioBuffer)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        val byteArray = ByteArray(len)

        System.arraycopy(b, off, byteArray, 0, len)

        this.write(byteArray)
    }

    override fun close() {
        ctx.close().sync()
    }
}