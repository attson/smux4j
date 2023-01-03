package com.attson.smux4j.ext.netty.handler

import com.attson.smux4j.mux.Mux
import com.attson.smux4j.session.Session
import com.attson.smux4j.ext.netty.context.ContextOutputStream
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Processing raw tcp data
 */
class SessionServerHandler(mux: Mux) : SessionClientHandler(mux) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private lateinit var session: Session

    override fun channelActive(ctx: ChannelHandlerContext) {
        this.session = mux.openSession(ctx.channel().id().asLongText())

        this.session.initConnection(ContextOutputStream(ctx)) {
            // auto read set
            ctx.channel().config().isAutoRead = true
        }

        super.channelActive(ctx)
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