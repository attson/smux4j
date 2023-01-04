package com.attson.smux4j

import com.attson.smux4j.ext.io.ByteArraysInputStream
import com.attson.smux4j.frame.Frame
import com.attson.smux4j.frame.cmdFIN
import com.attson.smux4j.frame.cmdPSH
import com.attson.smux4j.session.Session
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.OutputStream
import java.util.*

class Stream(private val id: Long, private val maxFrameSize: Int, private val sess: Session) : OutputStream() {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private var closed: Boolean = false

    /**
     * The bytes entered are reserved in recvBuffers
     */
    private var recvBuffers: ArrayList<ByteArray> = arrayListOf()

    private var recvReadable: Int = 0

    private val recvBufferLock = Any()

    private var reading = false

    init {
        if (logger.isDebugEnabled) {
            logger.debug("mux stream opened: " + this.id())
        }
    }

    fun pushBytes(data: ByteArray) {
        synchronized(recvBufferLock) {
            recvBuffers.add(data)
            recvReadable += data.size
        }
    }

    fun shortId(): Long {
        return id
    }

    fun id(): String {
        return "${sess.id()}-${id}"
    }

    public fun notifyReadEvent() {
        if (!reading && recvReadable > 0) {
            // Ensure that only one thread is in read

            // Single thread processing
            sess.executor().execute {
                var input: ByteArraysInputStream
                var readable: Int
                var buffers: ArrayList<ByteArray>

                // Copy the contents in recvBuffers, and use different buffers for reading and writing
                synchronized(recvBufferLock) {
                    if (reading) {
                        return@execute
                    }

                    reading = true

                    // Read from buffers
                    buffers = recvBuffers
                    readable = recvReadable

                    recvBuffers = arrayListOf()
                    recvReadable = 0

                    input = ByteArraysInputStream(buffers, readable)
                }

                // The event processing part is unlocked
                sess.streamListener().onReadEvent(this, input)
                input.clearRead()

                var needRead = false

                synchronized(recvBufferLock) {
                    reading = false

                    if (buffers.isNotEmpty()) {
                        recvBuffers.addAll(0, buffers)

                        needRead = recvReadable > 0
                        recvReadable += input.available()
                    }

                    sess.returnTokens(readable - input.available())
                }

                if (needRead) {
                    this.notifyReadEvent()
                }
            }
        }
    }

    private fun ensureOpen() {
        if (closed) {
            throw IOException("stream ${id()} closed")
        }
    }

    override fun write(b: Int) {
        ensureOpen()

        val frame = Frame(sess.getConfig().version.toUByte(), cmdPSH, this.id)
        frame.setData(byteArrayOf(b.toByte()))

        sess.writeFrame(frame)
    }

    override fun flush() {
        sess.flush()
    }

    override fun write(b: ByteArray) {
        if (logger.isDebugEnabled) {
            logger.debug("stream ${id()} write bytes " + b.size)
        }

        ensureOpen()

        var off = 0

        while (b.size - off > 0) {
            var sz = b.size - off
            if (sz > this.maxFrameSize) {
                sz = this.maxFrameSize
            }

            val byteArray = ByteArray(sz)

            System.arraycopy(b, off, byteArray, 0, sz)
            off += sz

            val frame = Frame(sess.getConfig().version.toUByte(), cmdPSH, this.id)
            frame.setData(byteArray)

            sess.writeFrame(frame)
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensureOpen()

        Objects.checkFromIndexSize(off, len, b.size)

        val byteArray = ByteArray(len)

        System.arraycopy(b, off, byteArray, 0, len)

        this.write(byteArray)
    }

    override fun close() {
        if (logger.isDebugEnabled) {
            logger.debug("mux stream closed: " + this.id())
        }

        val frame = Frame(sess.getConfig().version.toUByte(), cmdFIN, this.id)

        this.sess.writeFrame(frame)

        this.closed = true

        this.notifyCloseEvent()
    }

    fun fin() {
        if (logger.isDebugEnabled) {
            logger.debug("mux stream closed by fin: " + this.id())
        }

        this.closed = true

        this.notifyCloseEvent()
    }

    fun closeBySession() {
        if (logger.isDebugEnabled) {
            logger.debug("mux stream closed by session: " + this.id())
        }

        this.closed = true

        this.notifyCloseEvent()
    }

    private fun notifyCloseEvent() {
        // todo close event needs to be sent
        this.sess.executor().execute {
            this.sess.streamListener().onClosed(this)
        }
    }
}