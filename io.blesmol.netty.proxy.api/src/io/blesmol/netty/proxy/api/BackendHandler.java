package io.blesmol.netty.proxy.api;

import org.osgi.util.promise.Deferred;

import io.netty.channel.ChannelHandler;

public interface BackendHandler extends ChannelHandler {

	void setFrontendHandler(FrontendHandler frontendHandler);
	
	void setDeferred(Deferred<Boolean> deferred);
}
