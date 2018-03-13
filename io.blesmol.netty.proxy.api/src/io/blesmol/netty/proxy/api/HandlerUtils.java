package io.blesmol.netty.proxy.api;

import java.util.Optional;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;

//TODO: move to general netty project
public interface HandlerUtils {

	void closeOnFlush(Channel ch);

	String channelId(ChannelHandlerContext ctx);

	void closeFutureCtx(Future<ChannelHandlerContext> futureCtx);

	/**
	 * Set auto read to specified value on future or if not successful, close the
	 * closable if present.
	 */
	void setAutoRead(boolean autoRead, ChannelFuture future, Optional<Channel> closable);

}
