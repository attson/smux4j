package com.attson.smux4j.mux

import com.attson.smux4j.session.Session
import java.io.OutputStream

interface MuxServerHandler {
    fun onAccept(conn: OutputStream): Session

    fun onClosed()
}