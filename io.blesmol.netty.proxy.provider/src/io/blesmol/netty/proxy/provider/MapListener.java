package io.blesmol.netty.proxy.provider;

import java.util.Map;

import org.slf4j.Logger;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class MapListener<K, V> implements GenericFutureListener<Future<K>> {

	private final Map<K, V> target;
	private final V value;
	private boolean put;
	private final Logger logger;

	public MapListener(Map<K, V> target, V value, Logger logger) {
		this(target, value, true, logger);
	}
	
	public MapListener(Map<K, V> target, V value, boolean put, Logger logger) {
		super();
		this.target = target;
		this.value = value;
		this.put = put;
		this.logger = logger;
	}

	@Override
	public void operationComplete(Future<K> future) throws Exception {
		if (future.isSuccess()) {
			if (put) {
				target.put(future.get(), value);
			} else {
				target.remove(future.get());
			}
		} else {
			logger.warn("Error on future {} targeting {} with value {}, caused by: {}", future, target, value,
					future.cause());
		}
	}

}
