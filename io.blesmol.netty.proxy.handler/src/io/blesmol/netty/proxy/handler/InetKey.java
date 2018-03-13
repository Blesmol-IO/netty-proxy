package io.blesmol.netty.proxy.handler;

import java.net.InetSocketAddress;

public class InetKey {

	public final String inetHost;

	public final int inetPort;

	public InetKey(InetSocketAddress socketAddress) {
		this(socketAddress.getHostName(), socketAddress.getPort());
	}
	
	public InetKey(String inetHost, int inetPort) {
		if (inetHost == null || inetPort == -1) {
			throw new IllegalArgumentException(
					String.format("Invalid InetKey value detected: [inetHost=%s, inetPort=%s]", inetHost, inetPort));
		}
		this.inetHost = inetHost;
		this.inetPort = inetPort;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		InetKey other = (InetKey) obj;
		if (!inetHost.equals(other.inetHost))
			return false;
		if (inetPort != other.inetPort)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("InetKey [inetHost=%s, inetPort=%s]", inetHost, inetPort);
	}

}
