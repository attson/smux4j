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

class Stream(private val id: Long, private val frameSize: Int, private val sess: Session) : OutputStream() {
    // id at construct property
    // sess at construct property

    private var buffers: ArrayList<ByteArray> = arrayListOf()
    // heads   [][]byte // slice heads kept for recycle

    private val bufferLock = Any()

    // frameSize at construct property

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private var die: Boolean = false


    private var recvReadable: Int = 0


    private var reading = false

    // --------------------------------- java code ---------------------------------
    init {
        if (logger.isDebugEnabled) {
            logger.debug("mux stream opened: " + this.id())
        }
    }

    // --------------------------------- java code ---------------------------------

    fun id(): String {
        return "${sess.hashCode()}-${id}"
    }

    // read()

    fun pushBytes(data: ByteArray) {
        synchronized(bufferLock) {
            buffers.add(data)
            recvReadable += data.size
        }
    }


    fun notifyReadEvent() {
        if (!reading && recvReadable > 0) {
            // Ensure that only one thread is in read

            // Single thread processing
            sess.getConfig().getStreamIOExecutor().execute {
                var input: ByteArraysInputStream
                var readable: Int
                var buffers: ArrayList<ByteArray>

                // Copy the contents in recvBuffers, and use different buffers for reading and writing
                synchronized(bufferLock) {
                    if (reading) {
                        return@execute
                    }

                    reading = true

                    // Read from buffers
                    buffers = this.buffers
                    readable = recvReadable

                    this.buffers = arrayListOf()
                    recvReadable = 0

                    input = ByteArraysInputStream(buffers, readable)
                }

                // The event processing part is unlocked
                this.sess.getConfig().getStreamHandler().onReadEvent(this, input)
                input.clearRead()

                var needRead = false

                synchronized(bufferLock) {
                    reading = false

                    if (buffers.isNotEmpty()) {
                        this.buffers.addAll(0, buffers)

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
        if (die) {
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
            if (sz > this.frameSize) {
                sz = this.frameSize
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

        this.die = true

        this.notifyCloseEvent()
    }

    fun fin() {
        if (logger.isDebugEnabled) {
            logger.debug("mux stream closed by fin: " + this.id())
        }

        this.die = true

        this.notifyCloseEvent()
    }

    fun closeBySession() {
        if (logger.isDebugEnabled) {
            logger.debug("mux stream closed by session: " + this.id())
        }

        this.die = true

        this.notifyCloseEvent()
    }

    private fun notifyCloseEvent() {
        // todo close event needs to be sent
        this.sess.getConfig().getStreamIOExecutor().execute {
            this.sess.getConfig().getStreamHandler().onClosed(this)
        }
    }
}