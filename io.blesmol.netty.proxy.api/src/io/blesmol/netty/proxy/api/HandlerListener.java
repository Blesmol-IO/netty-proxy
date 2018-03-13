package io.blesmol.netty.proxy.api;

import io.netty.channel.ChannelHandlerContext;

// FIXME: move to generic netty project
public interface HandlerListener {

	void onAdded(ChannelHandlerContext handlerCtx);

	void onRemoved(ChannelHandlerContext handlerCtx);
}
