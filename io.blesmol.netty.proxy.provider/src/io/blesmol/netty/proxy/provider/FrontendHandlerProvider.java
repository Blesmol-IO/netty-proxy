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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

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
import io.blesmol.netty.api.NettyApi;
import io.blesmol.netty.api.NettyClient;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.proxy.api.FrontendHandler;
import io.blesmol.netty.proxy.api.ProxyApi;
import io.blesmol.netty.proxy.api.ReferenceName;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;

// Copyright 2012 The Netty Project and Coastal Hacking
@Component(configurationPid = ProxyApi.FrontendHandler.PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = ChannelHandler.class)
public class FrontendHandlerProvider extends ChannelInboundHandlerAdapter implements FrontendHandler {

	// Set indirectly via activate, not volatile for other methods
	private BundleContext bundleContext;
	private String servicePid;
	private String channelId;

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

	// Resolved when we receive the backend's context
	private final Deferred<ChannelHandlerContext> deferredBackendContext = new Deferred<>();
	private final Promise<ChannelHandlerContext> promisedBackendContext = deferredBackendContext.getPromise();

	// TODO: move to processProperties
	@Deprecated
	private volatile ProxyApi.FrontendHandler config;

	private List<String> configPids = new CopyOnWriteArrayList<>();

	// Registrations, which we need to clean up
	private volatile ServiceRegistration<Channel> channelRegistration = null;
	private volatile ServiceRegistration<EventLoopGroup> loopRegistration = null;

	@Reference
	ConfigurationAdmin configAdmin;

	@Reference
	ConfigurationUtil configUtil;

