package io.blesmol.netty.proxy.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.blesmol.netty.proxy.api.BackendHandler;
import io.blesmol.netty.proxy.api.HandlerUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

// TODO: make sharable
public class BackendHandlerImpl extends ChannelInboundHandlerAdapter implements BackendHandler {

	protected static final Logger logger = LoggerFactory.getLogger(BackendHandler.class);

	protected volatile Channel frontendChannel;

	protected volatile HandlerUtils handlerUtils;

	//
	// PROTECTED SETTERS AND UNSETTERS
	//

	/*
	 * Handler utils ought not come and go, but it might be set from a different thread
	 */
	protected void setHandlerUtils(HandlerUtils handlerUtils) {
		this.handlerUtils = handlerUtils;
	}

	protected void removeHandlerUtils(HandlerUtils handlerUtils) {
		this.handlerUtils = null;
	}

	protected void setFrontendChannel(Channel frontendChannel) {
		this.frontendChannel = frontendChannel;
	}

	protected void unsetFrontendChannel(Channel frontendChannel) {
		this.frontendChannel = null;
	}

	//
	// SERVICE METHODS
	//

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

		logger.debug("Backend handler added to channel {}.", ctx.channel());

		// if (ctx.channel().isActive()) {
		// channelActiveDeferred.resolve(null);
		// }
		//
		// // Register our context via the frontend's channel ID, on a non-IO thread
		// executor.execute(() -> {
		// channelActivePromise.onResolve(() -> {
		// Hashtable<String, Object> properties = new Hashtable<>();
		// properties.put(ProxyProviderApi.ChannelHandlerContext.CHANNEL_ID,
		// frontendChannelId);
		// contextRegistration =
		// bundleContext.registerService(ChannelHandlerContext.class, ctx, properties);
		// });
		// });
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {

		logger.debug("Backend handler is active on channel {}, about to read.", ctx.channel());
		
		// if (!channelActivePromise.isDone()) {
		// channelActiveDeferred.resolve(null);
		// }
		ctx.read();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		logger.debug("Backend handler on channel {} reading message {}", ctx.channel(), msg);
		Channel frontendChannel = this.frontendChannel;
		if (frontendChannel == null) {
			logger.error("Attempted to write to a frontend channel that does not exist, ignoring!");
			return;
		}

		frontendChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				if (future.isSuccess()) {
					logger.debug("Backend wrote and flushed frontend {}. Now calling read on frontend",
							frontendChannel);
					ctx.channel().read();
				} else {
					logger.error("Backend could not write and flush frontend {}. Closing frontend.", frontendChannel);

					future.channel().close();
				}
			}
		});

	}

	// @Override
	// public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
	// frontendChannel.flush();
	// System.out.println("Backend read completely");
	// }

	// What do here? Just because we're done does not mean the frontend channel is
	// done
	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		logger.debug("Backend handler on channel {} is inactive, clearing reference to frontend channel {}.", ctx.channel(),
				frontendChannel);
		this.frontendChannel = null;

		// final Channel frontendChannel = this.frontendChannel;
		// logger.debug("Backend channel {} is inactive, attempting to close frontend
		// channel {}.", ctx.channel(), frontendChannel);
		// promisedHandlerUtils.addListener(new HandlerUtilsListener() {
		// @Override
		// protected void onSuccess(HandlerUtils handlerUtils) {
		// if (frontendChannel != null) {
		// handlerUtils.closeOnFlush(frontendChannel);
		// }
		// }
		// });

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		final Channel ch = ctx.channel();
		logger.warn("Backend channel {} closing self after catching exception: {}", ch, cause);
		handlerUtils.closeOnFlush(ch);
	}

}
