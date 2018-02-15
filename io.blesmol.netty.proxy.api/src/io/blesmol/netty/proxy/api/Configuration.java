package io.blesmol.netty.proxy.api;

public interface Configuration {

	String BACKEND_HANDLER_PID = "io.blesmol.netty.proxy.api.BackendHandler";
	String BACKEND_HANDLER_NAME = "backendHandler";

	@interface BackendHandler {
		String appName();

		String handleName() default BACKEND_HANDLER_NAME;

		String destinationHost();

		int destinationPort();
	}

	String FRONTEND_HANDLER_PID = "io.blesmol.netty.proxy.api.FrontendHandler";
	String FRONTEND_HANDLER_NAME = "frontendHandler";

	@interface FrontendHandler {
		String appName();

		String handleName() default FRONTEND_HANDLER_NAME;

		String destinationHost();

		int destinationPort();
	}

	String HTTP_CONNECT_PROXY_SERVER_PID = "io.blesmol.netty.proxy.api.Configuration.HttpConnectProxyServer";
	String HTTP_CONNECT_PROXY_SERVER_NAME = "httpConnectProxyServer";

	@interface HttpConnectProxyServer {
		String appName();

		String handleName() default HTTP_CONNECT_PROXY_SERVER_NAME;

		// Maximum size of HTTP CONNECT request
		int maxContentLength() default 4096;
	}

}