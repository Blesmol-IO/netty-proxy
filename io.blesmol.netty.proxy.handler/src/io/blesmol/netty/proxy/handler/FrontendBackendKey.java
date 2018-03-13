package io.blesmol.netty.proxy.handler;

import java.net.InetSocketAddress;

import io.netty.channel.ChannelHandlerContext;

@Deprecated
public class FrontendBackendKey {
	public final String inetHost;
	public final int inetPort;
	public final String appName;

	public FrontendBackendKey(ChannelHandlerContext ctx, InetSocketAddress socketAddress) {
		this(toAppName(ctx), socketAddress.getHostName(), socketAddress.getPort());
	}

	public FrontendBackendKey(String appName, String inetHost, int inetPort) {
		if (inetHost == null || inetPort == -1 || appName == null) {
			throw new IllegalArgumentException(String.format(
					"A value passed to the constructor was invalid: appName: %s, inetHost: %s, inetPort: %d", appName,
					inetHost, inetPort));
		}
		this.inetHost = inetHost;
		this.inetPort = inetPort;
		this.appName = appName;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((appName == null) ? 0 : appName.hashCode());
		result = prime * result + ((inetHost == null) ? 0 : inetHost.hashCode());
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
		FrontendBackendKey other = (FrontendBackendKey) obj;
		if (appName == null) {
			if (other.appName != null)
				return false;
		} else if (!appName.equals(other.appName))
			return false;
		if (inetHost == null) {
			if (other.inetHost != null)
				return false;
		} else if (!inetHost.equals(other.inetHost))
			return false;
		if (inetPort != other.inetPort)
			return false;
		return true;
	}

	public static String toAppName(ChannelHandlerContext ctx) {
		return ctx.channel().id().asLongText();
	}
}
