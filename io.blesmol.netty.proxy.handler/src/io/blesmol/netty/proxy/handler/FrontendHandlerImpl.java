package io.blesmol.netty.proxy.handler;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.blesmol.netty.api.NettyClient;
import io.blesmol.netty.proxy.api.FrontendHandler;
import io.blesmol.netty.proxy.api.HandlerUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

// FIXME: make sharable once the dynamic handler supports sharables
// rememeber to override isSharable
//@Sharable
public class FrontendHandlerImpl extends ChannelInboundHandlerAdapter implements FrontendHandler {

	private volatile Boolean closed = false;
	private static final AtomicReferenceFieldUpdater<FrontendHandlerImpl, Boolean> CLOSED_UPDATER = AtomicReferenceFieldUpdater.newUpdater(FrontendHandlerImpl.class, Boolean.class, "closed");
	
	protected static final Logger logger = LoggerFactory.getLogger(FrontendHandler.class);

	// Map of maps between frontend channel id, inet host & port, and backend ctx
	final Map<String, Map<InetKey, Promise<ChannelHandlerContext>>> keyedCtxs = new ConcurrentHashMap<>();

	// Cache between frontend channel id and its current backend ctx
	protected final Map<String, Future<ChannelHandlerContext>> cachedCtxs = new ConcurrentHashMap<>();

	// Map between channel id and channel
	protected final Map<String, Channel> channels = new ConcurrentHashMap<>();

	protected volatile HandlerUtils handlerUtils;

	//
	// PROTECTED SETTERS, UNSETTERS, and GETTERS
	//

	/**
	 * Handler utils ought not come and go, but it might be set from a different thread
	 */
	protected void setHandlerUtils(HandlerUtils handlerUtils) {
		this.handlerUtils = handlerUtils;
	}

	protected void unsetHandlerUtils(HandlerUtils handlerUtils) {
		this.handlerUtils = null;
	}

	/**
	 * Enable auto-read on the expected netty client. Otherwise ignore it
	 */
	protected void setNettyClient(NettyClient client, String channelId, InetKey key) {

		final Promise<ChannelHandlerContext> backendPromise = optionalBackendPromise(channelId, key, false)
				.orElse(null);
		if (backendPromise == null) {
			logger.info("Unexpected netty client {}. No backend promise for provided channel {} and key {}. Ignoring.",
					client, channelId, key);
			return;
		}

		// Adding a handler should happen-before setting a netty client. If this is
		// invalid, below will cause an NPE via Optional.of
		final Channel frontendChannel = channels.get(channelId);

		// FIXME: move NettyClient away from OSGi promises
		// When our client's connection occurs sometime in the future...
		client.promise().onResolve(() -> {
			try {
				handlerUtils.setAutoRead(true, client.promise().getValue(), Optional.of(frontendChannel));
			} catch (Exception e) {
				logger.warn("Exception setting netty client {} via key {}! Cause:\n{}", client, key, e);
			}
		});

	}

	/**
	 * Do nothing
	 */
	protected void unsetNettyClient(NettyClient client, String channelId, InetKey key) {
	}

	/**
	 * Set backend context to fulfill its promise
	 */
	protected void setBackendCtx(ChannelHandlerContext backendCtx, String frontendChannelId, InetKey key) {

		final Promise<ChannelHandlerContext> backendPromise = optionalBackendPromise(frontendChannelId, key, false)
				.orElse(null);
		if (backendPromise == null) {
			logger.info("Unexpected backend ctx {}. No backend promise for provided channel {} and key {}. Ignoring.",
					backendCtx, frontendChannelId, key);
			return;
		}
		logger.debug("Mapping frontend channel {} to backend context channel {} and key {}", frontendChannelId, backendCtx.channel(), key);
		backendPromise.setSuccess(backendCtx);
	}

	/**
	 * Close backend context if we have a promise for it, otherwise ignore
	 */
	protected void unsetBackendCtx(ChannelHandlerContext backendCtx, String frontendChannelId, InetKey key) throws Exception {
		optionalBackendPromise(frontendChannelId, key, /* remove */true); //.ifPresent(handlerUtils::closeFutureCtx);
	}
	
	/**
	 * Called when we receive an inet key to connect to
	 */
	protected void onInetKey(ChannelHandlerContext frontendCtx, InetKey key) {}
	
	/*
	 * 
	 */

	//
	// PACKAGE ACCESSIBLE (for testing)
	//

	Optional<Promise<ChannelHandlerContext>> optionalBackendPromise(String frontendChannelId, InetKey key, boolean remove) {
		final Map<InetKey, Promise<ChannelHandlerContext>> map;
		if (remove) {
			Map<InetKey, Promise<ChannelHandlerContext>> temp = keyedCtxs.remove(frontendChannelId);
			map = (temp == null) ? Collections.emptyMap() : temp;
		} else {
			map = keyedCtxs.getOrDefault(frontendChannelId, Collections.emptyMap());
		}
		return Optional.ofNullable(map.get(key));
	}

