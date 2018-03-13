package io.blesmol.netty.proxy.handler;

import java.net.InetSocketAddress;

import io.netty.channel.ChannelHandlerContext;


public class InetChannelKey {

	public final String inetHost;

	public final int inetPort;

	public final String channelId;

	public InetChannelKey(InetSocketAddress socketAddress, ChannelHandlerContext ctx) {
		this(socketAddress.getHostName(), socketAddress.getPort(), ctx.channel().id().asLongText());
	}
	
	public InetChannelKey(String inetHost, int inetPort, String channelId) {
		super();
		if (inetHost == null || inetPort == -1 || channelId == null) {
			throw new IllegalArgumentException(String.format("Invalid InetChannelKey value detected: [inetHost=%s, inetPort=%s, channelId=%s]", inetHost, inetPort, channelId));
		}
		this.inetHost = inetHost;
		this.inetPort = inetPort;
		this.channelId = channelId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + channelId.hashCode();
		result = prime * result + inetHost.hashCode();
		result = prime * result + inetPort;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		InetChannelKey other = (InetChannelKey) obj;
		if (!channelId.equals(other.channelId))
			return false;
		if (!inetHost.equals(other.inetHost))
			return false;
		if (inetPort != other.inetPort)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("InetChannelKey [inetHost=%s, inetPort=%s, channelId=%s]", inetHost, inetPort, channelId);
	}

}
