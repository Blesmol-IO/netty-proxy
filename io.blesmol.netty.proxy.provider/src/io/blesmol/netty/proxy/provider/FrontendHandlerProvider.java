package io.blesmol.netty.proxy.provider;

import java.util.Map;

import org.osgi.service.component.ComponentServiceObjects;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceScope;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.util.promise.Deferred;

import io.blesmol.netty.api.OsgiChannelHandler;
import io.blesmol.netty.proxy.api.BackendHandler;
import io.blesmol.netty.proxy.api.Configuration;
import io.blesmol.netty.proxy.api.FrontendHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;

// Copyright 2012 The Netty Project and Coastal Hacking
@Component(scope = ServiceScope.PROTOTYPE, configurationPid = Configuration.FRONTEND_HANDLER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = ChannelHandler.class)
public class FrontendHandlerProvider extends ChannelInboundHandlerAdapter implements FrontendHandler {

	private volatile Channel outbound;

	@Reference(scope = ReferenceScope.PROTOTYPE)
	Bootstrap bootstrap;
	
	@Reference(scope = ReferenceScope.PROTOTYPE)
	BackendHandler backendHandler;

	@Reference(scope = ReferenceScope.PROTOTYPE_REQUIRED)
	ComponentServiceObjects<OsgiChannelHandler> channelHandlerFactory;

	// Can be read on different threads
	volatile Channel inbound;
	String destinationHost;
	int destinationPort;
	String handlerName;

	@Activate
	void activate(Configuration.FrontendHandler config, Map<String, ?> properties) {
		destinationHost = config.destinationHost();
		destinationPort = config.destinationPort();
		handlerName = config.handleName();
		backendHandler.setFrontendHandler(this);
	}

	@Deactivate
	void deactivate() {
		if (outbound != null && outbound.pipeline().get(handlerName) != null) {
			outbound.pipeline().remove(this);
		}
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		// TODO Auto-generated method stub
		super.handlerAdded(ctx);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		// TODO Auto-generated method stub
		super.handlerRemoved(ctx);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		inbound = ctx.channel();
		bootstrap.group(inbound.eventLoop()).channel(inbound.getClass())
				.handler(backendHandler)
//				.handler(new ChannelInitializer<Channel>() {
//					@Override
//					protected void initChannel(Channel ch) throws Exception {
//						
//						// FIXME: copy-pasta from the dynamic channel intializer
//
////						final OsgiChannelHandler handler = channelHandlerFactory.getService();
////						ch.pipeline().addLast(handler);
//						// Manually add backend
//						ch.pipeline().addLast(backendHandler);
//
//						// Unget the service when this channel is closed
//						ch.closeFuture().addListener(new ChannelFutureListener() {
//							@Override
//							public void operationComplete(ChannelFuture future) throws Exception {
////								if (channelHandlerFactory != null) {
////									channelHandlerFactory.ungetService(handler);
////								}
//
//							}
//						});						
//					}
//				})
				// https://stackoverflow.com/a/28294255
				.option(ChannelOption.AUTO_READ, false);

		final ChannelFuture outboundFuture = bootstrap.connect(destinationHost, destinationPort);
		outbound = outboundFuture.channel();
		outboundFuture.addListener(new ChannelFutureListener() {

			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					// Defer read until connection is successful
					inbound.read();
				} else {
					inbound.close();
				}

			}
		});
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) {

		Deferred<Boolean> outboundActive = new Deferred<>();
		backendHandler.setDeferred(outboundActive);
		outboundActive.getPromise().onResolve(new Runnable() {

			@Override
			public void run() {
				outbound.writeAndFlush(msg).addListener(new ChannelFutureListener() {
					@Override
					public void operationComplete(ChannelFuture future) {
						if (future.isSuccess()) {
							// was able to flush out data, start to read the next chunk
							ctx.channel().read();
						} else {
							future.channel().close();
						}
					}
				});
				
			}
		});
//		if (outbound.isActive()) {
//			outbound.writeAndFlush(msg).addListener(new ChannelFutureListener() {
//				@Override
//				public void operationComplete(ChannelFuture future) {
//					if (future.isSuccess()) {
//						// was able to flush out data, start to read the next chunk
//						ctx.channel().read();
//					} else {
//						future.channel().close();
//					}
//				}
//			});
//		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		// TODO: unregister from OSGi?
		if (outbound != null) {
			closeOnFlush(outbound);
		}
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

	@Override
	public Channel connectChannel() {
		return inbound;
	}

}
