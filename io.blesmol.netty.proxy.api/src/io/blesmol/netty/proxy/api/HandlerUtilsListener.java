package io.blesmol.netty.proxy.api;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class HandlerUtilsListener implements GenericFutureListener<Future<HandlerUtils>> {

	@Override
	public void operationComplete(Future<HandlerUtils> future) throws Exception {
		if (future.isSuccess()) {
			onSuccess(future.get());
		} else {
			onFailure(future.cause());
		}

	}
	
	protected void onSuccess(HandlerUtils handlerUtils) {}
	
	protected void onFailure(Throwable cause) {}
}
