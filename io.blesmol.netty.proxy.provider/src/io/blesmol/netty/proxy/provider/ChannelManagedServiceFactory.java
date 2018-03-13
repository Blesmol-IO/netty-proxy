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

/**
 * A managed service factory for channels
 */
public class ChannelManagedServiceFactory implements ManagedServiceFactory {

	private static final Logger logger = LoggerFactory.getLogger(ChannelManagedServiceFactory.class);

	public static final String SERVICE_PID = "io.netty.channel.Channel";

	private Map<String, ServiceRegistration<Channel>> registrations = new ConcurrentHashMap<>();
	
	private final BundleContext bundleContext;
	private final Map<String, Channel> channels;

	public ChannelManagedServiceFactory(BundleContext bundleContext, Map<String, Channel> channels) {
		this.bundleContext = bundleContext;
		this.channels = channels;
	}

	@Override
	public String getName() {
		return "Frontend managed service factory to register channels";
	}

	@Override
	public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {

		if (registrations.containsKey(pid)) {
			logger.warn("Modifying channel PID {} is not supported. Properties: {}", pid, properties);
			return;
		}

		// TODO: verify if needed, note outcome
		
//		final Hashtable<String, Object> serviceProperties = new Hashtable<>();
		// Enumeration<String> keys = properties.keys();
		// while (keys.hasMoreElements()) {
		// final String key = keys.nextElement();
		// serviceProperties.put(key, properties.get(key));
		// }
		// Include our service factory pid
		// serviceProperties.put(ConfigurationAdmin.SERVICE_FACTORYPID, SERVICE_PID);

		String channelId = (String) properties.get(NettyApi.Channel.CHANNEL_ID);

		final Channel channel = channelId != null ? channels.get(channelId) : null;
		if (channelId == null || channel == null) {
			logger.error("PID {} provided missing or invalid channel ID in its properties {}.", pid, properties);
			return;
		}

		logger.debug("Registering channel {} with ID {}, using PID {} and properties {}", channel, channelId, pid, properties);

		final ServiceRegistration<Channel> registration = bundleContext.registerService(Channel.class, channel,
				properties);
		registrations.put(pid, registration);
	}

	@Override
	public void deleted(String pid) {
		ServiceRegistration<Channel> registration = registrations.remove(pid);
		if (registration != null) {
			logger.warn("Unregistering PID {}.", pid);
			registration.unregister();
		}
	}
}
