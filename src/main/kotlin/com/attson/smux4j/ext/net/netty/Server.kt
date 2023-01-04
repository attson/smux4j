package com.attson.smux4j.ext.net.netty

import com.attson.smux4j.ext.net.Conn
import com.attson.smux4j.mux.ServerHandler
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import java.util.concurrent.LinkedBlockingDeque

class Server(private val config: ServerConfig, private val serverHandler: ServerHandler) {

    private val serverBootstrap = io.netty.bootstrap.ServerBootstrap()

    private val connQueue = LinkedBlockingDeque<Conn>()

    private var server: ChannelFuture? = null

    fun start() {

        var group = config.threadGroup

        if (group == null) {
            group = NioEventLoopGroup(config.threadCount)
        }

        val boot = serverBootstrap.group(group).channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(ChannelInboundHandler(serverHandler))
                }
            })
            .childOption(ChannelOption.SO_SNDBUF, 1024 * 2 * 1024)
            .childOption(ChannelOption.SO_RCVBUF, 1024 * 2 * 1024)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
//            .childOption(ChannelOption.MAX_MESSAGES_PER_WRITE, mux.config().maxFrameSize)
            .option(ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark(1024 * 1024, 1024 * 2 * 1024))

        server = boot.bind(config.host, config.port).sync()

        boot.bind("0.0.0.0", 3661)
            .sync()
            .channel()
            .closeFuture()
            .await()
    }
}