package io.blesmol.netty.proxy.api;

public interface ReferenceName extends io.blesmol.netty.api.ReferenceName {
	
	interface BackendHandler {
		String FRONTEND_CHANNEL = "frontendChannel";
		String FRONTEND_CHANNEL_TARGET = FRONTEND_CHANNEL + DOT_TARGET;
	}

	interface FrontendHandler {
		String NETTY_CLIENTS = "nettyClients";
		String NETTY_CLIENTS_TARGET = NETTY_CLIENTS + DOT_TARGET;

		String BACKEND_CONTEXTS = "backendContexts";
		String BACKEND_CONTEXTS_TARGET = BACKEND_CONTEXTS + DOT_TARGET;
	}

}
