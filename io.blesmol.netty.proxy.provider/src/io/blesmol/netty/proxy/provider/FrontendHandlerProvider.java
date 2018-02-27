package io.blesmol.netty.proxy.provider;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.EventExecutorGroupHandler;
import io.blesmol.netty.api.NettyClient;
import io.blesmol.netty.proxy.api.Configuration;
import io.blesmol.netty.proxy.api.FrontendHandler;
import io.blesmol.netty.proxy.api.Property;
import io.blesmol.netty.proxy.api.ReferenceName;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.EventExecutorGroup;

// Copyright 2012 The Netty Project and Coastal Hacking
@Component(configurationPid = Configuration.FRONTEND_HANDLER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = ChannelHandler.class)
public class FrontendHandlerProvider extends ChannelInboundHandlerAdapter implements FrontendHandler, EventExecutorGroupHandler {

	// Set in activate, not volatile for other methods
	private BundleContext bundleContext;
	private final Map<String, Object> properties = new ConcurrentHashMap<>();
	
	// Resolved when we receive a user event from a handler before us
	private final Deferred<InetSocketAddress> deferredSocketAddress = new Deferred<>();
	private final Promise<InetSocketAddress> promisedSocketAddress = deferredSocketAddress.getPromise();

	private final AtomicBoolean closed = new AtomicBoolean(false);

	// Resolved when the netty client service reference is set
	private final Deferred<ChannelFuture> deferredFuture = new Deferred<>();
	private final Promise<ChannelFuture> promisedFuture = deferredFuture.getPromise();

	// Resolved when the handler is added to the channel
	private final Deferred<Channel> deferredChannel = new Deferred<>();
	private final Promise<Channel> promisedChannel = deferredChannel.getPromise();

	private volatile Configuration.FrontendHandler config;

	private List<String> configPids = new CopyOnWriteArrayList<>();


	// Registration for our channel, which we need to clean up
	private volatile ServiceRegistration<Channel> channelRegistration = null;

