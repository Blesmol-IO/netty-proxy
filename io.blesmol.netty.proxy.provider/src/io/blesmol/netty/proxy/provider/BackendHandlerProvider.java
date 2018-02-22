package io.blesmol.netty.proxy.provider;

import java.lang.reflect.InvocationTargetException;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

import io.blesmol.netty.api.Property.ChannelHandler;
import io.blesmol.netty.proxy.api.BackendHandler;
import io.blesmol.netty.proxy.api.Configuration;
import io.blesmol.netty.proxy.api.FrontendHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

// Copyright 2012 The Netty Project and Coastal Hacking
@Component(configurationPid = Configuration.BACKEND_HANDLER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = BackendHandler.class)
public class BackendHandlerProvider extends ChannelInboundHandlerAdapter implements BackendHandler {

	private final Deferred<Channel> deferredFrontendChannel = new Deferred<>();
	private final Promise<Channel> promisedFrontendChannel = deferredFrontendChannel.getPromise();

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		// got our front end channel
		System.out.println("Backend received a user event");
		if (evt instanceof Channel) {
			System.out.println("Backend received frontend channel via user event");
			deferredFrontendChannel.resolve((Channel) evt);
		}
	}

	
	
	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		System.out.println("Backend active, reading");
		ctx.read();
	}
	//
	// @Override
	// public void channelRead(final ChannelHandlerContext ctx, Object msg) {
	// frontendHandler.connectChannel().writeAndFlush(msg).addListener(new
	// ChannelFutureListener() {
	// @Override
	// public void operationComplete(ChannelFuture future) {
	// if (future.isSuccess()) {
	// ctx.channel().read();
	// } else {
	// future.channel().close();
	// }
	// }
	// });
	// }

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		System.out.println("Backend reading...");
		promisedFrontendChannel.getValue().write(msg);
		System.out.println("Backend read");
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		System.out.println("Backend read complete started...");
		promisedFrontendChannel.getValue().flush();
		System.out.println("Backend read completely");
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		System.out.println("Backend inactive");
		// FrontendHandlerProvider.closeOnFlush(frontendHandler.connectChannel());
		try {
			promisedFrontendChannel.getValue().close();
		} catch (InvocationTargetException | InterruptedException e) {
			exceptionCaught(ctx, e);
		}
		System.out.println("Backend channel inactive");
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		FrontendHandlerProvider.closeOnFlush(ctx.channel());
	}

}
