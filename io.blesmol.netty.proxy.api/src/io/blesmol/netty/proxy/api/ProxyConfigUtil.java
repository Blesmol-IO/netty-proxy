package io.blesmol.netty.proxy.api;

import java.util.Hashtable;

public interface ProxyConfigUtil {

	String frontendHandlerCreate();
	
	Hashtable<String, Object> frontendHandlerProperties();
	
	Hashtable<String, Object> frontendHandlerTargetNettyClients();
		
}
