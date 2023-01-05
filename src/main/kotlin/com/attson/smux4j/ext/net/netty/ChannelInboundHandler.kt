package com.attson.smux4j.ext.net.netty

import com.attson.smux4j.mux.MuxServerHandler
import com.attson.smux4j.session.Session
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.DecoderException
import org.slf4j.LoggerFactory

class ChannelInboundHandler(private val serverHandler: MuxServerHandler): ChannelInboundHandlerAdapter() {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val cumulator: ByteToMessageDecoder.Cumulator = ByteToMessageDecoder.MERGE_CUMULATOR

    private var cumulation: ByteBuf? = null

    private lateinit var session: Session;

    override fun channelActive(ctx: ChannelHandlerContext) {
        session = serverHandler.onAccept(ContextOutputStream(ctx)).apply {
            setBucketNotify {
                ctx.channel().config().isAutoRead = true
            }
        }

        super.channelActive(ctx)
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

                // todo session closed
                if (session.bucketIsAvailable()) {
                    session.recv(ByteBufInputStream(cumulation))
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

    override fun channelInactive(ctx: ChannelHandlerContext) {
        this.session.close()

        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("session handler got exception: " + cause.message)

        this.session.close()
    }
}