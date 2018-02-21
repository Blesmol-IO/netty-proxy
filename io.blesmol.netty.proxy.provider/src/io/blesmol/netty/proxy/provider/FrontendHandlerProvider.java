package io.blesmol.netty.proxy.provider;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.component.annotations.ReferenceScope;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

import io.blesmol.netty.proxy.api.BackendHandler;
import io.blesmol.netty.proxy.api.Configuration;
import io.blesmol.netty.proxy.api.FrontendHandler;
import io.blesmol.netty.proxy.api.Property;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;

// Copyright 2012 The Netty Project and Coastal Hacking
@Component(configurationPid = Configuration.FRONTEND_HANDLER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = ChannelHandler.class)
public class FrontendHandlerProvider extends ChannelInboundHandlerAdapter implements FrontendHandler {

	private volatile org.osgi.service.cm.Configuration backendConfig;

	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final Deferred<Void> deferredClosed = new Deferred<>();
	private final Promise<Void> promisedClosed = deferredClosed.getPromise();

	private final Deferred<ChannelFuture> deferredFuture = new Deferred<>();
	private final Promise<ChannelFuture> promisedFuture = deferredFuture.getPromise();

	private final Deferred<BackendHandler> deferredHandler = new Deferred<>();
	private final Promise<BackendHandler> promisedHandler = deferredHandler.getPromise();

	private final Deferred<Channel> deferredChannel = new Deferred<>();
	private final Promise<Channel> promisedChannel = deferredChannel.getPromise();

	private volatile Configuration.FrontendHandler config;

	@Reference(scope = ReferenceScope.PROTOTYPE)
	Bootstrap bootstrap;

	@Reference
	ConfigurationAdmin configAdmin;

	@Reference
	ExecutorService executor;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	void setBackendHandler(BackendHandler backendHandler, Map<String, Object> props) {
		System.out.println("Received backend handler " + backendHandler);
		deferredHandler.resolve(backendHandler);
	}

	void unsetBackendHandler(BackendHandler backendHandler, Map<String, Object> props) {

	}

	@Activate
	void activate(Configuration.FrontendHandler config, Map<String, ?> properties) throws Exception {
		this.config = config;

		// Sometime in the future, start the client via the configured backend handler

		deferredFuture
				.resolveWith(createConfiguration()
						.then((p1) -> startClient(promisedChannel.getValue(), promisedHandler.getValue())))
				.then((p2) -> sendThisChannelToOtherChannelAsUserEvent(promisedChannel.getValue(),
						promisedFuture.getValue().channel()));

		// deferredFuture.resolveWith(promisedHandler.then((p) ->
		// startClient(promisedChannel.getValue(), p.getValue())));
	}

	@Deactivate
	void deactivate(Configuration.FrontendHandler config) {
		System.out.println("Deactivating frontend handler");
		this.config = config;
		promisedFuture.then(p -> close(p.getValue()));
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		deferredChannel.resolve(ctx.channel());
		System.out.println("Resolved channel context");

	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		// TODO Auto-generated method stub
		super.handlerRemoved(ctx);
	}

	private Promise<Void> createConfiguration() {

		Deferred<Void> result = new Deferred<>();

		executor.execute(new Runnable() {

			@Override
			public void run() {
				try {
					backendConfig = configAdmin.createFactoryConfiguration(Configuration.BACKEND_HANDLER_PID, "?");
					Hashtable<String, Object> props = new Hashtable<>();
					props.put(Property.BackendHandler.DESTINATION_HOST, config.destinationHost());
					props.put(Property.BackendHandler.DESTINATION_PORT, config.destinationPort());
					props.put(Property.BackendHandler.HANDLER_NAME, Configuration.BACKEND_HANDLER_NAME);

					backendConfig.update(props);
					result.resolve(null);
					System.out.println("Configuration created successfully");
				} catch (IOException e) {
					System.out.println("Configuration not created successfully!");
					result.fail(e);
				}

			}
		});

		return result.getPromise();

	}

	private Promise<Channel> sendThisChannelToOtherChannelAsUserEvent(Channel thisChannel, Channel otherChannel) {

		otherChannel.pipeline().fireUserEventTriggered(thisChannel);
		System.out.println("Firing user event");
		return Promises.resolved(thisChannel);
	}

