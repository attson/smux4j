package com.attson.smux4j

import com.attson.smux4j.listener.StreamListener
import com.attson.smux4j.ext.formatString
import com.attson.smux4j.mux.Mux
import com.attson.smux4j.mux.defaultSmuxConfig
import com.attson.smux4j.ext.netty.handler.SessionServerHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.net.BindException

class ServerTests {

    @Test
    fun `netty server test`() {
        val serverBootstrap = io.netty.bootstrap.ServerBootstrap()

        try {
            val defaultSmuxConfig = defaultSmuxConfig()
            val mux = Mux(defaultSmuxConfig).setStreamListener(object : StreamListener {
                override fun onReadEvent(stream: Stream, input: InputStream) {
                    val readAllBytes = input.readAllBytes()

                    println(readAllBytes.formatString())

                    stream.write(readAllBytes)

                    stream.flush()
                }

                override fun onClosed(stream: Stream) {
                    TODO("Not yet implemented")
                }
            })

            val boot = serverBootstrap.group(NioEventLoopGroup(50)).channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(SessionServerHandler(mux))
                    }
                }).childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_SNDBUF, 1024 * 2 * 1024)
                .childOption(ChannelOption.SO_RCVBUF, 1024 * 2 * 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.MAX_MESSAGES_PER_WRITE, mux.config().maxFrameSize)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark(1024 * 1024, 1024 * 2 * 1024))

            boot.bind("0.0.0.0", 3661)
                .sync()
                .channel()
                .closeFuture()
                .await()

        } catch (e: BindException) {
            e.printStackTrace()
            throw e
        }
    }
}