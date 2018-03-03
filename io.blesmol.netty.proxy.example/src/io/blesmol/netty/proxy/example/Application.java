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
import io.blesmol.netty.proxy.api.ProxyApi;

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
		extraProperties.put(ProxyApi.HttpObjectAggegator.MAX_CONTENT_LENGTH, 8192);

		// Properties for frontend proxy
		extraProperties.put(ProxyApi.FrontendHandler.CLIENT_FACTORY_PIDS,
				new String[] { ProxyApi.HttpClientCodec.PID, ProxyApi.BackendHandler.PID});
		extraProperties.put(ProxyApi.FrontendHandler.CLIENT_HANDLER_NAMES,
				new String[] { ProxyApi.HttpClientCodec.NAME, ProxyApi.BackendHandler.NAME });

		List<String> factoryPids = Stream
				.of(ProxyApi.HttpServerCodec.PID, ProxyApi.HttpObjectAggegator.PID,
						ProxyApi.HttpDirectProxyHandler.PID, ProxyApi.FrontendHandler.PID)
				.collect(Collectors.toList());
		List<String> handlerNames = Stream
				.of(ProxyApi.HttpServerCodec.NAME, ProxyApi.HttpObjectAggegator.NAME,
						ProxyApi.HttpDirectProxyHandler.NAME, ProxyApi.FrontendHandler.NAME)
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
