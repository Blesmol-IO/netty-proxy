package io.blesmol.netty.proxy.provider;

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;

import io.blesmol.netty.proxy.api.Configuration;
import io.blesmol.netty.proxy.api.HttpConnectProxyServer;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

@Component(
	configurationPid = Configuration.HTTP_CONNECT_PROXY_SERVER_PID,
	configurationPolicy = ConfigurationPolicy.REQUIRE,
	service = ChannelHandler.class
)
public class HttpConnectProxyServerProvider implements HttpConnectProxyServer, ChannelHandler {

	private volatile Configuration.HttpConnectProxyServer config;
	int maxContentLength;

	@Activate
	void activate(Configuration.HttpConnectProxyServer config, Map<String, Object> props) {
		this.config = config;
		maxContentLength = config.maxContentLength();
	}

	@Deactivate
	void deactivate(Configuration.HttpConnectProxyServer config, Map<String, Object> props) {

	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		Configuration.HttpConnectProxyServer config = this.config;
		System.out.println("Adding http connect related handlers");
        ctx.pipeline().addAfter(config.handlerName(), "httpServerCodec", new HttpServerCodec()); // final class
        ctx.pipeline().addAfter("httpServerCodec", "httpObjectAggegator", new HttpObjectAggregator(maxContentLength));
        ctx.pipeline().addAfter("httpObjectAggegator", "httpDirectProxyHandler", new HttpDirectProxyHandler());
//        ctx.pipeline().addAfter("httpDirectProxyHandler", "httpClientCodec", new HttpClientCodec());
        ctx.pipeline().remove(this);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		// TODO

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		// TODO Auto-generated method stub

	}

}
