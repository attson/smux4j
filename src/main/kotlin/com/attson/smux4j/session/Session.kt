package com.attson.smux4j.session

import com.attson.smux4j.*
import com.attson.smux4j.ext.io.LittleEndianDataInputStream
import com.attson.smux4j.frame.*
import com.attson.smux4j.listener.StreamHandler
import com.attson.smux4j.mux.Config
import com.attson.smux4j.mux.Mux
import com.attson.smux4j.session.exception.GoAwayException
import com.attson.smux4j.session.exception.InvalidProtocolException
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedChannelException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class Session(private val config: Config, private val conn: OutputStream, private val client: Boolean) {

    // java ext property begin
    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(javaClass)

    private val sessionId = conn.toString()

    // java ext property end

    // conn at construct property
    // config at construct property

    private var nextStreamId: Long = 0

    private val nextStreamIDLock: Any = Any()

    private val bucket: AtomicInteger = AtomicInteger(0)
    private var bucketNotify: (() -> Unit)? = null

    private val streams: HashMap<Long, Stream> = HashMap()
    private val streamLock: Any = Any()

    private val die = AtomicBoolean(false)

    private var goAway: Int = 0


    private var initialized = AtomicBoolean(false)


    private var keepalive: ScheduledFuture<*>? = null

    private val writeLock: Any = Any()

    fun getConfig(): Config {
        return this.config
    }

    init {
        bucket.set(config.maxReceiveBuffer)
        if (client) {
            this.nextStreamId = 1
        } else {
            this.nextStreamId = 0
        }

        if (!config.keepAliveDisabled) {
            this.keepalive()
        }
    }

    fun openStream(): Stream {
        if (this.isClosed()) {
            throw ClosedChannelException()
        }

        var sid: Long

        synchronized(this.nextStreamIDLock) {
            if (this.goAway > 0) {
                throw GoAwayException()
            }

            this.nextStreamId += 2
            sid = this.nextStreamId

            if (sid == (sid % 2)) {
                this.goAway = 1
                throw GoAwayException()
            }
        }

        val stream = Stream(sid, config.maxFrameSize, this)

        this.writeFrame(Frame(this.config.version.toUByte(), cmdSYN, sid))

        synchronized(this.streamLock) {
            // read error

            // writer error

            if (this.isClosed()) {
                throw ClosedChannelException()
            }

            streams[sid] = stream
        }

        return stream
    }


    // Open returns a generic ReadWriteCloser
    fun open(): Stream {
        return this.openStream()
    }

    // AcceptStream() -> handler
    // Accept() -> handler

    fun close() {
        if (this.die.compareAndSet(false, true)) {
            if (logger.isDebugEnabled) {
                logger.debug("mux session closed " + this.id())
            }

            synchronized(this.streamLock) {
                this.streams.forEach { (t, u) ->
                    u.closeBySession()
                }
            }

            // java code
            this.keepalive?.cancel(true)
            mux.removeSession(this.id())
            // java code

            conn.close()
        } else {
            throw ClosedChannelException()
        }
    }

    // CloseChan

    private fun notifyBucket() {
        this.bucketNotify?.let { it() }
    }

    // notifyBucket
    // notifyReadError
    // notifyWriteError
    // notifyProtoError



    fun isClosed(): Boolean {
        return this.die.get()
    }

    fun numStreams(): Int {
        if (this.isClosed()) {
            return 0
        }

        return synchronized(this.streamLock) {
            this.streams.size
        }
    }

    // SetDeadline
    // LocalAddr
    // RemoteAddr

    // notify the session that a stream has closed
    fun streamClosed(sid: Long) {
        synchronized(this.streamLock) {
            // return remaining tokens to the bucket

            // todo

            this.streams.remove(sid)
        }
    }

    // returnTokens is called by stream to return token after read
    fun returnTokens(n: Int) {
        if (this.bucket.addAndGet(n) > 0) {
            notifyBucket()
        }
    }

    // recvLoop in java is onInput
    fun onInput(input: InputStream) {

        // check that the readable size is>=headerSize and the current bucket space is enough
        while (input.available() >= headerSize && this.bucket.get() > 0 && !this.isClosed()) {
            val buf = LittleEndianDataInputStream(input)

            // Mark the current location. When the package is insufficient, reset to the location and keep half the package
            buf.mark(0)

            // Ver reserves bytes and discards them
            var version = buf.readByte()
            if (version != this.config.version.toByte()) {
                throw InvalidProtocolException()
            }

            val cmd = buf.readByte().toUByte()
            val length = buf.readUnsignedShort()
            val streamId = buf.readInt().toUInt().toLong()

            when (cmd) {
                cmdNOP -> {
                    // nop
                }

                cmdSYN -> {
                    synchronized(streamLock) {
                        if (!this.streams.containsKey(streamId)) {
                            this.streams[streamId] = Stream(streamId, config.maxFrameSize, this)
                        }
                        // chAccepts <-
                        // die
                    }
                }
                cmdFIN -> {
                    synchronized(streamLock) {
                        val stream = this.streams[streamId]

                        if (stream != null) {
                            stream.fin()
                            stream.notifyReadEvent()
                            // todo dot need remove ?
                            this.streams.remove(streamId)
                        }
                    }
                }
                cmdPSH -> {
                    if (length > 0) {
                        if (buf.available() >= length) {
                            val bytes = buf.readNBytes(length)
                            synchronized(streamLock) {
                                val stream = this.streams[streamId]

                                if (stream != null) {
                                    stream.pushBytes(bytes)
                                    this.bucket.addAndGet(-bytes.size)
                                    stream.notifyReadEvent()
                                }
                            }
                        } else {
                            buf.reset()
                            return
                        }
                    }
                }
                cmdUPD -> {
                    if (buf.available() >= szCmdUPD) {
                        val consumed = buf.readInt().toUInt().toLong()
                        val window = buf.readInt().toUInt().toLong()

                        // todo Not yet implemented
                        logger.warn("upd")
                    } else {
                        buf.reset()
                        return
                    }
                }
                else -> {
                    throw InvalidProtocolException()
                }
            }
        }
    }

    private fun keepalive() {
        val frame = Frame(this.config.version.toUByte(), cmdNOP, 0)
        this.keepalive = scheduled.scheduleAtFixedRate({
            writeFrame(frame)
            flush()
        }, config.keepAliveInterval, config.keepAliveInterval, TimeUnit.MILLISECONDS)
    }

    // shaperLoop
    // sendLoop

    fun id(): String {
        return sessionId
    }

    fun isAcceptRead(): Boolean {
        return this.bucket.get() > 0
    }

    fun writeFrame(frame: Frame) {
        if (logger.isDebugEnabled) {
            logger.debug("session ${id()} write frame $frame")
        }
        // Because there is only one conn, but there are multiple streams. When writing, you need to lock to ensure that the IO will not conflict
        synchronized(writeLock) {
            val allocate = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

            allocate.put(0)
            allocate.put(frame.getCmd().toByte())
            allocate.putShort(frame.getData().size.toShort())
            allocate.putInt(frame.getSid().toInt())

            conn.write(allocate.array())
            conn.write(frame.getData())
        }
    }

    fun flush() {
        if (logger.isDebugEnabled) {
            logger.debug("session ${id()} flushed")
        }
        conn.flush()
    }

    companion object {
        val logger: org.slf4j.Logger = LoggerFactory.getLogger(Session::class.java)

        val scheduled: ScheduledExecutorService = Executors.newScheduledThreadPool(10, object : ThreadFactory {
            private val group: ThreadGroup

            private val threadNumber = AtomicInteger(0)

            private val namePrefix: String = "smux.session.scheduled-"

            init {
                val s = System.getSecurityManager()
                group = if (s != null) s.threadGroup else Thread.currentThread().threadGroup
            }

            override fun newThread(r: Runnable): Thread {
                val name = namePrefix + threadNumber.incrementAndGet()
                logger.info("new scheduled thread $name")
                return Thread(group, r, name)
            }
        })

    }

    fun executor(): ThreadPoolExecutor {
        return mux.executor()
    }

    fun streamListener(): StreamHandler {
        return mux.streamListener()
    }
}