package com.attson.smux4j.session

import com.attson.smux4j.*
import com.attson.smux4j.ext.io.LittleEndianDataInputStream
import com.attson.smux4j.frame.*
import com.attson.smux4j.mux.StreamIOHandler
import com.attson.smux4j.mux.Config
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

class Session(
    private val config: Config,
    private val conn: OutputStream,
    client: Boolean
) {

    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(javaClass)

    // conn at construct property
    // config at construct property

    private var nextStreamId: Long = 0
    private val nextStreamIDLock: Any = Any()

    private val bucket: AtomicInteger = AtomicInteger(0)
    private var bucketNotify: (() -> Unit)? = null

    private val streams: HashMap<Long, Stream> = HashMap()
    private val streamLock: Any = Any()

    private val die = AtomicBoolean(false)
    // dieOnce sync.Once -> use atomic cas in java

    // socketReadError      atomic.Value
    // socketWriteError     atomic.Value
    // chSocketReadError    chan struct{}
    // chSocketWriteError   chan struct{}
    // socketReadErrorOnce  sync.Once
    // socketWriteErrorOnce sync.Once

    // protoError     atomic.Value
    // chProtoError   chan struct{}
    // protoErrorOnce sync.Once

    // chAccepts chan *Stream

    private val dataReady = AtomicInteger(0) // flag data has arrived
    private var goAway: Int = 0 // flag id exhausted

    // deadline atomic.Value

    // shaper chan writeRequest
    // writes chan writeRequest

    // --------------------------------- java code ---------------------------------
    private val writeLock: Any = Any()

    private var keepalive: ScheduledFuture<*>? = null
    private var keepaliveTimeout: ScheduledFuture<*>? = null

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

    fun getConfig(): Config {
        return this.config
    }

    fun setBucketNotify(bucketNotify: () -> Unit): Session {
        this.bucketNotify = bucketNotify

        return this
    }

    fun bucketIsAvailable(): Boolean {
        return this.bucket.get() > 0
    }

    fun flush() {
        if (logger.isDebugEnabled) {
            logger.debug("session ${hashCode()} flushed")
        }
        conn.flush()
    }

    // --------------------------------- java code ---------------------------------

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
                logger.debug("mux session closed " + this.hashCode())
            }

            synchronized(this.streamLock) {
                this.streams.forEach { (t, u) ->
                    u.closeBySession()
                }
            }

            // --------------------------------- java code ---------------------------------
            this.keepalive?.cancel(true)
            this.keepaliveTimeout?.cancel(true)
            // --------------------------------- java code ---------------------------------

            conn.close()
        } else {
            throw ClosedChannelException()
        }
    }

    // CloseChan [Not yet implemented]

    // notifyBucket notifies recvLoop that bucket is available
    private fun notifyBucket() {
        this.bucketNotify?.let { it() }
    }

    // notifyBucket [Not yet implemented]

    // notifyReadError [Not yet implemented]

    // notifyWriteError [Not yet implemented]

    // notifyProtoError [Not yet implemented]

    // IsClosed does a safe check to see if we have shutdown
    fun isClosed(): Boolean {
        return this.die.get()
    }

    // NumStreams returns the number of currently open streams
    fun numStreams(): Int {
        if (this.isClosed()) {
            return 0
        }

        return synchronized(this.streamLock) {
            this.streams.size
        }
    }

    // SetDeadline(t time.Time) [Not yet implemented]

    // LocalAddr() net.Addr [Not yet implemented]

    // RemoteAddr() net.Addr [Not yet implemented]

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

    // recvLoop keeps on reading from underlying connection if tokens are available
    // recvLoop in java is onInput, net server notify session to read
    fun recv(input: InputStream) {

        // check that the readable size is>=headerSize and the current bucket space is enough
        while (input.available() >= headerSize && this.bucketIsAvailable() && !this.isClosed()) {
            this.dataReady.set(1)

            val buf = LittleEndianDataInputStream(input)

            // Mark the current location. When the package is insufficient, reset to the location and keep half the package
            buf.mark(0)

            // Ver reserves bytes and discards them
            val version = buf.readByte()
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
        this.keepalive = this.config.getKeepAliveScheduled().scheduleAtFixedRate({
            this.writeFrame(frame)
            this.flush()

            this.notifyBucket() // force a signal to the recvLoop
        }, config.keepAliveInterval, config.keepAliveInterval, TimeUnit.MILLISECONDS)

        this.keepaliveTimeout = this.config.getKeepAliveScheduled().scheduleAtFixedRate({
            if (!this.dataReady.compareAndSet(1, 0)) {
                // recvLoop may block while bucket is 0, in this case,
                // session should not be closed.
                if (this.bucket.get() > 0) {
                    this.close()
                }
            }
        }, config.keepAliveTimeout, config.keepAliveTimeout, TimeUnit.MILLISECONDS)
    }

    // shaperLoop
    // sendLoop

    fun writeFrame(frame: Frame) {
        if (logger.isDebugEnabled) {
            logger.debug("session ${hashCode()} write frame $frame")
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

    // writeFrameInternal todo prio [Not yet implemented]
}