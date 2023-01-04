package com.attson.smux4j.mux

import com.attson.smux4j.session.Session
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class SmuxServerHandler(private var config: Config? = null) : ServerHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val sessions: ConcurrentHashMap<String, Session> = ConcurrentHashMap()

    init {
        if (config == null) {
            config = defaultConfig()
        }

        verifyConfig(config!!)
    }

    private fun openSession(outputStream: OutputStream): Session {
        val session = Session(config!!, outputStream, false)

        if (this.sessions.contains(session.id())) {
            logger.warn("mux session id exists")
        }

        this.sessions[session.id()] = session

        return session
    }

    override fun onAccept(outputStream: OutputStream) {
        this.openSession(outputStream)
    }

    override fun onRead() {
    }

    override fun onClosed() {
    }
}