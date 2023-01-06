package com.attson.smux4j.mux

import com.attson.smux4j.Stream
import java.io.InputStream

/**
 * one stream io handle, the bytes of input stream is decoded by mux protocol
 */
interface StreamIOHandler {
    fun onReadEvent(stream: Stream, input: InputStream)

    fun onClosed(stream: Stream)

    fun onFin(stream: Stream)
}