package io.blesmol.netty.proxy.handler;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.blesmol.netty.proxy.api.HandlerUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

// TODO: move to general netty project
public class HandlerUtilsImpl implements HandlerUtils {

	protected static final Logger logger = LoggerFactory.getLogger(HandlerUtils.class);

	@Override
	public void closeFutureCtx(Future<ChannelHandlerContext> futureCtx) {
		futureCtx.addListener(new GenericFutureListener<Future<ChannelHandlerContext>>() {
			@Override
			public void operationComplete(Future<ChannelHandlerContext> future) throws Exception {
				if (future.isSuccess()) {
					final Channel ch = future.get().channel();
					logger.debug("Closing backend channel {}", ch);
					closeOnFlush(ch);
				}
			}
		});

	}

	@Override
	public void setAutoRead(boolean autoRead, ChannelFuture future, Optional<Channel> closable) {
		future.addListener(new ChannelFutureListener() {

			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					logger.debug("Setting auto read to {} for channel {}", autoRead, future.channel());
					future.channel().config().setAutoRead(autoRead);
				} else {
					// Close the optional closable if present
					logger.error("Channel future was not successful, cannot set auto read to {}: {}", autoRead,
							future.cause());
					closable.ifPresent(c -> c.close());
				}
			}
		});

	}
	
	@Override
	public void closeOnFlush(Channel ch) {
		if (ch.isActive()) {
			ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public String channelId(ChannelHandlerContext ctx) {
		return ctx.channel().id().asLongText();
	}

}
