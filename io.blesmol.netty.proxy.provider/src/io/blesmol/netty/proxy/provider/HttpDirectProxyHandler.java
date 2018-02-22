package io.blesmol.netty.proxy.provider;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.List;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;

/**
 * 
 * Proxy which receives an HTTP request with a FQDN URL to proxy
 *
 */
public class HttpDirectProxyHandler extends MessageToMessageDecoder<HttpRequest> {

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		// Force a read to pull in the request
		System.out.println("Connect handler added");
		// ctx.channel().config().setAutoRead(true);
		ctx.read();
	}
	// @Override
	// public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
	// System.out.println("channel registered for reading inbound request");
	// }
	// @Override
	// public void channelActive(ChannelHandlerContext ctx) throws Exception {
	// System.out.println("channel active for reading inbound request");
	// }
	// @Override
	// public void channelRead(ChannelHandlerContext ctx, Object msg) throws
	// Exception {
	// System.out.println("Reading http request from client");
	// FullHttpRequest request = (FullHttpRequest) msg;
	// FullHttpResponse response;
	//
	// ctx.pipeline().remove(HttpObjectAggregator.class);
	// ctx.pipeline().get(HttpServerCodec.class).removeInboundHandler();
	//
	// if (!request.method().equals(HttpMethod.CONNECT)) {
	// try {
	// URL url = new URL(request.uri());
	// int port = url.getPort();
	// if (port == -1) {
	// port = url.getDefaultPort();
	// }
	// final SocketAddress socketAddress = new InetSocketAddress(url.getHost(),
	// port);
	// ctx.fireUserEventTriggered(socketAddress);
	// System.out.println("Sending socket address as event");
	//
	// HttpRequestEncoder encoder = new HttpRequestEncoder();
	// encoder.
	// ctx.fireChannelRead(request);
	// System.out.println("Forwarding request");
	//
	// ctx.pipeline().get(HttpServerCodec.class).removeOutboundHandler();
	// System.out.println("Removed http server codec outbound");
	// ctx.pipeline().remove("httpDirectProxyHandler");
	// System.out.println("Removing http proxy handler");
	//
	// } catch (IllegalArgumentException e) {
	// System.out.println("Could not parse URI " + request.uri());
	// }
	// } else {
	// System.out.println("CONNECT not supported");
	// response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
	// HttpResponseStatus.METHOD_NOT_ALLOWED);
	// response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
	// ctx.write(response);
	// ctx.pipeline().get(HttpServerCodec.class).removeOutboundHandler();
	// System.out.println("Removed http server codec outbound");
	// ctx.pipeline().remove("httpDirectProxyHandler");
	// System.out.println("Removing http proxy handler");
	//
	// }
	// // released in channel complete?
	//// request.release();
	//
	//// ctx.pipeline().get(HttpServerCodec.class).removeOutboundHandler();
	//// System.out.println("Removed http server codec outbound");
	//
	// }

	// @Override
	// public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
	// System.out.println("Channel read completely in connect handler");
	//// ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
	//// ctx.pipeline().remove("httpDirectProxyHandler");
	//// System.out.println("Removing http proxy handler");
	// }

	@Override
	protected void decode(ChannelHandlerContext ctx, HttpRequest request, List<Object> out) throws Exception {
		System.out.println("Reading http request from client");
		// FullHttpRequest request = (FullHttpRequest) msg;
		// FullHttpResponse response;

		ctx.pipeline().remove(HttpObjectAggregator.class);
		ctx.pipeline().get(HttpServerCodec.class).removeInboundHandler();

		if (!request.method().equals(HttpMethod.CONNECT)) {
			try {
				URL url = new URL(request.uri());
				int port = url.getPort();
				if (port == -1) {
					port = url.getDefaultPort();
				}
				final SocketAddress socketAddress = new InetSocketAddress(url.getHost(), port);
				ctx.fireUserEventTriggered(socketAddress);
				System.out.println("Sending socket address as event");

				// ctx.fireChannelRead(request);
				out.add(request);
				ReferenceCountUtil.retain(request);

				System.out.println("Forwarding request");

				ctx.pipeline().get(HttpServerCodec.class).removeOutboundHandler();
				System.out.println("Removed http server codec outbound");
				ctx.pipeline().remove("httpDirectProxyHandler");
				System.out.println("Removing http proxy handler");

			} catch (IllegalArgumentException e) {
				System.out.println("Could not parse URI " + request.uri());
			}
		}
		// else {
		// System.out.println("CONNECT not supported");
		// response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
		// HttpResponseStatus.METHOD_NOT_ALLOWED);
		// response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
		// ctx.write(response);
		// ctx.pipeline().get(HttpServerCodec.class).removeOutboundHandler();
		// System.out.println("Removed http server codec outbound");
		// ctx.pipeline().remove("httpDirectProxyHandler");
		// System.out.println("Removing http proxy handler");
		//
		// }

	}

}
