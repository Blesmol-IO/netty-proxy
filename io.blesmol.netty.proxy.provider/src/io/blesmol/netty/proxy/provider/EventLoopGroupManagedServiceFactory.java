package io.blesmol.netty.proxy.provider;

import java.util.Dictionary;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.blesmol.netty.api.NettyApi;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;

/**
 * A managed service factory for event loop groups
 */
public class EventLoopGroupManagedServiceFactory implements ManagedServiceFactory {

	private static final Logger logger = LoggerFactory.getLogger(EventLoopGroupManagedServiceFactory.class);
	
	public static final String SERVICE_PID = "io.blesmol.netty.proxy.provider.EventLoopGroupManagedServiceFactory";

	private Map<String, ServiceRegistration<EventLoopGroup>> registrations = new ConcurrentHashMap<>();

	private final BundleContext bundleContext;
	private final Map<String, Channel> channels;

	public EventLoopGroupManagedServiceFactory(BundleContext bundleContext, Map<String, Channel> channels) {
		this.bundleContext = bundleContext;
		this.channels = channels;
	}

	@Override
	public String getName() {
		return "Frontend managed service factory to register event loop groups";
	}

	@Override
	public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {

		if (registrations.containsKey(pid)) {
			logger.warn("Modifying event loop group PID {} is not supported. Properties: {}", pid, properties);
			return;
		}

		// Enumeration<String> keys = properties.keys();
		// while (keys.hasMoreElements()) {
		// final String key = keys.nextElement();
		// serviceProperties.put(key, properties.get(key));
		// }
		// Include our service factory pid
		// TODO: verify if needed, note outcome
		// serviceProperties.put(ConfigurationAdmin.SERVICE_FACTORYPID, SERVICE_PID);

		// appname hack is used to store channel id
		final String channelId = (String) properties.get(NettyApi.EventLoopGroup.APP_NAME);
		final String inetHost = (String) properties.get(NettyApi.EventLoopGroup.INET_HOST);
		final Integer inetPort = (Integer) properties.get(NettyApi.EventLoopGroup.INET_PORT);
		final String groupName = (String) properties.get(NettyApi.EventLoopGroup.GROUP_NAME);

		final Channel channel = channelId != null ? channels.get(channelId) : null;
		if (channelId == null || inetHost == null || inetPort == -1 || groupName == null) {
			logger.error("PID {} missing required or provided invalid event loop group properties: {}.", pid,
					properties);
			return;
		}

		logger.debug("Registering PID {}.", pid);
		final ServiceRegistration<EventLoopGroup> registration = bundleContext.registerService(EventLoopGroup.class, channel.eventLoop(),
				properties);
		registrations.put(pid, registration);
		
//		registeredEventLoopGroups.put(channelId, registration);

	}

	@Override
	public void deleted(String pid) {
		ServiceRegistration<EventLoopGroup> registration = registrations.remove(pid);
		if (registration != null) {
			logger.warn("Unregistering PID {}.", pid);
			registration.unregister();
		}

	}
}

