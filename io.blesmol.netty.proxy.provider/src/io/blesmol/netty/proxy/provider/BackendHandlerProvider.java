package io.blesmol.netty.proxy.provider;

import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

import io.blesmol.netty.api.EventExecutorGroupHandler;
import io.blesmol.netty.proxy.api.BackendHandler;
import io.blesmol.netty.proxy.api.ProxyApi;
import io.blesmol.netty.proxy.api.ReferenceName;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.EventExecutorGroup;

@Component(configurationPid = ProxyApi.BackendHandler.PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = ChannelHandler.class)
public class BackendHandlerProvider extends ChannelInboundHandlerAdapter
		implements BackendHandler, EventExecutorGroupHandler {

	// Only set in activate, does not need to be volatile
	private String frontendChannelId = null;
	private String servicePid;
	private String channelId;
	private String appName;
	private BundleContext bundleContext = null;

	// Resolved when channel is active, either when handler added or channel active is called
	private final Deferred<Void> channelActiveDeferred = new Deferred<>();
	private final Promise<Void> channelActivePromise = channelActiveDeferred.getPromise();
	
	// Registration for our context, which we need to clean up
	private volatile ServiceRegistration<ChannelHandlerContext> contextRegistration = null;

	@Reference(name = ReferenceName.BackendHandler.EVENT_EXECUTOR_GROUP)
	EventExecutorGroup eventExecutorGroup;

	volatile Channel frontendChannel;
	@Reference(name = ReferenceName.BackendHandler.FRONTEND_CHANNEL)
	void setFrontendChannel(Channel frontendChannel) {
		this.frontendChannel = frontendChannel;
		System.out.println(String.format("Set frontend channel %s on %s", frontendChannel, super.toString()));
	}

	// Use channel context executor for I/O related tasks
	@Reference
	ExecutorService executor;

	@Activate
	void activate(ProxyApi.BackendHandler config, BundleContext bundleContext, Map<String, Object> properties) {
		this.frontendChannelId = config.frontendChannelId();
		this.servicePid = (String)properties.get(Constants.SERVICE_PID);
		this.channelId = config.channelId();
		this.appName = config.appName();
		this.bundleContext = bundleContext;
		System.out.println("Activated " + this);
	}
	
	@Deactivate
	void deactivate() {
		ServiceRegistration<ChannelHandlerContext> contextRegistration = this.contextRegistration;
		if (contextRegistration != null) {
			System.out.println("Unregistering context in " + this);
			contextRegistration.unregister();
		}

	}
	

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		
		if (ctx.channel().isActive()) {
			channelActiveDeferred.resolve(null);
		}

		// Register our context via the frontend's channel ID, on a non-IO thread
		executor.execute(() -> {
			channelActivePromise.onResolve(() -> {
				Hashtable<String, Object> properties = new Hashtable<>();
				properties.put(ProxyApi.ChannelHandlerContext.CHANNEL_ID, frontendChannelId);
				contextRegistration = bundleContext.registerService(ChannelHandlerContext.class, ctx, properties);
			});
		});
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		System.out.println("About to read on backend " + this);
		
		if (!channelActivePromise.isDone()) {
			channelActiveDeferred.resolve(null);
		}
		ctx.read();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		System.out.println(String.format("Backend %s read %s", this, msg));
		frontendChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {

			@Override
			public void operationComplete(ChannelFuture future) {
				if (future.isSuccess()) {
					System.out.println(String.format("Backend %s wrote and flushed frontend. Now calling read on frontend", BackendHandlerProvider.this));
					ctx.channel().read();
				} else {
					System.out.println(String.format("Backend %s had a problem writing and flushing frontend. Now closing channel.", BackendHandlerProvider.this));
					future.channel().close();
				}
			}
		});

	}

//	@Override
//	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
//		frontendChannel.flush();
//		System.out.println("Backend read completely");
//	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		FrontendHandlerProvider.closeOnFlush(frontendChannel);
		System.out.println("Backend inactive " + this);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		FrontendHandlerProvider.closeOnFlush(ctx.channel());
	}

	@Override
	public EventExecutorGroup getEventExecutorGroup() {
		return eventExecutorGroup;
	}

	@Override
	public String toString() {
		return String.format("%s:%s:%s:[frontend]%s:[backend]%s", super.toString(), servicePid, appName, frontendChannelId, channelId);
	}
	
	
}
