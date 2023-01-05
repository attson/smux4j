package com.attson.smux4j.mux

import com.attson.smux4j.session.Session
import java.io.OutputStream

/**
 * net server to trigger mux server handler
 */
interface MuxServerHandler {
    fun onAccept(conn: OutputStream): Session

    fun onClosed()
}