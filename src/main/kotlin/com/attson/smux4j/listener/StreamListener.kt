package com.attson.smux4j.listener

import com.attson.smux4j.Stream
import java.io.InputStream

interface StreamListener {
    fun onReadEvent(stream: Stream, input: InputStream)

    fun onClosed(stream: Stream)
}