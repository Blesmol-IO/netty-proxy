package io.blesmol.netty.proxy.example;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import io.blesmol.netty.proxy.api.ProxyProviderApi;

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

	private final List<String> configPids = new CopyOnWriteArrayList<>();

	@Activate
	void activate(Config config) throws Exception {

		Map<String, Object> extraProperties = new HashMap<>();

		// Non-default properties for http object aggregator
		extraProperties.put(ProxyProviderApi.HttpObjectAggegator.MAX_CONTENT_LENGTH, 8192);

		// Properties for frontend proxy
		extraProperties.put(ProxyProviderApi.FrontendHandler.CLIENT_FACTORY_PIDS,
				new String[] { ProxyProviderApi.HttpClientCodec.PID, ProxyProviderApi.BackendHandler.PID});
		extraProperties.put(ProxyProviderApi.FrontendHandler.CLIENT_HANDLER_NAMES,
				new String[] { ProxyProviderApi.HttpClientCodec.NAME, ProxyProviderApi.BackendHandler.NAME });

		List<String> factoryPids = Stream
				.of(ProxyProviderApi.HttpServerCodec.PID, ProxyProviderApi.HttpObjectAggegator.PID,
						ProxyProviderApi.HttpDirectProxyHandler.PID, ProxyProviderApi.FrontendHandler.PID)
				.collect(Collectors.toList());
		List<String> handlerNames = Stream
				.of(ProxyProviderApi.HttpServerCodec.NAME, ProxyProviderApi.HttpObjectAggegator.NAME,
						ProxyProviderApi.HttpDirectProxyHandler.NAME, ProxyProviderApi.FrontendHandler.NAME)
				.collect(Collectors.toList());

		// need to create a configuration for app-wide shared event executor group used
		// by frontends
		// configPids.add(configUtil.createEventLoopGroup(config.appName(),
		// ReferenceName.FrontendHandler.EVENT_EXECUTOR_GROUP));
		configPids.addAll(configUtil.createNettyServer(config.appName(), config.hostame(), config.port(), factoryPids,
				handlerNames, Optional.of(extraProperties)));

	}

	@Deactivate
	void deactivate(Config config) throws Exception {
		configUtil.deleteConfigurationPids(configPids);

	}

}
