package com.attson.smux4j.ext.net

interface Listener {
    fun accept(): Conn

    fun close()
}