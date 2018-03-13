package io.blesmol.netty.proxy.provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import io.blesmol.netty.proxy.api.ProxyProviderApi;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.HttpRequestEncoder;

@Component(configurationPid = ProxyProviderApi.HttpRequestEncoder.PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = ChannelHandler.class)
public class HttpRequestEncoderProvider extends HttpRequestEncoder {

	@Override
	public boolean acceptOutboundMessage(Object msg) throws Exception {
		System.out.println("In http request encoder, accepted message of instance: " + msg);
		return super.acceptOutboundMessage(msg);
	}

	
}
