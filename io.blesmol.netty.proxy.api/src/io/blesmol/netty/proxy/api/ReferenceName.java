package io.blesmol.netty.proxy.api;

public interface ReferenceName {

	interface FrontendHandler {

//		String CHANNEL_INITIALIZER = "channelInitializer.target";
		String BACKEND_HANDLER = "backendHandler.target";
		String CHANNEL_HANDLER_FACTORY = "channelHandlerFactory.target";
	}
}
