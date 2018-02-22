package io.blesmol.netty.proxy.example;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.proxy.api.Configuration;

@Component(immediate = true)
public class Application {

	int port;
	String hostname;

	@interface Config {
		int port() default 8485;

		String hostame() default "localhost";

		String appName() default "io.blesmol.netty.proxy.example.Application";
	}

	@Reference
	ConfigurationUtil configUtil;

	@Reference
	ConfigurationAdmin admin;

	List<org.osgi.service.cm.Configuration> configurations = new CopyOnWriteArrayList<>();
	List<String> clientAppNames = new CopyOnWriteArrayList<>();
	
	private volatile String configPid;

	@Activate
	void activate(Config config) throws Exception {

//				.of(Configuration.HTTP_CONNECT_PROXY_SERVER_NAME, Configuration.FRONTEND_HANDLER_NAME)

		List<String> factoryPids = Stream.of(Configuration.HTTP_CONNECT_PROXY_SERVER_PID, Configuration.FRONTEND_HANDLER_PID).collect(Collectors.toList());
		List<String> handlerNames = Stream.of(Configuration.HTTP_CONNECT_PROXY_SERVER_NAME, Configuration.FRONTEND_HANDLER_NAME).collect(Collectors.toList());
		
		configPid = configUtil.createNettyServerConfig(config.appName(), config.hostame(), config.port(), factoryPids, handlerNames, Optional.empty());

//		createHttpConnectProxyServer(config);

	}

	@Deactivate
	void deactivate(Config config) throws Exception {
		configUtil.deleteNettyServerConfig(configPid);

	}

//	private void createHttpConnectProxyServer(Config config) throws Exception {
//		org.osgi.service.cm.Configuration configuration = admin
//				.createFactoryConfiguration(Configuration.HTTP_CONNECT_PROXY_SERVER_PID, "?");
//		final Hashtable<String, Object> props = new Hashtable<>();
//		props.put(Property.HttpConnectProxyServer.APP_NAME, config.appName());
//		configuration.update(props);
//		configurations.add(configuration);
//	}
	
	
//	private String createFrontendHandler(String appName, String hostname, int port) throws Exception {
//
//		// TODO: use netty client
//		
//		// FIXME: if destination name is untrusted, may result in ldap injection via
//		// [\*\(\)]
//		final String clientAppName = String.format("%s_%s:%d", appName, hostname, port);
//
//		// Create an initializer and dynamic handler for the client
//		
//		
//		configUtil.createOsgiChannelHandlerConfig(clientAppName, new ArrayList<>());
//
//		// Create the frontend, which contains the client
//		org.osgi.service.cm.Configuration configuration = admin
//				.createFactoryConfiguration(Configuration.FRONTEND_HANDLER_PID, "?");
//		final Hashtable<String, Object> props = new Hashtable<>();
//		props.put(Property.FrontendHandler.APP_NAME, appName);
//		props.put(Property.FrontendHandler.DESTINATION_HOST, hostname);
//		props.put(Property.FrontendHandler.DESTINATION_PORT, port);
//
//		// Target our backend and dynamic handler
//		props.put(ReferenceName.FrontendHandler.BACKEND_HANDLER,
//				String.format("(%s=%s)", Property.BackendHandler.APP_NAME, clientAppName));
//		props.put(ReferenceName.FrontendHandler.CHANNEL_HANDLER_FACTORY,
//				String.format("(%s=%s)", Property.FrontendHandler.APP_NAME, clientAppName));
//		
//		configuration.update(props);
//		configurations.add(configuration);
//		return clientAppName;
//	}

//	private void createBackendHandler(String appName, String clientAppName, String host, int port) throws Exception {
//		// Create the backend, which the frontend updates
//		org.osgi.service.cm.Configuration configuration = admin
//				.createFactoryConfiguration(Configuration.BACKEND_HANDLER_PID, "?");
//		final Hashtable<String, Object> props = new Hashtable<>();
//		props.put(Property.BackendHandler.APP_NAME, clientAppName);
//		props.put(Property.BackendHandler.HANDLE_NAME, Configuration.BACKEND_HANDLER_NAME);
//		configuration.update(props);
//		configurations.add(configuration);
//
//	}
//
//	private void deleteFrontendHandlers() throws Exception {
//		for (String clientAppName : clientAppNames) {
////			configUtil.deleteChannelInitializerConfig(clientAppName);
//			configUtil.deleteOsgiChannelHandlerConfig(clientAppName);
//		}
//	}
}
