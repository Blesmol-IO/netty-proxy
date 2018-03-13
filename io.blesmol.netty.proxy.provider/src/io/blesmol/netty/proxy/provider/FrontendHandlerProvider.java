package io.blesmol.netty.proxy.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.NettyApi;
import io.blesmol.netty.api.NettyClient;
import io.blesmol.netty.proxy.api.HandlerUtils;
import io.blesmol.netty.proxy.api.ProxyProviderApi;
import io.blesmol.netty.proxy.api.ReferenceName;
import io.blesmol.netty.proxy.handler.FrontendHandlerImpl;
import io.blesmol.netty.proxy.handler.InetKey;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;

// Copyright 2012 The Netty Project and Coastal Hacking
@Component(configurationPid = ProxyProviderApi.FrontendHandler.PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = ChannelHandler.class)
public class FrontendHandlerProvider extends FrontendHandlerImpl {

	// Set via activate, not volatile for other methods
	private BundleContext bundleContext;
	private String servicePid;
	private ProxyProviderApi.FrontendHandler config;

	// Set in different thread than activate
	private volatile ServiceRegistration<ManagedServiceFactory> channelFactoryRegistration;
	private volatile ServiceRegistration<ManagedServiceFactory> eventLoopGroupRegistration;
	private volatile Future<?> activateFuture;

	private final Map<String, List<String>> channelsToPids = new ConcurrentHashMap<>();

	protected final EventExecutorGroup executorGroup = new DefaultEventLoopGroup(4);

	@Reference
	ConfigurationAdmin configAdmin;

	@Reference
	ConfigurationUtil configUtil;

	@Reference
	@Override
	protected void setHandlerUtils(HandlerUtils handlerUtils) {
		super.setHandlerUtils(handlerUtils);
	}

	@Override
	protected void unsetHandlerUtils(HandlerUtils handlerUtils) {
		super.unsetHandlerUtils(handlerUtils);
	}

