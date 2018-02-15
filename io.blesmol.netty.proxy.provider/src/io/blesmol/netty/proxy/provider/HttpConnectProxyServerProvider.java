package io.blesmol.netty.proxy.provider;

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.ServiceScope;

import io.blesmol.netty.proxy.api.Configuration;
import io.blesmol.netty.proxy.api.HttpConnectProxyServer;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

@Component(
	configurationPid = Configuration.HTTP_CONNECT_PROXY_SERVER_PID,
	configurationPolicy = ConfigurationPolicy.REQUIRE,
	service = { HttpConnectProxyServer.class, ChannelHandler.class },
	scope = ServiceScope.PROTOTYPE
)
public class HttpConnectProxyServerProvider implements HttpConnectProxyServer, ChannelHandler {

	int maxContentLength;

	@Activate
	void activate(Configuration.HttpConnectProxyServer config, Map<String, Object> props) {
		maxContentLength = config.maxContentLength();
	}

	@Deactivate
	void deactivate(Configuration.HttpConnectProxyServer config, Map<String, Object> props) {

	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline().addLast(new HttpServerCodec());
        ctx.pipeline().addLast(new HttpObjectAggregator(maxContentLength));
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
