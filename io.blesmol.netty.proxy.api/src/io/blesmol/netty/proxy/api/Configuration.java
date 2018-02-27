package io.blesmol.netty.proxy.api;

public interface Configuration {

	String BACKEND_HANDLER_PID = "io.blesmol.netty.proxy.api.BackendHandler";
	String BACKEND_HANDLER_NAME = "backendHandler";

	@interface BackendHandler {
		String handlerName() default BACKEND_HANDLER_NAME;

		String appName();

		String channelId();

		String frontendChannelId();
	}

	String FRONTEND_HANDLER_PID = "io.blesmol.netty.proxy.api.FrontendHandler";
	String FRONTEND_HANDLER_NAME = "frontendHandler";

	@interface FrontendHandler {
		String handlerName() default FRONTEND_HANDLER_NAME;

		String appName();

		String channelId();
		
		String[] clientFactoryPids();
		
		String[] clientHandlerNames();
		
	}

	String HTTP_CONNECT_PROXY_SERVER_PID = "io.blesmol.netty.proxy.api.HttpConnectProxyServer";
	String HTTP_CONNECT_PROXY_SERVER_NAME = "httpConnectProxyServer";

	@interface HttpConnectProxyServer {
		String handlerName() default HTTP_CONNECT_PROXY_SERVER_NAME;

		String appName();

		String channelId();

		// Maximum size of HTTP CONNECT request
		int maxContentLength() default 4096;
	}

}
