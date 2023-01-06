package com.attson.smux4j.mux

import com.attson.smux4j.Stream
import com.attson.smux4j.session.exception.InvalidProtocolException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.concurrent.*
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

    // --------------------------------- java code ---------------------------------
    val logger: Logger = LoggerFactory.getLogger(javaClass)

    var keepAliveThreadCount: Int = 0
    var streamIOExecutorConfig: StreamIOExecutorConfig? = null
    private var streamHandler: StreamIOHandler? = null

    class StreamIOExecutorConfig(
        var corePoolSize: Int,
        var maximumPoolSize: Int,
        var keepAliveTime: Long,
        var workQueueCount: Int,
        var rejectedExecutionHandler: RejectedExecutionHandler
    )

    private var keepAliveExecutor: ScheduledExecutorService? = null

    private var streamIOExecutor: ExecutorService? = null

    fun setKeepAliveScheduled(keepAliveScheduled: ScheduledExecutorService): Config {
        this.keepAliveExecutor = keepAliveScheduled

        return this
    }

    fun getKeepAliveScheduled(): ScheduledExecutorService {
        if (keepAliveExecutor == null) {
            synchronized(this) {
                if (keepAliveExecutor == null) {
                    this.keepAliveExecutor = Executors.newScheduledThreadPool(this.keepAliveThreadCount, object : ThreadFactory {
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
            }
        }

        return this.keepAliveExecutor!!
    }

    fun setStreamIOReadExecutor(streamIOReadExecutor: ExecutorService): Config {
        this.streamIOExecutor = streamIOReadExecutor

        return this
    }
    fun getStreamIOExecutor(): ExecutorService {
        if (streamIOExecutor == null) {
            synchronized(this) {
                if (streamIOExecutor == null) {
                    if (this.streamIOExecutorConfig == null) {
                        this.streamIOExecutor = Executors.newFixedThreadPool(1)
                    } else {
                        val that = this

                        this.streamIOExecutor = ThreadPoolExecutor(
                            this.streamIOExecutorConfig!!.corePoolSize,
                            this.streamIOExecutorConfig!!.maximumPoolSize,
                            this.streamIOExecutorConfig!!.keepAliveTime,
                            TimeUnit.MILLISECONDS,
                            ArrayBlockingQueue(this.streamIOExecutorConfig!!.workQueueCount),
                            object : ThreadFactory {
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
                            rejectedExecutionHandler = that.streamIOExecutorConfig!!.rejectedExecutionHandler
                        }
                    }
                }
            }
        }

        return this.streamIOExecutor!!
    }

    fun getStreamHandler(): StreamIOHandler {
        if (streamHandler == null) {
            synchronized(this) {
                if (streamHandler == null) {
                    this.streamHandler = object : StreamIOHandler {
                        override fun onReadEvent(stream: Stream, input: InputStream) {
                            TODO("Not yet implemented")
                        }

                        override fun onClosed(stream: Stream) {
                            TODO("Not yet implemented")
                        }

                        override fun onFin(stream: Stream) {
                            TODO("Not yet implemented")
                        }
                    }
                }
            }
        }

        return this.streamHandler!!
    }

    fun setStreamHandler(streamHandler: StreamIOHandler): StreamIOHandler {
        this.streamHandler = streamHandler

        return streamHandler
    }

    // --------------------------------- java code ---------------------------------
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

    // --------------------- java share thread pool config begin ---------------------

    config.keepAliveThreadCount = 10
    config.streamIOExecutorConfig = Config.StreamIOExecutorConfig(
        8,
        50,
        60000,
        500,
        ThreadPoolExecutor.DiscardPolicy()
    )

    // --------------------- java share thread pool config end ---------------------

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