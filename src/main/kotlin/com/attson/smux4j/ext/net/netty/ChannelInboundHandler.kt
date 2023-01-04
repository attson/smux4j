package com.attson.smux4j.ext.net.netty

import com.attson.smux4j.ext.netty.context.ContextOutputStream
import com.attson.smux4j.mux.ServerHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

class ChannelInboundHandler(private val serverHandler: ServerHandler): ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        serverHandler.onAccept(ContextOutputStream(ctx))

        super.channelActive(ctx)
    }

}