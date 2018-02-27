package io.blesmol.netty.proxy.api;

public interface Property extends io.blesmol.netty.api.Property {

	interface BackendHandler extends ChannelHandler {
		String FRONTEND_CHANNEL_ID = "frontendChannelId";
	}

	interface FrontendHandler extends ChannelHandler {
		String CLIENT_FACTORY_PIDS = "clientFactoryPids";
		String CLIENT_HANDLER_NAMES = "clientHandlerNames";
	}

	interface HttpConnectProxyServer extends ChannelHandler {
		String MAX_CONTENT_LENGTH = "maxContentLength";
	}

	interface Channel {
		String CHANNEL_ID = "channelId";
	}
}
