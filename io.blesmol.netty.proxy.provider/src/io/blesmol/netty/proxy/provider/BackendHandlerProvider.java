package io.blesmol.netty.proxy.provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;

import io.blesmol.netty.api.EventExecutorGroupHandler;
import io.blesmol.netty.proxy.api.BackendHandler;
import io.blesmol.netty.proxy.api.Configuration;
import io.blesmol.netty.proxy.api.ReferenceName;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.EventExecutorGroup;

@Component(configurationPid = Configuration.BACKEND_HANDLER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = ChannelHandler.class)
public class BackendHandlerProvider extends ChannelInboundHandlerAdapter implements BackendHandler, EventExecutorGroupHandler {

	@Reference(name = ReferenceName.BackendHandler.EVENT_EXECUTOR_GROUP)
	EventExecutorGroup eventExecutorGroup;

	@Reference(name = ReferenceName.BackendHandler.FRONTEND_CHANNEL)
	Channel frontendChannel;

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		System.out.println("Backend active, about to read");
		ctx.read();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		frontendChannel.write(msg);
		System.out.println("Backend read");
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		frontendChannel.flush();
		System.out.println("Backend read completely");
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		frontendChannel.close();
		System.out.println("Backend inactive");
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		if (ctx.channel().isActive()) {
			ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public EventExecutorGroup getEventExecutorGroup() {
		return eventExecutorGroup;
	}
}
