package io.blesmol.netty.proxy.api;

@Deprecated
public interface Property extends io.blesmol.netty.api.Property {

	interface BackendHandler extends ChannelHandler {
	}

	interface FrontendHandler extends ChannelHandler {
	}
	
	 
}
