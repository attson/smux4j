package com.attson.smux4j.ext.netty.handler

import com.attson.smux4j.mux.Mux
import com.attson.smux4j.session.Session
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.DecoderException

open class SessionClientHandler(protected val mux: Mux) : ChannelInboundHandlerAdapter() {
    private val cumulator: ByteToMessageDecoder.Cumulator = ByteToMessageDecoder.MERGE_CUMULATOR

    private var cumulation: ByteBuf? = null

    private var session: Session? = null

    private fun getSession(ctx: ChannelHandlerContext): Session {
        if (session == null) {
            session = mux.getSession(ctx.channel().id().asLongText())
            if (session != null) {
                return session!!
            } else {
                throw RuntimeException("mux get session fail. maybe session is closed")
            }
        }

        return session!!
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is ByteBuf) {
            /**
             * The channelRead feature of netty: byteBuf is not reserved even if it has not been read completely. Therefore,
             * when the package is incomplete, it is necessary to reserve parts. The solution is similar to ByteToMessageDecoder
             *
             * Since Netty is enabled by autoRead by default, that is, as long as there is a byte in,
             *
             * the channelRead event will be triggered, which will result in the constant incoming of read content,
             * and the user layer buffer limit cannot be implemented,
             *
             * Therefore, you need to manually control the autoRead parameter to avoid byte accumulation
             */

            try {
                val first = cumulation == null
                cumulation = cumulator.cumulate(
                    ctx.alloc(),
                    if (first) Unpooled.EMPTY_BUFFER else cumulation, msg
                )

                val session = this.getSession(ctx)
                if (session.isAcceptRead()) {
                    session.onInput(ByteBufInputStream(cumulation))
                } else {
                    ctx.channel().config().isAutoRead = false
                }
            } catch (e: DecoderException) {
                throw e
            } catch (e: Exception) {
                throw DecoderException(e)
            } finally {
                cumulation?.let {
                    if (!it.isReadable) {
                        it.release()
                        cumulation = null
                    }
                }
            }
        } else {
            ctx.fireChannelRead(msg)
        }
    }
}