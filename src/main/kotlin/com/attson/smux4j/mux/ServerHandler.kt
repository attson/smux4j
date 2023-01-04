package com.attson.smux4j.mux

import java.io.OutputStream

interface ServerHandler {
    fun onAccept(outputStream: OutputStream)

    fun onRead()

    fun onClosed()
}