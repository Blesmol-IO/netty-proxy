package io.blesmol.netty.proxy.api;

public interface ReferenceName extends io.blesmol.netty.api.ReferenceName {
	
	interface BackendHandler {
		String FRONTEND_CHANNEL = "frontendChannel";
		String FRONTEND_CHANNEL_TARGET = FRONTEND_CHANNEL + DOT_TARGET;
		// Use same name as frontend
		String EVENT_EXECUTOR_GROUP = FrontendHandler.EVENT_EXECUTOR_GROUP;
		String EVENT_EXECUTOR_GROUP_TARGET = EVENT_EXECUTOR_GROUP + DOT_TARGET;
	}

	interface FrontendHandler {
		
		String NETTY_CLIENT = "nettyClient";
		String NETTY_CLIENT_TARGET = NETTY_CLIENT + DOT_TARGET;
		String EVENT_EXECUTOR_GROUP = "frontendEventExecutorGroup";
		String EVENT_EXECUTOR_GROUP_TARGET = EVENT_EXECUTOR_GROUP + DOT_TARGET;
		
		String BACKEND_CONTEXT = "backendContext";
		String BACKEND_CONTEXT_TARGET = BACKEND_CONTEXT + DOT_TARGET;
	}
}
