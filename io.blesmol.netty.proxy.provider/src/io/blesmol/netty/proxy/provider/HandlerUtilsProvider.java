package io.blesmol.netty.proxy.provider;

import org.osgi.service.component.annotations.Component;

import io.blesmol.netty.proxy.api.HandlerUtils;
import io.blesmol.netty.proxy.handler.HandlerUtilsImpl;

// TODO: move to general netty project
@Component(service = HandlerUtils.class)
public class HandlerUtilsProvider extends HandlerUtilsImpl {

}