	// Use channel context executor for I/O related tasks
	@Reference
	ExecutorService executor;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL, name = ReferenceName.FrontendHandler.NETTY_CLIENT, target = "(appName=changeme)")
	void setNettyClient(NettyClient client, Map<String, Object> properties) {

		// TODO: move this code on resolving the backend context. maybe don't need
		// reference to client
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

								System.out.println(String.format("Setting auto read to true now for channel %s in %s", future.channel(), FrontendHandlerProvider.this));
								future.channel().config().setAutoRead(true);
								executor.execute(() -> {
									deferredFuture.resolve(future);
									System.out.println(String.format("Netty client %s resolved for %s", client,
											FrontendHandlerProvider.this));
								});
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
		close();
	}

	// Requires changing of target at runtime
	@Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, name = ReferenceName.FrontendHandler.BACKEND_CONTEXT, target = "(channelId=unreachable)")
	void setBackendChannelHandlerContext(ChannelHandlerContext backendContext) {
		deferredBackendContext.resolve(backendContext);
		System.out.println(String.format("%s resolved backend context on channel %s", this, backendContext.channel()));
	}

	void unsetBackendChannelHandlerContext(ChannelHandlerContext backendContext) {
		close();
	}

	@Activate
	void activate(BundleContext bundleContext, ProxyApi.FrontendHandler config, Map<String, ?> properties)
			throws Exception {

		// Must be called before createConfigurations
		processProperties(config, properties);

		// TODO: save only what is needed, in the processProperties call
		this.config = config;
		this.bundleContext = bundleContext;

		executor.execute(() -> {

			// When our channel is available
			promisedChannel
					// Update this component's configuration based on our channel ID
					.then(channel -> updateSelfConfiguration(channel.getValue().id().asLongText()));

			// When we receive an address to connect to
			promisedSocketAddress

					// Then create our client configurations based upon our channel
					.then(socketAddress -> registerEventLoopAndcreateConfigurations(promisedChannel,
							socketAddress.getValue()));

		});

		// .then((c) -> updateSelfConfiguration(c.getValue().id().asLongText()))
		// // Then start the client via
		// .then((channelId) -> createConfigurations(channelId,
		// promisedSocketAddress.getValue()));

		// promisedSocketAddress.then((address) ->
		// createConfigurations(promisedChannel.getValue(), address.getValue()));

	}

	@Modified
	void modified(Map<String, ?> properties) {
		System.out.println(String.format("Modifying %s with properties %s", this, properties));
		// this.config = config;
	}

	@Deactivate
	void deactivate(ProxyApi.FrontendHandler config) {
		System.out.println("Deactivating frontend handler " + this);
		this.config = config;

		close();
	}

	/*
	 * Only call this in activate. If there's a desire to call in modified, then
	 * adjust all fields to be volatile and consider atomic field updaters
	 */
	private void processProperties(ProxyApi.FrontendHandler config, Map<String, ?> properties) {
		servicePid = (String) properties.get(Constants.SERVICE_PID);
		channelId = config.channelId();
	}

	// Resolves if we successfully updated our configuration
	private Promise<Void> updateSelfConfiguration(String channelId) {
		Deferred<Void> result = new Deferred<>();

		// Ensure this does not run on activate thread
		executor.execute(new Runnable() {

			@Override
			public void run() {

				try {
					String servicePidFilter = String.format("(%s=%s)", Constants.SERVICE_PID, servicePid);
					final org.osgi.service.cm.Configuration[] configurations = configAdmin
							.listConfigurations(servicePidFilter);

					if (configurations.length == 0 || configurations.length > 1) {
						String prefix = configurations.length == 0 ? "No" : "Too many";
						result.fail(new IllegalStateException(String.format(
								"%s configurations found for self with filter '%s'", prefix, servicePidFilter)));
						return;
					}

					final org.osgi.service.cm.Configuration configuration = configurations[0];
					final Dictionary<String, Object> props = configuration.getProperties();
					final Hashtable<String, Object> hashtable = new Hashtable<>();
					final Enumeration<String> keys = props.keys();
					while (keys.hasMoreElements()) {
						final String key = keys.nextElement();
						hashtable.put(key, props.get(key));
					}

					// Target netty client using our channel id
					final String clientTarget = String.format("(%s=%s)", NettyApi.NettyClient.APP_NAME, channelId);
					hashtable.put(ReferenceName.FrontendHandler.NETTY_CLIENT_TARGET, clientTarget);

					// Target channel handler context registered by backend. The backend will get
					// our ID after we register our channel as a service
					final String backendContextTarget = String.format("(%s=%s)",
							ProxyApi.ChannelHandlerContext.CHANNEL_ID, channelId);
					hashtable.put(ReferenceName.FrontendHandler.BACKEND_CONTEXT_TARGET, backendContextTarget);

					try {
						configuration.update(hashtable);
					} catch (IOException e) {
						System.out.println("Error updating configuration in " + FrontendHandlerProvider.this);
						deferredChannel.fail(e);
						return;
					}
					result.resolve(null);
					System.out.println(String.format("updateSelfConfiguration %s with properties %s", FrontendHandlerProvider.this, hashtable));

				} catch (Exception e) {
					System.out.println("Configuration not created successfully in " + FrontendHandlerProvider.this);
					result.fail(e);
				}
			}
		});

		return result.getPromise();
	}

	private Promise<Void> registerEventLoopAndcreateConfigurations(Promise<Channel> promisedChannel,
			InetSocketAddress socketAddress) {

		Deferred<Void> result = new Deferred<>();

		executor.execute(() -> {

			promisedChannel.onResolve(() -> {
				try {
					final Channel myChannel = promisedChannel.getValue();
					final String myChannelId = myChannel.id().asLongText();
					final String inetHost = socketAddress.getHostName();
					final int inetPort = socketAddress.getPort();

					// Register our event loop for our client
					Hashtable<String, Object> eventLoopProperties = new Hashtable<>();
					eventLoopProperties.put(NettyApi.EventLoopGroup.APP_NAME, myChannelId);
					eventLoopProperties.put(NettyApi.EventLoopGroup.INET_HOST, inetHost);
					eventLoopProperties.put(NettyApi.EventLoopGroup.INET_PORT, inetPort);
					eventLoopProperties.put(NettyApi.EventLoopGroup.GROUP_NAME,
							io.blesmol.netty.api.ReferenceName.NettyClient.EVENT_LOOP_GROUP);
					loopRegistration = bundleContext.registerService(EventLoopGroup.class, myChannel.eventLoop(),
							eventLoopProperties);
					System.out.println(String.format("Registered event loop %s for %s:%s:%d", myChannel.eventLoop(), myChannelId, inetHost, inetPort));

					// Create channel initializer and client app
					final Map<String, Object> extraProperties = new HashMap<>();

					// Gratuitously set the backend's frontend channel target to our channel ID
					// The backend could do this too on its activate, since we also supply our
					// channel ID below
					String frontendChannelTarget = String.format("(%s=%s)", ProxyApi.Channel.CHANNEL_ID, myChannelId);
					extraProperties.put(ReferenceName.BackendHandler.FRONTEND_CHANNEL_TARGET, frontendChannelTarget);

					// Supply our channel ID for the backend. It'll use this to register its context
					extraProperties.put(ProxyApi.BackendHandler.FRONTEND_CHANNEL_ID, myChannelId);

					final Optional<Map<String, Object>> optionalExtras = Optional.of(extraProperties);
					final List<String> clientFactoryPids = Arrays.asList(config.clientFactoryPids());
					final List<String> clientHandlerNames = Arrays.asList(config.clientHandlerNames());

					final Optional<String> serverAppName = Optional.of(config.appName());

					// Simulate createNettyClient

					// Create the boostrap provider
					configPids.add(configUtil.createBootstrapProvider(myChannelId, inetHost, inetPort, serverAppName));

					// configPids.add(configUtil.createEventLoopGroup(channelId,
					// ReferenceName.NettyClient.EVENT_LOOP_GROUP));

					// and channel initializer
					configPids.addAll(configUtil.createChannelInitializer(myChannelId, inetHost, inetPort,
							clientFactoryPids, clientHandlerNames, optionalExtras));

					// Do not shutdown the event loop group, since it's from the server bootstrap's
					// worker group
					configPids.add(configUtil.createNettyClientConfig(myChannelId, inetHost, inetPort, clientFactoryPids,
							clientHandlerNames, optionalExtras, serverAppName, /* shutdownGroup */ Optional.of(false)));

					result.resolve(null);
					System.out.println("Configuration created successfully");
				} catch (Exception e) {
					System.out.println("Configuration not created successfully!");
					result.fail(e);
				}

			});
		});

		return result.getPromise();

	}

	//
	// SERVICE METHODS
	//

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		deferredChannel.resolve(ctx.channel());
		System.out.println("Resolved channel context in frontend " + this);

		// Register our channel and its event loop on a non-IO thread
		executor.execute(new Runnable() {

			@Override
			public void run() {
				final String channelId = ctx.channel().id().asLongText();
				Hashtable<String, Object> channelProperties = new Hashtable<>();
				channelProperties.put(ProxyApi.Channel.CHANNEL_ID, channelId);
				channelRegistration = bundleContext.registerService(Channel.class, ctx.channel(), channelProperties);
				System.out.println("Registered channel ID " + channelId);
			}
		});
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof InetSocketAddress) {
			final InetSocketAddress address = (InetSocketAddress) evt;
			executor.execute(() -> {
				deferredSocketAddress.resolve(address);
				System.out.println(
						String.format("Resolved socket address %s to connect to in frontend %s", address, this));
			});
		}
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		close();
	}

	@Override
	public void channelRead(final ChannelHandlerContext frontendContext, Object msg) {

		// Deferred<Boolean> outboundActive = new Deferred<>();
		// backendHandler.setDeferred(outboundActive);

		// promisedFuture.onResolve(new Runnable() {
		promisedBackendContext.onResolve(new Runnable() {

			@Override
			public void run() {
				try {
					ChannelHandlerContext backendContext = promisedBackendContext.getValue();
					System.out
							.println(String.format("%s writing the following inbound message to backend channel %s: %s",
									FrontendHandlerProvider.this, backendContext.channel(), msg));
					// Note: writing to the backend context uses the first outbound channel handler
					// *before* it
					// So, make sure to register encoders before the backend handler
					backendContext.writeAndFlush(msg).addListener(new ChannelFutureListener() {
						@Override
						public void operationComplete(ChannelFuture future) {
							if (future.isSuccess()) {
								// was able to flush out data, force channel read
								frontendContext.channel().read();
								System.out.println(String.format("%s wrote inbound message to backend channel %s",
										FrontendHandlerProvider.this, backendContext.channel()));
							} else {
								System.out.println(
										String.format("%s did not write an inbound message to backend channel %s",
												FrontendHandlerProvider.this, backendContext.channel()));
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
		System.out.println("Frontend channel read completely on channel " + channelId);
		super.channelReadComplete(ctx);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		promisedFuture.onResolve(() -> {
			try {
				closeOnFlush(promisedFuture.getValue().channel());
			} catch (InvocationTargetException | InterruptedException e) {
				System.out.println("Could not close my backend channel on " + this);
				e.printStackTrace();
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

	private void close() {

		if (closed.getAndSet(true) == false) {

			System.out.println("Closing " + this);
			try {
				configUtil.deleteConfigurationPids(configPids);
			} catch (Exception e1) {
				e1.printStackTrace();
			}

			promisedFuture.onResolve(new Runnable() {
				@Override
				public void run() {
					try {
						ChannelFuture channelFuture = promisedFuture.getValue();
						closeOnFlush(channelFuture.channel());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});

			Stream.<ServiceRegistration<?>>of(this.channelRegistration, this.loopRegistration).forEach(sr -> {
				if (sr != null) {
					System.out.println("Removing service registrations on " + this);
					sr.unregister();
				}
			});

		}

	}

	@Override
	public String toString() {
		return servicePid + ":" + channelId;
	}

}