	private Promise<ChannelFuture> startClient(Channel inboundChannel, BackendHandler backendHandler) {

		final Deferred<ChannelFuture> deferred = new Deferred<>();

		executor.execute(new Runnable() {

			@Override
			public void run() {
				System.out.println("Starting netty client");
				bootstrap.group(inboundChannel.eventLoop()).channel(inboundChannel.getClass()).handler(backendHandler)
						// .handler(new ChannelInitializer<Channel>() {
						// @Override
						// protected void initChannel(Channel ch) throws Exception {
						//
						// // FIXME: copy-pasta from the dynamic channel intializer
						//
						//// final OsgiChannelHandler handler = channelHandlerFactory.getService();
						//// ch.pipeline().addLast(handler);
						// // Manually add backend
						// ch.pipeline().addLast(backendHandler);
						//
						// // Unget the service when this channel is closed
						// ch.closeFuture().addListener(new ChannelFutureListener() {
						// @Override
						// public void operationComplete(ChannelFuture future) throws Exception {
						//// if (channelHandlerFactory != null) {
						//// channelHandlerFactory.ungetService(handler);
						//// }
						//
						// }
						// });
						// }
						// })[
						// https://stackoverflow.com/a/28294255
						.option(ChannelOption.AUTO_READ, false);

				final ChannelFuture outboundFuture = bootstrap.connect(config.destinationHost(),
						config.destinationPort());
				outboundFuture.addListener(new ChannelFutureListener() {

					@Override
					public void operationComplete(ChannelFuture future) throws Exception {
						if (future.isSuccess()) {
							// Defer read until connection is successful
							deferred.resolve(future);
							future.channel().config().setAutoRead(true);
							// inboundChannel.read();
							// future.channel().config().setAutoRead(true);
							System.out.println("Channel is active, reading");
						} else {
							System.out.println("Channel was not activated correctly!");
							deferred.fail(new IllegalStateException("Channel was not opened successfully"));
							inboundChannel.close();
						}

					}
				});

			}
		});

		return deferred.getPromise();
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) {

		// Deferred<Boolean> outboundActive = new Deferred<>();
		// backendHandler.setDeferred(outboundActive);

		promisedFuture.onResolve(new Runnable() {

			@Override
			public void run() {
				try {
					System.out.println("Writing inbound message to outbound channel");
					promisedFuture.getValue().channel().writeAndFlush(msg).addListener(new ChannelFutureListener() {
						@Override
						public void operationComplete(ChannelFuture future) {
							if (future.isSuccess()) {
								// was able to flush out data, start to read the next chunk
								ctx.channel().read();
								System.out.println("Wrote outbound message successfully");
							} else {
								future.channel().close();
								System.out.println("Did not write outbound message successfully!");
							}
						}
					});
				} catch (InvocationTargetException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		});
		// if (outbound.isActive()) {
		// outbound.writeAndFlush(msg).addListener(new ChannelFutureListener() {
		// @Override
		// public void operationComplete(ChannelFuture future) {
		// if (future.isSuccess()) {
		// // was able to flush out data, start to read the next chunk
		// ctx.channel().read();
		// } else {
		// future.channel().close();
		// }
		// }
		// });
		// }
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		System.out.println("Channel read completely");

	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		promisedFuture.then(p -> close(p.getValue()));
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		closeOnFlush(ctx.channel());
	}

	/**
	 * Closes the specified channel after all queued write requests are flushed.
	 */
	static void closeOnFlush(Channel ch) {
		if (ch.isActive()) {
			ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
		}
	}

	private Promise<Void> close(ChannelFuture otherFuture) {

		if (closed.getAndSet(true) == false) {

			final org.osgi.service.cm.Configuration backendConfig = this.backendConfig;

			executor.execute(new Runnable() {

				@Override
				public void run() {
					closeOnFlush(otherFuture.channel());
					try {
						backendConfig.delete();
						deferredClosed.resolve(null);
					} catch (IOException e) {

						deferredClosed.fail(e);
					}
				}
			});
		}

		return promisedClosed;

	}

}
