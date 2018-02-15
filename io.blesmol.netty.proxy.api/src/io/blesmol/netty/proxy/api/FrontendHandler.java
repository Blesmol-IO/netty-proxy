package io.blesmol.netty.proxy.api;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

public interface FrontendHandler extends ChannelHandler {

	Channel connectChannel();
}