	// TODO: target
	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, name = ReferenceName.FrontendHandler.BACKEND_CONTEXTS, target = "(serverAppName=void)")
	protected void setBackendCtx(ChannelHandlerContext backendCtx, Map<String, Object> properties) {
		String inetHost = (String) properties.get(ProxyProviderApi.ChannelHandlerContext.INET_HOST);
		int inetPort = (Integer) properties.get(ProxyProviderApi.ChannelHandlerContext.INET_PORT);
		String frontendChannelId = (String) properties.get(ProxyProviderApi.ChannelHandlerContext.APP_NAME);
		InetKey key = new InetKey(inetHost, inetPort);
		super.setBackendCtx(backendCtx, frontendChannelId, key);
	}

	protected void unsetBackendCtx(ChannelHandlerContext backendCtx, Map<String, Object> properties) throws Exception {
		String inetHost = (String) properties.get(ProxyProviderApi.ChannelHandlerContext.INET_HOST);
		int inetPort = (Integer) properties.get(ProxyProviderApi.ChannelHandlerContext.INET_PORT);
		String frontendChannelId = (String) properties.get(ProxyProviderApi.ChannelHandlerContext.APP_NAME);
		InetKey key = new InetKey(inetHost, inetPort);
		super.unsetBackendCtx(backendCtx, frontendChannelId, key);
	}

	// TODO: target
	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE, name = ReferenceName.FrontendHandler.NETTY_CLIENTS, target = "(serverAppName=void)")
	protected void setNettyClient(NettyClient client, Map<String, Object> properties) {
		String inetHost = (String) properties.get(NettyApi.NettyClient.INET_HOST);
		int inetPort = (Integer) properties.get(NettyApi.NettyClient.INET_PORT);
		String channelId = (String) properties.get(NettyApi.NettyClient.APP_NAME);
		InetKey key = new InetKey(inetHost, inetPort);
		super.setNettyClient(client, channelId, key);
	}

	protected void unsetNettyClient(NettyClient client, Map<String, Object> properties) {
		String inetHost = (String) properties.get(NettyApi.NettyClient.INET_HOST);
		int inetPort = (Integer) properties.get(NettyApi.NettyClient.INET_PORT);
		String channelId = (String) properties.get(NettyApi.NettyClient.APP_NAME);
		InetKey key = new InetKey(inetHost, inetPort);
		super.unsetNettyClient(client, channelId, key);
	}

	@Activate
	void activate(BundleContext bundleContext, ProxyProviderApi.FrontendHandler config, Map<String, ?> properties)
			throws Exception {

		servicePid = (String) properties.get(Constants.SERVICE_PID);
		this.config = config;
		this.bundleContext = bundleContext;
		executorGroup.submit(correctlySetTargets()).addListener((f) -> {
			if (!f.isSuccess()) {
				logger.error("Could not set frontend targets correctly, cause: {}", f.cause());
			}
		});
		activateFuture = executorGroup.submit(registerManagedServiceFactories());

	}

	// Defined so we can dynamically change targets that need changing
	@Modified
	void modified() {
	}

	@Deactivate
	void deactivate() {
		activateFuture.addListener((f) -> {
			executorGroup.submit(unregisterManagedServiceFactories());
			executorGroup.submit(deleteAllConfigs());
		});
	}

	// PACKAGE PUBLIC (for testing)

	Callable<Void> correctlySetTargets() {
		return () -> {
			String servicePidFilter = String.format("(%s=%s)", Constants.SERVICE_PID, servicePid);
			final org.osgi.service.cm.Configuration[] configurations = configAdmin.listConfigurations(servicePidFilter);

			if (configurations.length == 0 || configurations.length > 1) {
				String prefix = configurations.length == 0 ? "No" : "Too many";
				throw new IllegalStateException(
						String.format("%s configurations found for self with filter '%s'", prefix, servicePidFilter));
			}

			final org.osgi.service.cm.Configuration configuration = configurations[0];
			final Dictionary<String, Object> props = configuration.getProperties();
			final Hashtable<String, Object> hashtable = new Hashtable<>();
			final Enumeration<String> keys = props.keys();
			while (keys.hasMoreElements()) {
				final String key = keys.nextElement();
				hashtable.put(key, props.get(key));
			}

			// Target netty client using our service pid
			final String clientTarget = String.format("(%s=%s)", NettyApi.NettyClient.SERVER_APP_NAME, servicePid);
			hashtable.put(ReferenceName.FrontendHandler.NETTY_CLIENTS_TARGET, clientTarget);

			// Target channel handler context registered by backend. The backend will get
			// our ID after we register our channel as a service
			final String backendContextTarget = String.format("(%s=%s)",
					ProxyProviderApi.ChannelHandlerContext.SERVER_APP_NAME, servicePid);
			hashtable.put(ReferenceName.FrontendHandler.BACKEND_CONTEXTS_TARGET, backendContextTarget);
			configuration.update(hashtable);
			return null;
		};
	}

	Callable<Void> registerManagedServiceFactories() {
		return () -> {
			// Channel factory
			final Hashtable<String, Object> channelProps = new Hashtable<>(1);
			// TODO: consider PIDs based on the FE name
			channelProps.put(Constants.SERVICE_PID, ChannelManagedServiceFactory.SERVICE_PID);
			channelFactoryRegistration = bundleContext.registerService(ManagedServiceFactory.class,
					new ChannelManagedServiceFactory(bundleContext, channels), channelProps);

			// Event loop group factory
			final Hashtable<String, Object> elgProps = new Hashtable<>(1);
			elgProps.put(Constants.SERVICE_PID, EventLoopGroupManagedServiceFactory.SERVICE_PID);
			eventLoopGroupRegistration = bundleContext.registerService(ManagedServiceFactory.class,
					new EventLoopGroupManagedServiceFactory(bundleContext, channels), elgProps);
			return null;
		};
	}

	Callable<Void> unregisterManagedServiceFactories() {
		return () -> {
			Stream.of(channelFactoryRegistration, eventLoopGroupRegistration).forEach(r -> {
				// copy locally since volatile
				final ServiceRegistration<ManagedServiceFactory> reg = r;
				if (reg != null)
					reg.unregister();
			});
			return null;
		};
	}

	// TODO: Move to config util in some way, shape, or form!
	Callable<String> createChannelConfig(String channelId) {
		return () -> {
			return configUtil.createChannelConfig(ChannelManagedServiceFactory.SERVICE_PID, config.appName(),
					config.inetHost(), config.inetPort(), channelId);
		};
	}

	Callable<String> createEventLoopGroupConfig(String channelId) {
		return () -> {
			// FIXME? using channel ID as app name hack
			return configUtil.createEventLoopGroup(channelId, config.inetHost(), config.inetPort(),
					NettyApi.Bootstrap.Reference.EVENT_LOOP_GROUP);
		};
	}

	Callable<String> getOrCreateBootstrapProviderConfig(String appName, String inetHost, int inetPort,
			List<String> factoryPids, List<String> handlerNames, Optional<Map<String, Object>> extraProperties,
			Optional<String> serverAppName) {
		return () -> {
			Set<String> pids = configUtil.getOrCreate(Stream.of(NettyApi.Bootstrap.PID).collect(Collectors.toList()),
					configUtil.bootstrapProperties(appName, inetHost, inetPort, factoryPids, handlerNames,
							extraProperties, serverAppName))
					.call();

			if (pids.size() != 1) {
				final String message = "Invalid results returned from getOrCreateBootstrapProviderConfig";
				logger.error(message);
				throw new IllegalStateException(message);
			}
			return pids.iterator().next();
		};
	}

	Callable<List<String>> getOrCreateChannelInitializer(String appName, String hostname, int port,
			List<String> factoryPids, List<String> handlerNames, Optional<Map<String, Object>> extraProperties) {
		return () -> {
			List<String> results = new ArrayList<>();
			Set<String> pids = configUtil
					.getOrCreate(Stream.of(NettyApi.ChannelInitializer.PID).collect(Collectors.toList()),
							configUtil.channelInitializerProperties(appName, hostname, port,
									factoryPids.toArray(ConfigurationUtil.EMPTY_ARRAY),
									handlerNames.toArray(ConfigurationUtil.EMPTY_ARRAY), extraProperties))
					.call();
			if (pids.size() > 1) {
				final String message = "Too many results returned from getOrCreateChannelInitializer";
				logger.error(message);
				throw new IllegalStateException(message);
			}
			results.addAll(pids);
			pids = configUtil.getOrCreate(Stream.of(NettyApi.EventExecutorGroup.PID).collect(Collectors.toList()),
					configUtil.eventExecutorGroupProperties(appName, hostname, port,
							ReferenceName.ChannelInitializer.EVENT_EXECUTOR_GROUP))
					.call();
			if (pids.size() > 1) {
				final String message = "Too many results returned from getOrCreateChannelInitializer";
				logger.error(message);
				throw new IllegalStateException(message);
			}
			results.addAll(pids);
			return results;
		};

		// return () -> {
		// return configUtil.createChannelInitializer(appName, hostname, port,
		// factoryPids, handlerNames,
		// extraProperties);
		// };
	}

	Callable<String> getOrCreateNettyClientConfig(String appName, String hostname, Integer port,
			List<String> factoryPids, List<String> handlerNames, Optional<Map<String, Object>> extraProperties,
			Optional<String> serverAppName, Optional<Boolean> shutdownGroup) {
		return () -> {

			List<String> results = new ArrayList<>();
			Set<String> pids = configUtil.getOrCreate(Stream.of(NettyApi.NettyClient.PID).collect(Collectors.toList()),
					configUtil.nettyClientProperties(appName, hostname, port, factoryPids, handlerNames,
							extraProperties, serverAppName, shutdownGroup))
					.call();
			if (pids.size() != 1) {
				final String message = "Invalid results returned from getOrCreateNettyClientConfig";
				logger.error(message);
				throw new IllegalStateException(message);
			}
			return pids.iterator().next();
		};
	}

	Callable<Void> deleteAllConfigs() {
		return () -> {
			Map<String, List<String>> channelsToPids = this.channelsToPids;
			this.channelsToPids.clear();

			channelsToPids.values().parallelStream().forEach(l -> {
				if (l != null && !l.isEmpty()) {
					try {
						configUtil.deleteConfigurationPids(l);
					} catch (Exception e) {
						logger.debug("Potentially avoidable exception when deleting configs: {}", e);
					}
				}
			});
			return null;
		};
	}

	Callable<List<String>> deleteConfigs(String channelId) {
		return () -> {
			final List<String> pids = channelsToPids.remove(channelId);
			if (pids != null && !pids.isEmpty()) {
				configUtil.deleteConfigurationPids(pids);
			}
			return pids;
		};
	}

	Callable<Void> deletePids(List<String> pids) {
		return () -> {
			configUtil.deleteConfigurationPids(pids);
			return null;
		};
	}

	// Callable<List<String>> createNettyClientConfigurations(ChannelHandlerContext
	// frontendCtx, InetKey key) {
	// return () -> {
	//
	// final String channelId = handlerUtils.channelId(frontendCtx);

	// // Construct a custom channel pid using LDAP * to search for all.
	// // Working example of LDAP injection ;)
	// final Hashtable<String, Object> pid = new Hashtable<>(1);
	// setChannelPid("*", pid);
	// String frontendChannelTarget = configUtil.channelTarget(getPidKey(),
	// getPidValue(pid), config.appName(),
	// config.inetHost(), config.inetPort(), channelId);
	//
	// final Map<String, Object> extraProperties = new HashMap<>();
	// extraProperties.put(ReferenceName.BackendHandler.FRONTEND_CHANNEL_TARGET,
	// frontendChannelTarget);
	//
	// // Supply our channel ID for the backend. It'll use this to register its
	// context
	// extraProperties.put(ProxyProviderApi.BackendHandler.FRONTEND_CHANNEL_ID,
	// channelId);
	//
	// final Optional<Map<String, Object>> optionalExtras =
	// Optional.of(extraProperties);
	// final List<String> clientFactoryPids =
	// Arrays.asList(config.clientFactoryPids());
	// final List<String> clientHandlerNames =
	// Arrays.asList(config.clientHandlerNames());
	//
	// final Optional<String> serverAppName = Optional.of(config.appName());

	// Simulate createNettyClient

	// Create the boostrap provider
	// configPids.add(configUtil.createBootstrapProvider(myChannelId, inetHost,
	// inetPort, serverAppName));

	// configPids.add(configUtil.createEventLoopGroup(channelId,
	// ReferenceName.NettyClient.EVENT_LOOP_GROUP));

	// // and channel initializer
	// configPids.addAll(configUtil.createChannelInitializer(myChannelId, inetHost,
	// inetPort, clientFactoryPids,
	// clientHandlerNames, optionalExtras));
	//
	// // Do not shutdown the event loop group, since it's from the server
	// bootstrap's
	// // worker group
	// configPids.add(configUtil.createNettyClientConfig(myChannelId, inetHost,
	// inetPort, clientFactoryPids,
	// clientHandlerNames, optionalExtras, serverAppName, /* shutdownGroup */
	// Optional.of(false)));
	// return null;
	// };
	// }

	// SERVICE METHODS

	/**
	 * Create frontend channel configuration
	 * 
	 * Note: running on I/O thread
	 */
	@Override
	public void handlerAdded(ChannelHandlerContext frontendCtx) throws Exception {
		// Call super first
		super.handlerAdded(frontendCtx);
		final String channelId = handlerUtils.channelId(frontendCtx);

		final ConfigPidsListener<String> listener = new ConfigPidsListener<String>(channelId, channelsToPids, logger);

		// Run on a separate executor since it's not directly I/O
		executorGroup.submit(createChannelConfig(channelId)).addListener(listener);
		executorGroup.submit(createEventLoopGroupConfig(channelId)).addListener(listener);
	}

	/**
	 * Delete frontend channel configuration
	 * 
	 * Note: running on I/O thread
	 */
	@Override
	public void handlerRemoved(ChannelHandlerContext frontendCtx) throws Exception {
		// Call super first
		super.handlerRemoved(frontendCtx);

		final String channelId = handlerUtils.channelId(frontendCtx);
		// Run on a separate executor since it's not directly I/O
		executorGroup.submit(deleteConfigs(channelId));
	}

	/**
	 * Create netty client and associated configurations
	 * 
	 * Note: running on I/O thread
	 */
	@Override
	protected void onInetKey(ChannelHandlerContext frontendCtx, InetKey key) {

		final String channelId = handlerUtils.channelId(frontendCtx);

		final ConfigPidsListener<String> listener = new ConfigPidsListener<String>(channelId, channelsToPids, logger);

		// Construct a custom channel pid using LDAP * to search for all.
		// Working example of LDAP injection ;)
		// [CM Configuration Updater (Update:
		// pid=io.blesmol.netty.proxy.provider.ChannelManagedServiceFactory.9744b6ba-0733-4d70-bf09-1765557d845b)]
		// DEBUG io.blesmol.netty.proxy.provider.ChannelManagedServiceFactory -
		// Registering channel [id: 0x8e67266a, L:/127.0.0.1:54333 - R:/127.0.0.1:56682]
		// with ID 6c4008fffeb796be-000035af-00000003-7b66de3e232b3df7-8e67266a, using
		// PID
		// io.blesmol.netty.proxy.provider.ChannelManagedServiceFactory.9744b6ba-0733-4d70-bf09-1765557d845b
		// and properties
		// {appName=io.blesmol.netty.proxy.test.FrontendBackendRoundtripTest:proxy,
		// channelId=6c4008fffeb796be-000035af-00000003-7b66de3e232b3df7-8e67266a,
		// inetHost=localhost, inetPort=54333,
		// service.factoryPid=io.blesmol.netty.proxy.provider.ChannelManagedServiceFactory,
		// service.pid=io.blesmol.netty.proxy.provider.ChannelManagedServiceFactory.9744b6ba-0733-4d70-bf09-1765557d845b}
		// final Hashtable<String, Object> pid = new Hashtable<>(1);
		// setChannelPid("*", pid);
		String frontendChannelTarget = configUtil.channelTarget(config.appName(), config.inetHost(), config.inetPort(),
				channelId);
		final List<String> clientFactoryPids = Arrays.asList(config.clientFactoryPids());
		final List<String> clientHandlerNames = Arrays.asList(config.clientHandlerNames());
		// Use our pid as a unique identifier
		final Optional<String> serverAppName = Optional.of(servicePid);
		final Map<String, Object> extraProperties = new HashMap<>();
		extraProperties.put(ReferenceName.BackendHandler.FRONTEND_CHANNEL_TARGET, frontendChannelTarget);
		// Supply our PID for the backend. It'll use this to register its context
		extraProperties.put(ProxyProviderApi.BackendHandler.FRONTEND_SERVICE_PID, servicePid);
		final Optional<Map<String, Object>> optionalExtras = Optional.of(extraProperties);

		// Run on our local executor since the OSGi calls are not directly I/O related
		executorGroup.submit(getOrCreateBootstrapProviderConfig(channelId, key.inetHost, key.inetPort,
				clientFactoryPids, clientHandlerNames, optionalExtras, serverAppName)).addListener(listener);

		executorGroup.submit(getOrCreateChannelInitializer(channelId, key.inetHost, key.inetPort, clientFactoryPids,
				clientHandlerNames, optionalExtras)).addListener(listener);

		executorGroup.submit(getOrCreateNettyClientConfig(channelId, key.inetHost, key.inetPort, clientFactoryPids,
				clientHandlerNames, optionalExtras, serverAppName, /* groups belong to server */Optional.of(false)));
	}
	// Create all the netty configs

	// @Modified
	// void modified(Map<String, ?> properties) {
	// System.out.println(String.format("Modifying %s with properties %s", this,
	// properties));
	// // this.config = config;
	// }
	//
	// @Deactivate
	// void deactivate(ProxyProviderApi.FrontendHandler config) {
	// System.out.println("Deactivating frontend handler " + this);
	// this.config = config;
	//
	// // TODO: delete all pids
	// close();
	// }

	// Resolves if we successfully updated our configuration
	// private Promise<Void> updateSelfConfiguration(String channelId) {
	// Deferred<Void> result = new Deferred<>();
	//
	// // Ensure this does not run on activate thread
	// executor.execute(new Runnable() {
	//
	// @Override
	// public void run() {
	//
	// try {
	// String servicePidFilter = String.format("(%s=%s)", Constants.SERVICE_PID,
	// servicePid);
	// final org.osgi.service.cm.Configuration[] configurations = configAdmin
	// .listConfigurations(servicePidFilter);
	//
	// if (configurations.length == 0 || configurations.length > 1) {
	// String prefix = configurations.length == 0 ? "No" : "Too many";
	// result.fail(new IllegalStateException(String.format(
	// "%s configurations found for self with filter '%s'", prefix,
	// servicePidFilter)));
	// return;
	// }
	//
	// final org.osgi.service.cm.Configuration configuration = configurations[0];
	// final Dictionary<String, Object> props = configuration.getProperties();
	// final Hashtable<String, Object> hashtable = new Hashtable<>();
	// final Enumeration<String> keys = props.keys();
	// while (keys.hasMoreElements()) {
	// final String key = keys.nextElement();
	// hashtable.put(key, props.get(key));
	// }
	//
	// // Target netty client using our channel id
	// final String clientTarget = String.format("(%s=%s)",
	// NettyApi.NettyClient.APP_NAME, channelId);
	// hashtable.put(ReferenceName.FrontendHandler.NETTY_CLIENT_TARGET,
	// clientTarget);
	//
	// // Target channel handler context registered by backend. The backend will get
	// // our ID after we register our channel as a service
	// final String backendContextTarget = String.format("(%s=%s)",
	// ProxyProviderApi.ChannelHandlerContext.CHANNEL_ID, channelId);
	// hashtable.put(ReferenceName.FrontendHandler.BACKEND_CONTEXT_TARGET,
	// backendContextTarget);
	//
	// try {
	// configuration.update(hashtable);
	// } catch (IOException e) {
	// System.out.println("Error updating configuration in " +
	// FrontendHandlerProvider.this);
	// deferredChannel.fail(e);
	// return;
	// }
	// result.resolve(null);
	// System.out.println(String.format("updateSelfConfiguration %s with properties
	// %s", FrontendHandlerProvider.this, hashtable));
	//
	// } catch (Exception e) {
	// System.out.println("Configuration not created successfully in " +
	// FrontendHandlerProvider.this);
	// result.fail(e);
	// }
	// }
	// });
	//
	// return result.getPromise();
	// }
	//
	// private Promise<Void>
	// registerEventLoopAndcreateConfigurations(Promise<Channel> promisedChannel,
	// InetSocketAddress socketAddress) {
	//
	// Deferred<Void> result = new Deferred<>();
	//
	// executor.execute(() -> {
	//
	// promisedChannel.onResolve(() -> {
	// try {
	// final Channel myChannel = promisedChannel.getValue();
	// final String myChannelId = myChannel.id().asLongText();
	// final String inetHost = socketAddress.getHostName();
	// final int inetPort = socketAddress.getPort();
	//
	// // Register our event loop for our client
	// // Hashtable<String, Object> eventLoopProperties = new Hashtable<>();
	// // eventLoopProperties.put(NettyApi.EventLoopGroup.APP_NAME, myChannelId);
	// // eventLoopProperties.put(NettyApi.EventLoopGroup.INET_HOST, inetHost);
	// // eventLoopProperties.put(NettyApi.EventLoopGroup.INET_PORT, inetPort);
	// // eventLoopProperties.put(NettyApi.EventLoopGroup.GROUP_NAME,
	// // io.blesmol.netty.api.ReferenceName.NettyClient.EVENT_LOOP_GROUP);
	// // loopRegistration = bundleContext.registerService(EventLoopGroup.class,
	// // myChannel.eventLoop(),
	// // eventLoopProperties);
	// // System.out.println(String.format("Registered event loop %s for %s:%s:%d",
	// // myChannel.eventLoop(), myChannelId, inetHost, inetPort));
	//
	// // Create channel initializer and client app
	// final Map<String, Object> extraProperties = new HashMap<>();
	//
	// // Gratuitously set the backend's frontend channel target to our channel ID
	// // The backend could do this too on its activate, since we also supply our
	// // channel ID below
	// String frontendChannelTarget = String.format("(%s=%s)",
	// ProxyProviderApi.Channel.CHANNEL_ID,
	// myChannelId);
	// extraProperties.put(ReferenceName.BackendHandler.FRONTEND_CHANNEL_TARGET,
	// frontendChannelTarget);
	//
	// // Supply our channel ID for the backend. It'll use this to register its
	// context
	// extraProperties.put(ProxyProviderApi.BackendHandler.FRONTEND_CHANNEL_ID,
	// myChannelId);
	//
	// final Optional<Map<String, Object>> optionalExtras =
	// Optional.of(extraProperties);
	// final List<String> clientFactoryPids =
	// Arrays.asList(config.clientFactoryPids());
	// final List<String> clientHandlerNames =
	// Arrays.asList(config.clientHandlerNames());
	//
	// final Optional<String> serverAppName = Optional.of(config.appName());
	//
	// // Simulate createNettyClient
	//
	// // Create the boostrap provider
	// configPids.add(configUtil.createBootstrapProvider(myChannelId, inetHost,
	// inetPort, serverAppName));
	//
	// // configPids.add(configUtil.createEventLoopGroup(channelId,
	// // ReferenceName.NettyClient.EVENT_LOOP_GROUP));
	//
	// // and channel initializer
	// configPids.addAll(configUtil.createChannelInitializer(myChannelId, inetHost,
	// inetPort,
	// clientFactoryPids, clientHandlerNames, optionalExtras));
	//
	// // Do not shutdown the event loop group, since it's from the server
	// bootstrap's
	// // worker group
	// configPids.add(configUtil.createNettyClientConfig(myChannelId, inetHost,
	// inetPort,
	// clientFactoryPids, clientHandlerNames, optionalExtras, serverAppName,
	// /* shutdownGroup */ Optional.of(false)));
	//
	// result.resolve(null);
	// System.out.println("Configuration created successfully");
	// } catch (Exception e) {
	// System.out.println("Configuration not created successfully!");
	// result.fail(e);
	// }
	//
	// });
	// });
	//
	// return result.getPromise();
	//
	// }

	@Override
	public String toString() {
		return servicePid;
	}

	// public class ChannelManagedServiceFactory implements ManagedServiceFactory {
	//
	// public static final String SERVICE_PID =
	// "io.blesmol.netty.proxy.provider.FrontendHandlerProvider.ChannelManagedServiceFactory";
	//
	// private Map<String, ServiceRegistration<Channel>> registrations = new
	// ConcurrentHashMap<>();
	//
	// @Override
	// public String getName() {
	// return "Frontend managed service factory to register channels";
	// }
	//
	// @Override
	// public void updated(String pid, Dictionary<String, ?> properties) throws
	// ConfigurationException {
	//
	// if (registrations.containsKey(pid)) {
	// logger.warn("Modifying channel PID {} is not supported. Properties: {}", pid,
	// properties);
	// return;
	// }
	//
	//// final Hashtable<String, Object> serviceProperties = new Hashtable<>();
	// // Enumeration<String> keys = properties.keys();
	// // while (keys.hasMoreElements()) {
	// // final String key = keys.nextElement();
	// // serviceProperties.put(key, properties.get(key));
	// // }
	// // Include our service factory pid
	// // TODO: verify if needed, note outcome
	// // serviceProperties.put(ConfigurationAdmin.SERVICE_FACTORYPID, SERVICE_PID);
	//
	// String channelId = (String) properties.get(NettyApi.Channel.CHANNEL_ID);
	//
	// final Channel channel = channelId != null ? channels.get(channelId) : null;
	// if (channelId == null || channel == null) {
	// logger.error("PID {} provided missing or invalid channel ID in its properties
	// {}.", pid, properties);
	// return;
	// }
	//
	// logger.debug("Registering PID {}.", pid);
	//
	// final ServiceRegistration<Channel> registration =
	// bundleContext.registerService(Channel.class, channel,
	// properties);
	// registrations.put(pid, registration);
	// }
	//
	// @Override
	// public void deleted(String pid) {
	// ServiceRegistration<Channel> registration = registrations.remove(pid);
	// if (registration != null) {
	// logger.warn("Unregistering PID {}.", pid);
	// registration.unregister();
	// }
	// }
	//
	// }
	//
	// /**
	// * A managed service factory for frontend channels
	// */
	// public class EventLoopGroupManagedServiceFactory implements
	// ManagedServiceFactory {
	//
	// public static final String SERVICE_PID =
	// "io.blesmol.netty.proxy.provider.FrontendHandlerProvider.EventLoopGroupManagedServiceFactory";
	//
	// private Map<String, ServiceRegistration<EventLoopGroup>> registrations = new
	// ConcurrentHashMap<>();
	//
	// @Override
	// public String getName() {
	// return "Frontend managed service factory to register channels";
	// }
	//
	// @Override
	// public void updated(String pid, Dictionary<String, ?> properties) throws
	// ConfigurationException {
	//
	// if (registrations.containsKey(pid)) {
	// logger.warn("Modifying event loop group PID {} is not supported. Properties:
	// {}", pid, properties);
	// return;
	// }
	//
	// // Enumeration<String> keys = properties.keys();
	// // while (keys.hasMoreElements()) {
	// // final String key = keys.nextElement();
	// // serviceProperties.put(key, properties.get(key));
	// // }
	// // Include our service factory pid
	// // TODO: verify if needed, note outcome
	// // serviceProperties.put(ConfigurationAdmin.SERVICE_FACTORYPID, SERVICE_PID);
	//
	// // appname hack is used to store channel id
	// final String channelId = (String)
	// properties.get(NettyApi.EventLoopGroup.APP_NAME);
	// final String inetHost = (String)
	// properties.get(NettyApi.EventLoopGroup.INET_HOST);
	// final Integer inetPort = (Integer)
	// properties.get(NettyApi.EventLoopGroup.INET_PORT);
	// final String groupName = (String)
	// properties.get(NettyApi.EventLoopGroup.GROUP_NAME);
	//
	// final Channel channel = channelId != null ? channels.get(channelId) : null;
	// if (channelId == null || inetHost == null || inetPort == -1 || groupName ==
	// null) {
	// logger.error("PID {} missing required or provided invalid event loop group
	// properties: {}.", pid,
	// properties);
	// return;
	// }
	//
	// logger.debug("Registering PID {}.", pid);
	// final ServiceRegistration<EventLoopGroup> registration =
	// bundleContext.registerService(EventLoopGroup.class, channel.eventLoop(),
	// properties);
	// registrations.put(pid, registration);
	//
	//// registeredEventLoopGroups.put(channelId, registration);
	//
	// }
	//
	// @Override
	// public void deleted(String pid) {
	// ServiceRegistration<EventLoopGroup> registration = registrations.remove(pid);
	// if (registration != null) {
	// logger.warn("Unregistering PID {}.", pid);
	// registration.unregister();
	// }
	//
	// }
	// }

}
