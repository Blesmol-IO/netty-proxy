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
import io.blesmol.netty.api.Property;
import io.blesmol.netty.proxy.api.Configuration;
import io.blesmol.netty.proxy.api.ReferenceName;

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
	
	private List<String> configPids;

	@Activate
	void activate(Config config) throws Exception {

		// Target front ends
		String frontendEventExecutorGroupTarget = String.format("(&(%s=%s)(%s=%s))", Property.EventExecutorGroup.APP_NAME, config.appName(), Property.EventExecutorGroup.GROUP_NAME, ReferenceName.FrontendHandler.EVENT_EXECUTOR_GROUP);
		Map<String, Object> extraProperties = new HashMap<>();
		extraProperties.put(ReferenceName.FrontendHandler.EVENT_EXECUTOR_GROUP_TARGET, frontendEventExecutorGroupTarget);

		List<String> factoryPids = Stream.of(Configuration.HTTP_CONNECT_PROXY_SERVER_PID, Configuration.FRONTEND_HANDLER_PID).collect(Collectors.toList());
		List<String> handlerNames = Stream.of(Configuration.HTTP_CONNECT_PROXY_SERVER_NAME, Configuration.FRONTEND_HANDLER_NAME).collect(Collectors.toList());
		
		// need to create a configuration for app-wide shared event executor group used by frontends
		configPids.add(configUtil.createEventLoopGroup(config.appName(), ReferenceName.FrontendHandler.EVENT_EXECUTOR_GROUP));
		configPids.addAll(configUtil.createNettyServer(config.appName(), config.hostame(), config.port(), factoryPids, handlerNames, Optional.empty()));

	}

	@Deactivate
	void deactivate(Config config) throws Exception {
		configUtil.deleteConfigurationPids(configPids);

	}

}
