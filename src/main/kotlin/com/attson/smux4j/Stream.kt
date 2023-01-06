package com.attson.smux4j

import com.attson.smux4j.ext.io.ByteArraysInputStream
import com.attson.smux4j.frame.Frame
import com.attson.smux4j.frame.cmdFIN
import com.attson.smux4j.frame.cmdPSH
import com.attson.smux4j.session.Session
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.nio.channels.ClosedChannelException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class Stream(private val id: Long, private val frameSize: Int, private val sess: Session) : OutputStream() {
    // -> id at construct property
    // -> sess at construct property

    private var buffers: ArrayList<ByteArray> = arrayListOf()
    // -> heads   [][]byte // slice heads kept for recycle

    private val bufferLock = Any()

    // -> frameSize at construct property

    // flag the stream has closed
    private var die = AtomicBoolean(false)
    // -> dieOnce sync.Once -> use atomic cas in java

    // FIN command
    private var chFinEvent = AtomicBoolean(false)
    // -> finEventOnce sync.Once -> use atomic cas in java

    private var recvReadable: Int = 0


    private var reading = false

    // --------------------------------- java code ---------------------------------

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    init {
        if (logger.isDebugEnabled) {
            logger.debug("mux stream opened: " + this.idStr())
        }
    }

    private fun notifyCloseEvent() {
        this.sess.getConfig().getStreamIOExecutor().execute {
            this.sess.getConfig().getStreamHandler().onClosed(this)
        }
    }

    private fun notifyFinEvent() {
        this.sess.getConfig().getStreamIOExecutor().execute {
            this.sess.getConfig().getStreamHandler().onFin(this)
        }
    }

    private fun idStr(): String {
        return "${sess.hashCode()}-${id}"
    }

    override fun write(b: Int) {
        this.write(byteArrayOf(b.toByte()))
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        Objects.checkFromIndexSize(off, len, b.size)

        val byteArray = ByteArray(len)

        System.arraycopy(b, off, byteArray, 0, len)

        this.write(byteArray)
    }

    override fun flush() {
        sess.flush()
    }

    // --------------------------------- java code ---------------------------------

    fun id(): Long {
        return id
    }

    // -> Read()
    // -> tryRead()
    // -> tryReadv2()
    // -> WriteTo()
    // -> writeTov2
    // -> sendWindowUpdate()
    // -> waitRead()


    // Write implements outputStream
    //
    // Note that the behavior when multiple goroutines write concurrently is not deterministic,
    // frames may interleave in random way.
    override fun write(b: ByteArray) {
        if (logger.isDebugEnabled) {
            logger.debug("stream ${idStr()} write bytes " + b.size)
        }

        if (die.get()) {
            logger.error("stream ${idStr()} closed")

            throw ClosedChannelException()
        }

        var off = 0

        val frame = Frame(sess.getConfig().version.toUByte(), cmdPSH, this.id)

        while (b.size - off > 0) {
            var sz = b.size - off
            if (sz > this.frameSize) {
                sz = this.frameSize
            }

            val byteArray = ByteArray(sz)

            System.arraycopy(b, off, byteArray, 0, sz)
            off += sz

            frame.setData(byteArray)

            sess.writeFrame(frame)
        }
    }

    // -> writeV2()
    override fun close() {
        if (this.die.compareAndSet(false, true)) {
            if (logger.isDebugEnabled) {
                logger.debug("mux stream closed: " + this.idStr())
            }

            this.sess.writeFrame(Frame(sess.getConfig().version.toUByte(), cmdFIN, this.id))
            this.sess.streamClosed(this.id)

            // java need to notify listener
            this.notifyCloseEvent()
        } else {
            throw ClosedChannelException()
        }
    }

    // -> GetDieCh()
    // -> SetReadDeadline()
    // -> SetWriteDeadline()
    // -> SetDeadline()

    fun sessionClose() {
        if (this.die.compareAndSet(false, true)) {
            if (logger.isDebugEnabled) {
                logger.debug("mux stream closed by session: " + this.idStr())
            }

            this.notifyCloseEvent()
        }
    }

    // -> LocalAddr()
    // -> RemoteAddr()

    fun pushBytes(data: ByteArray) {
        synchronized(bufferLock) {
            buffers.add(data)
            recvReadable += data.size
        }
    }

    // recycleTokens transform remaining bytes to tokens(will truncate buffer)
    fun recycleTokens(): Int {
        var n: Int

        synchronized(bufferLock) {
            n = recvReadable

            this.buffers = arrayListOf()

            recvReadable = 0
        }

        return n
    }

    // notify read event
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

    // -> update()

    fun fin() {
        if (this.chFinEvent.compareAndSet(false, true)) {
            if (logger.isDebugEnabled) {
                logger.debug("mux stream closed by fin: " + this.idStr())
            }

            this.notifyCloseEvent()
        }
    }
}