package com.attson.smux4j.ext.net.netty

import io.netty.channel.EventLoopGroup

class ServerConfig(val host: String, val port: Int) {

    var threadCount: Int = 50

    var threadGroup: EventLoopGroup? = null
}