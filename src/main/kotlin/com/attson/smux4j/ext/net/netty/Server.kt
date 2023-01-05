package com.attson.smux4j.ext.net.netty

import com.attson.smux4j.mux.MuxServerHandler
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.LoggerFactory

class Server(private val config: ServerConfig, private val serverHandler: MuxServerHandler, private val apply: ((boot: ServerBootstrap) -> Unit)? = null) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val serverBootstrap = ServerBootstrap()

    private lateinit var server: ChannelFuture

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
            .option(ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark(1024 * 1024, 1024 * 2 * 1024))
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.SO_SNDBUF, 1024 * 2 * 1024)
            .childOption(ChannelOption.SO_RCVBUF, 1024 * 2 * 1024)
            .childOption(ChannelOption.MAX_MESSAGES_PER_WRITE, 65535) // MAX_MESSAGES_PER_WRITE should equal muxConfig.maxFrameSize

        apply?.let { it(boot) }

        server = boot.bind(config.host, config.port).sync()

        logger.info("net server started on : ${config.host}:${config.port}")

        server.channel().closeFuture().addListener {
            serverHandler.onClosed()
        }.await()
    }
}