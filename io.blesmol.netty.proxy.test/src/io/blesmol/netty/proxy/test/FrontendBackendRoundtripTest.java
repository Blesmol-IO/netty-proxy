package io.blesmol.netty.proxy.test;

import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedServiceFactory;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.proxy.api.ProxyProviderApi;
import io.blesmol.netty.test.RoundtripClientServerTest.LatchTestClientHandler;
import io.blesmol.netty.test.RoundtripClientServerTest.LatchTestServerHandler;
import io.blesmol.netty.test.TestUtils;
import io.blesmol.netty.test.TestUtils.LatchTestChannelHandlerFactory;
import io.blesmol.netty.test.TestUtils.TestChannelHandlerFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/*
 * Perform a round trip test using a proxy in-between 
 */
@RunWith(MockitoJUnitRunner.class)
public class FrontendBackendRoundtripTest {

	final BundleContext context = FrameworkUtil.getBundle(FrontendBackendRoundtripTest.class).getBundleContext();

	private ConfigurationUtil configUtil;

	List<String> configPids;

	static final String serverHostname = "localhost";
	static final String proxyHostname = serverHostname;
	static final int serverPort = 54334;
	static final int proxiedPort = 54333;
	static final String serverFactoryPid = LatchTestServerHandler.class.getName();
	static final String clientFactoryPid = LatchTestClientHandler.class.getName();
	static final String proxyShimFactoryPid = TestProxyShimHandler.class.getName();

	@Before
	public void before() throws Exception {
		configUtil = TestUtils.getService(context, ConfigurationUtil.class, 700);

	}

	@After
	public void after() throws Exception {
		configUtil.deleteConfigurationPids(configPids);
	}

	@Test
	public void shouldRoundtripMessage() throws Exception {

		// Configure backend server and register a managed service factory for the
		// backend server handler
		configPids = configUtil.createNettyServer(FrontendBackendRoundtripTest.class.getName() + ":server",
				serverHostname, serverPort, Arrays.asList(serverFactoryPid), Arrays.asList("testServerHandler"), Optional.empty());
		Hashtable<String, Object> serverHandlerProps = new Hashtable<>();
		CountDownLatch serverLatch = new CountDownLatch(1);
		LatchTestChannelHandlerFactory serverHandlerFactory = new LatchTestChannelHandlerFactory(context,
				LatchTestServerHandler.class, serverLatch);
		serverHandlerProps.put(Constants.SERVICE_PID, serverFactoryPid);
		ServiceRegistration<ManagedServiceFactory> serverRegistration = context
				.registerService(ManagedServiceFactory.class, serverHandlerFactory, serverHandlerProps);

		// Configure proxy server and register managed service factory for its proxy
		// shim		
		Hashtable<String, Object> shimHandlerProps = new Hashtable<>();
		TestChannelHandlerFactory shimHandlerFactory = new TestChannelHandlerFactory(context,
				TestProxyShimHandler.class);
		shimHandlerProps.put(Constants.SERVICE_PID, proxyShimFactoryPid);
		ServiceRegistration<ManagedServiceFactory> shimRegistration = context
				.registerService(ManagedServiceFactory.class, shimHandlerFactory, shimHandlerProps);

		
		// Add the frontend event executor group
		Map<String, Object> extraProperties = new HashMap<>();
		final String proxyAppName = FrontendBackendRoundtripTest.class.getName() + ":proxy";

		// Target the FE & BE event executor groups, which is used for async req/res processing
		// And create an event executor group for them
//		String frontendEventExecutorGroupTarget = String.format("(&(%s=%s)(%s=%s))", Property.EventExecutorGroup.APP_NAME, proxyAppName, Property.EventExecutorGroup.GROUP_NAME, ReferenceName.FrontendHandler.EVENT_EXECUTOR_GROUP);
//		extraProperties.put(ReferenceName.FrontendHandler.EVENT_EXECUTOR_GROUP_TARGET, frontendEventExecutorGroupTarget);
//		configPids.add(configUtil.createEventExecutorGroup(proxyAppName, ReferenceName.FrontendHandler.EVENT_EXECUTOR_GROUP));

		extraProperties.put(ProxyProviderApi.FrontendHandler.CLIENT_FACTORY_PIDS,
				new String[] { ProxyProviderApi.BackendHandler.PID});
		extraProperties.put(ProxyProviderApi.FrontendHandler.CLIENT_HANDLER_NAMES,
				new String[] { ProxyProviderApi.BackendHandler.NAME});
		List<String> proxyFactoryPids = Arrays.asList(proxyShimFactoryPid, ProxyProviderApi.FrontendHandler.PID);
		List<String> proxyFactoryHandlerNames = Arrays.asList("proxyShimHandler", ProxyProviderApi.FrontendHandler.NAME);
		configPids.addAll(configUtil.createNettyServer(proxyAppName,
				"localhost", proxiedPort, proxyFactoryPids, proxyFactoryHandlerNames, Optional.of(extraProperties)));

		// Then register managed service factory for client handler, which will be
		// called
		Hashtable<String, Object> clientHandlerProps = new Hashtable<>();
		CountDownLatch clientLatch = new CountDownLatch(1);
		LatchTestChannelHandlerFactory clientHandlerFactory = new LatchTestChannelHandlerFactory(context,
				LatchTestClientHandler.class, clientLatch);
		clientHandlerProps.put(Constants.SERVICE_PID, clientFactoryPid);
		ServiceRegistration<ManagedServiceFactory> clientRegistration = context
				.registerService(ManagedServiceFactory.class, clientHandlerFactory, clientHandlerProps);

		
		// And configure client, not shutting down its event loops
		configPids.addAll(configUtil.createNettyClient(FrontendBackendRoundtripTest.class.getName() + ":client",
				proxyHostname, proxiedPort, Arrays.asList(clientFactoryPid), Arrays.asList("testClientHandler"),
				Optional.empty(), Optional.empty(), Optional.of(false)));

		// TODO: 2 osgi handlers available at time of break point. should be more!

		assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
		assertTrue(clientLatch.await(5, TimeUnit.SECONDS));

		// Cleanup
		serverRegistration.unregister();
		shimRegistration.unregister();
		clientRegistration.unregister();

	}

	public static class TestProxyShimHandler extends ChannelInboundHandlerAdapter {

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			final SocketAddress socketAddress = new InetSocketAddress(serverHostname, serverPort);
			System.out.println("Sent user event with address " + socketAddress);
			ctx.fireUserEventTriggered(socketAddress);
			ctx.fireChannelRead(msg);
		}
	}
}