	@Reference(name = ReferenceName.FrontendHandler.EVENT_EXECUTOR_GROUP)
	EventExecutorGroup eventExecutorGroup;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL, name = ReferenceName.FrontendHandler.NETTY_CLIENT, target = "(appName=changeme)")
	void setNettyClient(NettyClient client, Map<String, Object> properties) {
		
		// When our client's connection occurs sometime in the future...
		client.promise().onResolve(new Runnable() {
			
			@Override
			public void run() {
				
				// Try to add a listener to it...
				try {
					client.promise().getValue().addListener(new ChannelFutureListener() {

						@Override
						public void operationComplete(ChannelFuture future) throws Exception {
							if (future.isSuccess()) {
								// That resolves our deferrer and enables inbound reading
								System.out.println("Resolving outbound future");
								future.channel().config().setAutoRead(true);
								deferredFuture.resolve(future);

								System.out.println("Backend channel is active, reading");
							} else {
								
								// Or fails our deferrer and closes our inbound channel too
								System.out.println("Backend channel was not activated correctly!");
								deferredFuture.fail(new IllegalStateException("Channel was not opened successfully"));
								promisedChannel.onResolve(new Runnable() {
								
									@Override
									public void run() {
										try {
											promisedChannel.getValue().close();
										} catch (InvocationTargetException | InterruptedException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}
								});
							}

						}
					});
				} catch (InvocationTargetException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		});
		System.out.println("Received netty client " + client);
	}

	void unsetNettyClient(NettyClient client) {

	}

	@Reference
	ConfigurationAdmin configAdmin;

	@Reference
	ConfigurationUtil configUtil;

	@Reference
	ExecutorService executor;

	@Activate
	void activate(BundleContext bundleContext, Configuration.FrontendHandler config, Map<String, ?> properties)
			throws Exception {
		this.properties.putAll(properties);

		this.config = config;
		this.bundleContext = bundleContext;

		// Sometime in the future, start the client via the configured backend handler
		promisedSocketAddress.then((address) -> createConfigurations(promisedChannel.getValue(), address.getValue()));
	
	}
	
	@Modified
	void modified(Map<String, ?> properties) {
		System.out.println("Modified frontend handler with properties " + properties);
		this.properties.clear();
		this.properties.putAll(properties);
		this.config = config;
	}

	@Deactivate
	void deactivate(Configuration.FrontendHandler config) {
		System.out.println("Deactivating frontend handler");
		this.config = config;
		promisedFuture.onResolve(new Runnable() {

			@Override
			public void run() {
				try {
					close(promisedFuture.getValue());
				} catch (InvocationTargetException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		});
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		deferredChannel.resolve(ctx.channel());
		System.out.println("Resolved channel context");

		// Register channel
		final String channelId = ctx.channel().id().asLongText();
		Hashtable<String, Object> properties = new Hashtable<>();
		properties.put(Property.Channel.CHANNEL_ID, channelId);
		channelRegistration = bundleContext.registerService(Channel.class, ctx.channel(), properties);
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof InetSocketAddress) {
			deferredSocketAddress.resolve((InetSocketAddress) evt);
			System.out.println("Received socket address to connect to.");
		}
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		// TODO Auto-generated method stub
		super.handlerRemoved(ctx);
	}

	private Promise<Void> createConfigurations(Channel channel, InetSocketAddress socketAddress) {

		Deferred<Void> result = new Deferred<>();

		executor.execute(new Runnable() {

			@Override
			public void run() {
				try {
					// Get our configuration and modify it to reset the netty client target
					final String channelId = channel.id().asLongText();
					final String clientTarget = String.format("(%s=%s)",
							io.blesmol.netty.api.Property.NettyClient.APP_NAME, channelId);
					String selfFilter = String.format("(%s=%s)", Constants.SERVICE_PID,
							properties.get(Constants.SERVICE_PID));
					org.osgi.service.cm.Configuration[] configurations = configAdmin.listConfigurations(selfFilter);

					if (configurations.length == 0) {
						result.fail(new IllegalStateException(String.format("No configurations found for self with filter '%s'", selfFilter)));
						return;
					}

					Arrays.stream(configurations).forEach(c -> {
						final Dictionary<String, Object> props = c.getProperties();
						final Hashtable<String, Object> hashtable = new Hashtable<>();
						final Enumeration<String> keys = props.keys();
						while (keys.hasMoreElements()) {
							final String key = keys.nextElement();
							hashtable.put(key, props.get(key));
						}
						hashtable.put(ReferenceName.FrontendHandler.NETTY_CLIENT_TARGET, clientTarget);
						try {
							c.update(hashtable);
						} catch (IOException e) {
							System.out.println("Error updating configuration");
							deferredChannel.fail(e);
							return;
						}
						System.out.println("Updated configuration");
					});

					// Create channel initializer and client app
					final String hostname = socketAddress.getHostName();
					final int port = socketAddress.getPort();
					final Map<String, Object> extraProperties = new HashMap<>();

					// Set the target for the backend handler via extra properties
					String frontendChannelTarget = String.format("(%s=%s)", Property.Channel.CHANNEL_ID, channelId);
					extraProperties.put(ReferenceName.BackendHandler.FRONTEND_CHANNEL_TARGET, frontendChannelTarget);

					final Optional<Map<String, Object>> optionalExtras = Optional.of(extraProperties);
					final List<String> clientFactoryPids = Arrays.asList(config.clientFactoryPids());
					final List<String> clientHandlerNames = Arrays.asList(config.clientHandlerNames());

					final Optional<String> serverAppName = Optional.of(config.appName());
					
					// Create the boostrap provider
					configPids.add(configUtil.createBootstrapProvider(channelId, hostname, port, serverAppName));
					
					// and channel initializer
					configPids.addAll(configUtil.createChannelInitializer(channelId, hostname, port, clientFactoryPids,
							clientHandlerNames, optionalExtras));

					// Setting the optional serverAppName in the netty client configuration uses this netty server's
					// worker event loop group as the client's group
					configPids.add(configUtil.createNettyClientConfig(channelId, hostname, port, clientFactoryPids,
							clientHandlerNames, optionalExtras, serverAppName));

					result.resolve(null);
					System.out.println("Configuration created successfully");
				} catch (Exception e) {
					System.out.println("Configuration not created successfully!");
					result.fail(e);
				}

			}
		});

		return result.getPromise();

	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) {

		// Deferred<Boolean> outboundActive = new Deferred<>();
		// backendHandler.setDeferred(outboundActive);

		promisedFuture.onResolve(new Runnable() {

			@Override
			public void run() {
				try {
					System.out.println("Writing inbound message to outbound channel");
					promisedFuture.getValue().channel().writeAndFlush(msg).addListener(new ChannelFutureListener() {
						@Override
						public void operationComplete(ChannelFuture future) {
							if (future.isSuccess()) {
								// was able to flush out data, force channel read
								ctx.channel().read();
								System.out.println("Wrote outbound message successfully");
							} else {
								System.out.println("Did not write outbound message successfully!");
								future.cause().printStackTrace();

								future.channel().close();

							}
						}
					});
				} catch (InvocationTargetException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		});

	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		System.out.println("Frontend channel read completely");

	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		promisedFuture.onResolve(new Runnable() {

			@Override
			public void run() {
				try {
					close(promisedFuture.getValue());
				} catch (InvocationTargetException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		});
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		closeOnFlush(ctx.channel());
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		System.out.println("Frontend channel is active");
		super.channelActive(ctx);
	}
	
	/**
	 * Closes the specified channel after all queued write requests are flushed.
	 */
	static void closeOnFlush(Channel ch) {
		if (ch.isActive()) {
			ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
		}
	}

	private void close(ChannelFuture otherFuture) {

		if (closed.getAndSet(true) == false) {

			ServiceRegistration<Channel> channelRegistration = this.channelRegistration;
			if (channelRegistration != null) {
				channelRegistration.unregister();
			}

			closeOnFlush(otherFuture.channel());
		}

	}

	@Override
	public EventExecutorGroup getEventExecutorGroup() {
		return eventExecutorGroup;
	}

}
