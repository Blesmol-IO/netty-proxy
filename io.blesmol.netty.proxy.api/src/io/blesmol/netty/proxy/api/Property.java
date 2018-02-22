package io.blesmol.netty.proxy.api;

public interface Property {

	interface BackendHandler extends io.blesmol.netty.api.Property.ChannelHandler {
		String DESTINATION_HOST = "destinationHost";
		String DESTINATION_PORT = "destinationPort";
	}

	interface FrontendHandler extends io.blesmol.netty.api.Property.ChannelHandler {

	}

	interface HttpConnectProxyServer extends io.blesmol.netty.api.Property.ChannelHandler {
		String MAX_CONTENT_LENGTH = "maxContentLength";
	}

}
