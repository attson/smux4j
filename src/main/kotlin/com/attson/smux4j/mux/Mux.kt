package com.attson.smux4j.mux

import com.attson.smux4j.Stream
import com.attson.smux4j.listener.StreamHandler
import com.attson.smux4j.session.Session
import com.attson.smux4j.session.exception.InvalidProtocolException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.lang.RuntimeException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class Config {
    // SMUX Protocol version, support 1,2
    var version: Int = 0

    // Disabled keepalive
    var keepAliveDisabled: Boolean = false

    // KeepAliveInterval is how often to send a NOP command to the remote
    var keepAliveInterval: Long = 0

    // KeepAliveTimeout is how long the session
    // will be closed if no data has arrived
    var keepAliveTimeout: Long = 0

    // MaxFrameSize is used to control the maximum
    // frame size to sent to the remote
    var maxFrameSize: Int = 0

    // MaxReceiveBuffer is used to control the maximum
    // number of data in the buffer pool
    var maxReceiveBuffer: Int = 0

    // MaxStreamBuffer is used to control the maximum
    // number of data per stream
    var maxStreamBuffer: Int = 0

}

// DefaultConfig is used to return a default configuration
fun defaultConfig(): Config {
    val config = Config()
    config.version = 1
    config.keepAliveInterval = 10 * 1000
    config.keepAliveTimeout = 30 * 1000
    config.maxFrameSize = 32768
    config.maxReceiveBuffer = 4194304
    config.maxStreamBuffer = 65536
    return config
}

fun verifyConfig(config: Config) {
    if (!(config.version == 1 || config.version == 2)) {
        throw InvalidProtocolException()
    }

    if (!config.keepAliveDisabled) {
        if (config.keepAliveInterval == 0.toLong()) {
            throw RuntimeException("keep-alive interval must be positive")
        }
        if (config.keepAliveTimeout < config.keepAliveInterval) {
            throw RuntimeException("keep-alive timeout must be larger than keep-alive interval")
        }
    }
    if (config.maxFrameSize <= 0) {
        throw RuntimeException("max frame size must be positive")
    }
    if (config.maxFrameSize > 65535) {
        throw RuntimeException("max frame size must not be larger than 65535")
    }
    if (config.maxReceiveBuffer <= 0) {
        throw RuntimeException("max receive buffer must be positive")
    }
    if (config.maxStreamBuffer <= 0) {
        throw RuntimeException("max stream buffer must be positive")
    }
    if (config.maxStreamBuffer > config.maxReceiveBuffer) {
        throw RuntimeException("max stream buffer must not be larger than max receive buffer")
    }
    if (config.maxStreamBuffer > Int.MAX_VALUE) {
        throw RuntimeException("max stream buffer cannot be larger than 2147483647")
    }
}

class Mux(private val config: Config) {
    private val sessions: ConcurrentHashMap<String, Session> = ConcurrentHashMap()

    private var streamListener: StreamHandler = object : StreamHandler {
        override fun onReadEvent(stream: Stream, input: InputStream) {
            TODO("Not yet implemented")
        }

        override fun onClosed(stream: Stream) {
            TODO("Not yet implemented")
        }

    }

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    init {
        verifyConfig(config)
    }

    private val executor = ThreadPoolExecutor(
        8, 50, 60000, TimeUnit.MILLISECONDS, ArrayBlockingQueue(500), object : ThreadFactory {
            private val group: ThreadGroup

            private val threadNumber = AtomicInteger(0)

            private val namePrefix: String = "session-"

            init {
                val s = System.getSecurityManager()
                group = if (s != null) s.threadGroup else Thread.currentThread().threadGroup
            }

            override fun newThread(r: Runnable): Thread {
                val name = namePrefix + threadNumber.incrementAndGet()
                logger.info("thread pool scale up $name")
                return Thread(group, r, name)
            }
        }
    ).apply {
        rejectedExecutionHandler = ThreadPoolExecutor.DiscardPolicy()
    }

    fun executor(): ThreadPoolExecutor {
        return executor
    }

    fun config(): Config {
        return this.config
    }

    fun openSession(sessionId: String): Session {
        val session = Session(sessionId, this)

        if (this.sessions.contains(sessionId)) {
            logger.warn("mux session id exists")
        }

        this.sessions[sessionId] = session

        return session
    }

    fun getSession(sessionId: String): Session? {
        return this.sessions[sessionId]
    }

    fun removeSession(sessionId: String) {
        this.sessions.remove(sessionId)
    }

    fun setStreamListener(streamListener: StreamHandler): Mux {
        this.streamListener = streamListener

        return this
    }

    fun streamListener(): StreamHandler {
        return streamListener
    }
}