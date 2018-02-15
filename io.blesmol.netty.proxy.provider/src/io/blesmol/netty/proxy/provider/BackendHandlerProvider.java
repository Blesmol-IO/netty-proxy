package io.blesmol.netty.proxy.provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.util.promise.Deferred;

import io.blesmol.netty.proxy.api.BackendHandler;
import io.blesmol.netty.proxy.api.Configuration;
import io.blesmol.netty.proxy.api.FrontendHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

// Copyright 2012 The Netty Project and Coastal Hacking
@Component(scope = ServiceScope.PROTOTYPE, configurationPid = Configuration.BACKEND_HANDLER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = BackendHandler.class)
public class BackendHandlerProvider extends ChannelInboundHandlerAdapter implements BackendHandler {

	private FrontendHandler frontendHandler;
	
	private Deferred<Boolean> deferred;

	@Override
	public void setFrontendHandler(FrontendHandler frontendHandler) {
		this.frontendHandler = frontendHandler;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		deferred.resolve(true);
		ctx.read();
	}
//
//	@Override
//	public void channelRead(final ChannelHandlerContext ctx, Object msg) {
//		frontendHandler.connectChannel().writeAndFlush(msg).addListener(new ChannelFutureListener() {
//			@Override
//			public void operationComplete(ChannelFuture future) {
//				if (future.isSuccess()) {
//					ctx.channel().read();
//				} else {
//					future.channel().close();
//				}
//			}
//		});
//	}

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    		frontendHandler.connectChannel().write(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    		frontendHandler.connectChannel().flush();
    }
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
//		FrontendHandlerProvider.closeOnFlush(frontendHandler.connectChannel());
		frontendHandler.connectChannel().close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		FrontendHandlerProvider.closeOnFlush(ctx.channel());
	}

	@Override
	public void setDeferred(Deferred<Boolean> deferred) {
		this.deferred = deferred;
		
	}

}
