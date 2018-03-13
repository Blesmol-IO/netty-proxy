package io.blesmol.netty.proxy.provider;

import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Callable;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.proxy.api.HandlerUtils;
import io.blesmol.netty.proxy.api.ProxyProviderApi;
import io.blesmol.netty.proxy.api.ReferenceName;
import io.blesmol.netty.proxy.handler.BackendHandlerImpl;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;

@Component(configurationPid = ProxyProviderApi.BackendHandler.PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = ChannelHandler.class)
public class BackendHandlerProvider extends BackendHandlerImpl {
	
	// Set in activate, does not need to be volatile
	private String frontendServicePid;
	private String inetHost;
	private int inetPort;
	private String servicePid;
	private String channelId;
	private String appName;
	private BundleContext bundleContext = null;

	// TODO: consider using a shared executor amongst all backends
	protected final EventExecutorGroup executorGroup = new DefaultEventExecutor(); // single thread?
	
	// Volatile might be overkill?
	private volatile Future<Void> handlerAdded;
	
	// Registration for our context, which we need to clean up
	private volatile ServiceRegistration<ChannelHandlerContext> contextRegistration;
	
	@Reference(name = ReferenceName.BackendHandler.FRONTEND_CHANNEL)
	@Override
	protected void setFrontendChannel(Channel frontendChannel) {
		super.setFrontendChannel(frontendChannel);
		logger.debug("Backend setting frontend channel {}", frontendChannel);
	}

	@Reference
	@Override
	protected void setHandlerUtils(HandlerUtils handlerUtils) {
		super.setHandlerUtils(handlerUtils);
	}

	@Reference
	ConfigurationUtil configUtil;
	
	@Activate
	void activate(ProxyProviderApi.BackendHandler config, BundleContext bundleContext, Map<String, Object> properties) {
		this.frontendServicePid = config.frontendServicePid();
		this.servicePid = (String)properties.get(Constants.SERVICE_PID);
		this.channelId = config.channelId();
		this.appName = config.appName();
		this.inetHost = config.inetHost();
		this.inetPort = config.inetPort();
		this.bundleContext = bundleContext;
		logger.debug("Activated");
	}
	
	@Deactivate
	void deactivate() {
		ServiceRegistration<ChannelHandlerContext> contextRegistration = this.contextRegistration;
		if (contextRegistration != null) {
			logger.debug("Unregistering backend context");
			contextRegistration.unregister();
		}
		
		// TODO remove

	}
	
	Callable<Void> registerChannelHandlerContext(ChannelHandlerContext ctx) {
		return () -> {
			// TODO: move to proxy util
			final Hashtable<String, Object> properties = new Hashtable<>(1);
			properties.put(ProxyProviderApi.ChannelHandlerContext.APP_NAME, frontendChannel.id().asLongText());
			properties.put(ProxyProviderApi.ChannelHandlerContext.INET_HOST, inetHost);
			properties.put(ProxyProviderApi.ChannelHandlerContext.INET_PORT, inetPort);
			properties.put(ProxyProviderApi.ChannelHandlerContext.SERVER_APP_NAME, frontendServicePid);
			logger.debug("Registering backend context for channel {} with properties {}", ctx.channel(), properties);
			contextRegistration = bundleContext.registerService(ChannelHandlerContext.class, ctx, properties);
			return null;
		};
	}
	Callable<Void> unregisterChannelHandlerContext() {
		return () -> {
			// TODO: move to proxy util
			contextRegistration.unregister();
			return null;
		};
	}
	
	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		super.handlerAdded(ctx);
		final InetSocketAddress remoteAddress = (InetSocketAddress)ctx.channel().remoteAddress();
		handlerAdded = executorGroup.submit(registerChannelHandlerContext(ctx)).addListener((f) -> {
			if (!f.isSuccess()) {
				logger.error("Error registering channel handle context {}, cause: {}", ctx, f.cause());
			}
		});
	}
	
	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		super.handlerRemoved(ctx);
		// Volatile, non-IO
		Future<Void> handlerAdded = this.handlerAdded;
		if (handlerAdded != null) {
			handlerAdded.addListener((f) -> {
				executorGroup.submit(unregisterChannelHandlerContext());
			});
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		logger.debug("Reading on backend channel {}", ctx.channel());
		ctx.read();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		logger.debug("Backend reading on channel {}", ctx.channel());
		frontendChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {

			@Override
			public void operationComplete(ChannelFuture future) {
				if (future.isSuccess()) {
					logger.debug("Backend wrote and flushed on frontend channel {}, now calling read on it.", future.channel());
					ctx.channel().read();
				} else {
					logger.warn("Backend could not write and flush to frontend channel via cause: {}", future.cause());
//					future.channel().close();
				}
			}
		});

	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		logger.debug("Backend going inactive for channel {}", ctx.channel());
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		logger.error("Received exception on backend handler via cause: {}", cause);
		handlerUtils.closeOnFlush(ctx.channel());
	}

	@Override
	public String toString() {
		return String.format("%s:%s:[frontend]%s:[backend]%s", servicePid, appName, frontendChannel.id().asLongText(), channelId);
	}
	
	
}
