package io.blesmol.netty.proxy.provider;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class ConfigPidsListener<K> implements GenericFutureListener<Future<Object>> {

	private final K key;
	private final Map<K, List<String>> keyedPids;
	private final Logger logger;

	public ConfigPidsListener(K key, Map<K, List<String>> keyedPids, Logger logger) {
		this.key = key;
		this.keyedPids = keyedPids;
		this.logger = logger;
	}

	@Override
	public void operationComplete(Future<Object> future) throws Exception {
		if (future.isSuccess()) {
			keyedPids.putIfAbsent(key, new CopyOnWriteArrayList<>());
			final List<String> pids = keyedPids.get(key);

			final Object thing = future.get();
			if (thing instanceof String) {
				pids.add((String) thing);
			} else if (thing instanceof Collection) {
				try {
					@SuppressWarnings("unchecked")
					Collection<String> collection = (Collection<String>) thing;
					pids.addAll(collection);
				} catch (ClassCastException e) {
					logger.error("Could not cast thing {} to Collection<String>: {}", thing, e);
				}
			} else {
				logger.warn("Unexpected thing {} passed as result of a future!", thing);
			}
		} else {
			logger.error("Error occurred when attempting to complete a configuration entry, cause: {}", future.cause());
		}
	}

}
