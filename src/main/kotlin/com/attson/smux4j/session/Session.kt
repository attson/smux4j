package com.attson.smux4j.session

import com.attson.smux4j.*
import com.attson.smux4j.ext.io.LittleEndianDataInputStream
import com.attson.smux4j.frame.*
import com.attson.smux4j.listener.StreamListener
import com.attson.smux4j.mux.Config
import com.attson.smux4j.mux.Mux
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class Session(private val sessionId: String, private val mux: Mux) {
    private var closed = AtomicBoolean(false)

    private var initialized = AtomicBoolean(false)

    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(javaClass)

    private val streams: HashMap<Long, Stream> = HashMap()

    private val streamLock: Any = Any()

    private val bucket: AtomicInteger = AtomicInteger(0)

    private val config = mux.config()

    private lateinit var outputStream: OutputStream

    private lateinit var acceptRead: () -> Unit

    private var nextStreamId: AtomicLong = AtomicLong(1)

    private var keepalive: ScheduledFuture<*>? = null

    private val writeLock: Any = Any()

    fun getConfig(): Config {
        return this.config
    }

    init {
        bucket.set(config.maxReceiveBuffer)
    }

    fun returnTokens(n: Int) {
        this.bucket.addAndGet(n)

        // notify
        acceptRead()
    }

    fun id(): String {
        return sessionId
    }

    fun close() {
        if (this.closed.compareAndSet(false, true)) {
            if (logger.isDebugEnabled) {
                logger.debug("mux session closed " + this.id())
            }

            this.keepalive?.cancel(true)

            this.streams.forEach { (t, u) ->
                u.closeBySession()
            }

            mux.removeSession(this.id())
            outputStream.close()
        } else {
            logger.warn("session ${id()} is closed.")
        }
    }

    fun openStream(): Stream {
        val streamId = nextStreamId.addAndGet(2)
        var stream = streams[streamId]

        if (stream != null) {
            logger.warn("session ${id()} open exists stream $streamId")
            return stream
        }

        stream = Stream(streamId, config.maxFrameSize, this)
        streams[streamId] = stream

        this.writeFrame(Frame(this.config.version.toUByte(), cmdSYN, streamId))
        return stream
    }

    fun initConnection(outputStream: OutputStream, acceptRead: () -> Unit): Session {
        if (initialized.compareAndSet(false, true)) {
            this.outputStream = outputStream
            this.acceptRead = acceptRead

            if (!config.keepAliveDisabled) {
                val frame = Frame(this.config.version.toUByte(), cmdNOP, 0)
                this.keepalive = scheduled.scheduleAtFixedRate({
                    writeFrame(frame)
                    flush()
                }, config.keepAliveInterval, config.keepAliveInterval, TimeUnit.MILLISECONDS)
            }
        } else {
            logger.warn("session ${id()} is initialized.")
        }

        return this
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

            outputStream.write(allocate.array())
            outputStream.write(frame.getData())
        }
    }

    fun flush() {
        if (logger.isDebugEnabled) {
            logger.debug("session ${id()} flushed")
        }
        outputStream.flush()
    }

    fun read(input: InputStream) {
        // check that the readable size is>=headerSize and the current bucket space is enough
        while (input.available() >= headerSize && this.isAcceptRead()) {
            val buf = LittleEndianDataInputStream(input)

            // Mark the current location. When the package is insufficient, reset to the location and keep half the package
            buf.mark(0)

            // Ver reserves bytes and discards them
            buf.readByte()
            val cmd = buf.readByte().toUByte()
            val length = buf.readUnsignedShort()
            val streamId = buf.readInt().toUInt().toLong()

            when (cmd) {
                cmdPSH -> {
                    if (length > 0) {
                        if (buf.available() >= length) {
                            val bytes = buf.readNBytes(length)
                            synchronized(streamLock) {
                                if (this.streams.containsKey(streamId)) {
                                    this.bucket.addAndGet(-bytes.size)
                                    this.streams[streamId]!!.pushBytes(bytes)
                                }
                            }
                        } else {
                            buf.reset()
                            return
                        }
                    }
                }
                cmdSYN -> {
                    synchronized(streamLock) {
                        if (!this.streams.containsKey(streamId)) {
                            this.streams[streamId] = Stream(streamId, config.maxFrameSize, this)
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
                cmdNOP -> {
                    // nop
                }
                cmdFIN -> {
                    synchronized(streamLock) {
                        if (this.streams.containsKey(streamId)) {
                            this.streams[streamId]!!.closeByFin()
                            this.streams.remove(streamId)
                        }
                    }
                }
                else -> {
                    logger.warn("err cmd")
                    buf.skipBytes(buf.available())
                    return
                }
            }
        }
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

    fun streamListener(): StreamListener {
        return mux.streamListener()
    }
}