	void closeBackendAndCleanup(ChannelHandlerContext frontendCtx) {

		// Quick return
		if (CLOSED_UPDATER.compareAndSet(this, true, true)) {
			return;
		}

		// Remove backends since the frontend is going away
		final String channelId = handlerUtils.channelId(frontendCtx);
//		logger.debug("Closing all backend contexts related to channel {}", channelId);

		// Don't care about return value
		channels.remove(channelId);
		cachedCtxs.remove(channelId);

		// Closing is managed by the netty client app, which will be deactived when we remove
		// the frontend event loop group.

//		// And close
//		final Map<InetKey, Promise<ChannelHandlerContext>> keyedBackends = keyedCtxs.remove(channelId);
//		if (keyedBackends != null) {
//			keyedBackends.values().forEach(handlerUtils::closeFutureCtx);
//		}
	}

	//
	// SERVICE METHODS
	//

	@Override
	public void userEventTriggered(ChannelHandlerContext frontendCtx, Object evt) throws Exception {
		if (evt instanceof InetSocketAddress || evt instanceof InetKey) {

			final InetKey key = (evt instanceof InetSocketAddress) ? new InetKey((InetSocketAddress) evt) : (InetKey) evt;
			final String channelId = handlerUtils.channelId(frontendCtx);
			keyedCtxs.putIfAbsent(channelId, new ConcurrentHashMap<>());
			final Map<InetKey, Promise<ChannelHandlerContext>> inetCtxs = keyedCtxs.get(channelId);

			// Create promise using frontend executor since it is somewhat I/O related
			inetCtxs.putIfAbsent(key, new DefaultPromise<>(frontendCtx.executor()));

			final Future<ChannelHandlerContext> backendFuture = inetCtxs.get(key);

			// And then put into the cache
			cachedCtxs.put(channelId, backendFuture);
			logger.debug("Put backend future {} as current backend context for frontend channel {}", backendFuture,
					channelId);
			onInetKey(frontendCtx, key);
		} else {
			super.userEventTriggered(frontendCtx, evt);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext frontendCtx, Throwable cause) {
		handlerUtils.closeOnFlush(frontendCtx.channel());
		logger.debug("exceptionCaught called with cause {}", cause);
	}

	@Override
	public void handlerAdded(ChannelHandlerContext frontendCtx) throws Exception {
		channels.put(handlerUtils.channelId(frontendCtx), frontendCtx.channel());
		logger.debug("handlerAdded called");
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext frontendCtx) throws Exception {
		closeBackendAndCleanup(frontendCtx);
		logger.debug("handlerRemoved called");
	}

	@Override
	public void channelRead(ChannelHandlerContext frontendCtx, Object msg) throws Exception {

		// Usually not null: userEventTriggered should have been called with a socket
		// address. And neither channelInactiver nor handlerRemoved should have been
		// called.

		// However, this could occur if this handler was added with a different event
		// executor than the channel's and the handler is about to be added (pending).
		final Future<ChannelHandlerContext> backendFuture = cachedCtxs.get(frontendCtx.channel().id().asLongText());
		if (backendFuture == null) {
			logger.warn("No backend context available via context {} when channel was read, closing channel.",
					frontendCtx);

			handlerUtils.closeOnFlush(frontendCtx.channel());
			return;
		}

		// Async write to our future context
		backendFuture.addListener(new GenericFutureListener<Future<ChannelHandlerContext>>() {

			@Override
			public void operationComplete(Future<ChannelHandlerContext> ctxFuture) throws Exception {
				if (ctxFuture.isSuccess()) {
					final ChannelHandlerContext backendCtx = ctxFuture.get();
					logger.debug("Writing inbound message {} to backend channel {}.", msg, backendCtx.channel());
					backendCtx.writeAndFlush(msg).addListener(new ChannelFutureListener() {
						@Override
						public void operationComplete(ChannelFuture writeFuture) throws Exception {
							if (writeFuture.isSuccess()) {
								// was able to flush out data, force channel read
								logger.debug("Wrote message successfully to backend channel {}, reading on frontend.",
										writeFuture.channel());
								frontendCtx.channel().read();
							} else {
								logger.warn(
										"Unsuccessful writing inbound message to backend channel {}, caused by:\n{}",
										writeFuture.channel(), writeFuture.cause());
//								writeFuture.channel().close();
							}
						}
					});
				} else {
					logger.warn(
							"Not successful getting backend context via context {} when channel was read, closing channel. Cause: \n{}",
							frontendCtx, backendFuture.cause());
					frontendCtx.channel().close();
				}
			}

		});
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext frontendCtx) throws Exception {
		logger.debug("channelReadCcomplete called.");
		super.channelReadComplete(frontendCtx);
	}

	@Override
	public void channelInactive(ChannelHandlerContext frontendCtx) throws Exception {
		closeBackendAndCleanup(frontendCtx);
	}

}
