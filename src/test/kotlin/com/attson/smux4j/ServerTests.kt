package com.attson.smux4j

import com.attson.smux4j.ext.formatString
import com.attson.smux4j.ext.net.netty.Server
import com.attson.smux4j.ext.net.netty.ServerConfig
import com.attson.smux4j.mux.MuxServerHandler
import com.attson.smux4j.mux.StreamIOHandler
import com.attson.smux4j.mux.defaultConfig
import com.attson.smux4j.mux.verifyConfig
import com.attson.smux4j.session.Session
import io.netty.channel.ChannelOption
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream

class ServerTests {

    @Test
    fun server() {
        val muxConfig = defaultConfig()


        muxConfig.setStreamHandler(object : StreamIOHandler {
            override fun onReadEvent(stream: Stream, input: InputStream) {
                val readAllBytes = input.readAllBytes()

                println(readAllBytes.formatString() + " " +readAllBytes.toString(Charsets.UTF_8))

                stream.write("pong".toByteArray(Charsets.UTF_8))

                stream.flush()
            }

            override fun onClosed(stream: Stream) {
                TODO("Not yet implemented")
            }
        })

        val serverConfig = ServerConfig("0.0.0.0", 3001)

        Server(serverConfig, object : MuxServerHandler {
            init {
                verifyConfig(muxConfig)
            }

            override fun onAccept(conn: OutputStream): Session {
                return Session(muxConfig, conn, false)
            }

            override fun onClosed() {
                TODO("Not yet implemented")
            }

        }) {
            it.childOption(ChannelOption.MAX_MESSAGES_PER_WRITE, muxConfig.maxFrameSize)
        }.start()
    }
}