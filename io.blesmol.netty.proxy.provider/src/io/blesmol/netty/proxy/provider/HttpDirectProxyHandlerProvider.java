package io.blesmol.netty.proxy.provider;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import io.blesmol.netty.proxy.api.HttpDirectProxyHandler;
import io.blesmol.netty.proxy.api.ProxyApi;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;

@Component(configurationPid = ProxyApi.HttpDirectProxyHandler.PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = ChannelHandler.class)
public class HttpDirectProxyHandlerProvider extends MessageToMessageDecoder<HttpRequest> implements HttpDirectProxyHandler {
	
	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		// Force a read to pull in the request
		System.out.println("Connect handler added");
		// ctx.channel().config().setAutoRead(true);
		ctx.read();
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, HttpRequest request, List<Object> out) throws Exception {
		System.out.println("Reading http request from client");
		final String channelId = ctx.channel().id().asLongText();
		ctx.pipeline().remove(ProxyApi.HttpObjectAggegator.NAME);
		System.out.println("Removed http object aggegator");
		
//		ctx.pipeline().remove(ProxyApi.HttpServerCodec.NAME);
//		System.out.println("Removed http object aggegator and server codec");


		if (!request.method().equals(HttpMethod.CONNECT)) {
			try {
				URL url = new URL(request.uri());
				int port = url.getPort();
				if (port == -1) {
					port = url.getDefaultPort();
				}
				final SocketAddress socketAddress = new InetSocketAddress(url.getHost(), port);
				System.out.println("Sending socket address as event");
				ctx.fireUserEventTriggered(socketAddress);

				System.out.println(String.format("Forwarding request to %s on channel %s", url.toString(), channelId));
				out.add(request);
				ReferenceCountUtil.retain(request);
				ctx.pipeline().remove(ProxyApi.HttpDirectProxyHandler.NAME);
				System.out.println("Removed http proxy handler from " + channelId);
			} catch (IllegalArgumentException e) {
				System.out.println("Could not parse URI " + request.uri());
			}
		} else {
			System.out.println(String.format("Received CONNECT request to %s on %s", request.uri(), channelId));
			ctx.pipeline().remove(ProxyApi.HttpDirectProxyHandler.NAME);
			System.out.println("Removed http proxy handler from " + channelId);
			ctx.channel().close();
		}

	}
	
}
