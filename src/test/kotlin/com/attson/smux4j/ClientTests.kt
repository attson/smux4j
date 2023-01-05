package com.attson.smux4j

import org.junit.jupiter.api.Test

class ClientTests {

    @Test
    fun `test client`() {
//        val defaultSmuxConfig = defaultConfig()
//        val mux = Mux(defaultSmuxConfig).setStreamListener(object : StreamIOHandler {
//            override fun onReadEvent(stream: Stream, input: InputStream) {
//                val readAllBytes = input.readAllBytes()
//
//                println(readAllBytes.formatString())
//            }
//
//            override fun onClosed(stream: Stream) {
//                TODO("Not yet implemented")
//            }
//        })
//
//        val boot = Bootstrap().group(NioEventLoopGroup(50)).channel(NioSocketChannel::class.java)
//            .handler(object : ChannelInitializer<SocketChannel>() {
//                override fun initChannel(ch: SocketChannel) {
//                    ch.pipeline().addLast(SessionClientHandler(mux))
//                }
//            })
//            .option(ChannelOption.TCP_NODELAY, true)
//            .option(ChannelOption.SO_SNDBUF, 1024 * 2 * 1024)
//            .option(ChannelOption.SO_RCVBUF, 1024 * 2 * 1024)
//            .option(ChannelOption.SO_KEEPALIVE, true)
//            .option(ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark(1024 * 1024, 1024 * 2 * 1024))
//            .option(ChannelOption.MAX_MESSAGES_PER_WRITE, mux.config().maxFrameSize)
//
//        Thread {
//            val ch = boot.connect("127.0.0.1", 3662).sync().channel()
//
//            val session = mux.openSession(ch.id().asLongText())
//
//            session.initConnection(ChannelOutputStream(ch)) {
//                ch.config().isAutoRead = true
//            }
//
//            val openStream = session.openStream()
//
//            val byteArray = ByteArray(255)
//            for (i in 0 until 255) {
//                byteArray[i] = i.toByte()
//            }
//
//            for (i in 0 until 10) {
//                openStream.write(byteArray)
//                openStream.flush()
//                Thread.sleep(2000)
//            }
//
//            openStream.close()
//
//            ch.closeFuture().sync().await()
//        }.start()
//
//        val ch = boot.connect("127.0.0.1", 3661).sync().channel()
//
//        val session = mux.openSession(ch.id().asLongText())
//
//        session.initConnection(ChannelOutputStream(ch)) {
//            ch.config().isAutoRead = true
//        }
//
//        val openStream = session.openStream()
//
//        val byteArray = ByteArray(255)
//        for (i in 0 until 255) {
//            byteArray[i] = i.toByte()
//        }
//
//        for (i in 0 until 10) {
//            openStream.write(byteArray)
//            openStream.flush()
//            Thread.sleep(2000)
//        }
//
//        openStream.close()
//
//        ch.closeFuture().sync().await()
    }
}