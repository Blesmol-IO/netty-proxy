package io.blesmol.netty.proxy.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

import io.blesmol.netty.proxy.api.HandlerUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;

public class FrontendHandlerImplTest {

	/*
	 * Test that backend context is promised correctly when the impl
	 * is called correctly
	 */
	@Test
	public void shouldPromiseBackendCtx() throws Exception {
		
		// Cannot use below because it's reverse resolved to "localhost"
		// Remove InetSocketAddress use; replace w/ InetKey
//		final String expectedInetHost = "127.0.0.1";
		final String expectedInetHost = "localhost";
		final int expectedInetPort = 54321;
		final String expectedChannelId = UUID.randomUUID().toString();
		
		// Frontend channel context related mocks
		ChannelId mockId = mock(ChannelId.class);
		when(mockId.asLongText()).thenReturn(expectedChannelId);
		Channel mockFrontendChannel = mock(Channel.class);
		when(mockFrontendChannel.id()).thenReturn(mockId);
		final ChannelHandlerContext mockFrontendCtx = mock(ChannelHandlerContext.class);
		when(mockFrontendCtx.channel()).thenReturn(mockFrontendChannel);
		// Implicitly test event executor, for better or worse
		final EventExecutor eventExecutor = new DefaultEventExecutor();
		when(mockFrontendCtx.executor()).thenReturn(eventExecutor);
		final ChannelHandlerContext mockBackendCtx = mock(ChannelHandlerContext.class);

		final InetSocketAddress socketAddress = new InetSocketAddress(expectedInetHost, expectedInetPort);

		// Setup
		//
		FrontendHandlerImpl impl = new FrontendHandlerImpl();
		// Implicitly test handler utils, for better or worse
		HandlerUtils handlerUtils = new HandlerUtilsImpl();
		impl.setHandlerUtils(handlerUtils);

		// Calls and Verifies
		//
		// 1st: handlerAdded
		impl.handlerAdded(mockFrontendCtx);
		assertEquals(mockFrontendChannel, impl.channels.get(expectedChannelId));

		// 2nd: userEventTriggered
		impl.userEventTriggered(mockFrontendCtx, socketAddress);
		final InetKey expectedKey = new InetKey(expectedInetHost, expectedInetPort);
		Map<InetKey, io.netty.util.concurrent.Promise<ChannelHandlerContext>> keyedMap = impl.keyedCtxs.get(expectedChannelId);
		assertNotNull(keyedMap);

		io.netty.util.concurrent.Promise<ChannelHandlerContext> backendPromise = keyedMap.get(expectedKey);
		assertNotNull(backendPromise);
		assertTrue(!backendPromise.isDone());

		// 3rd: setBackendCtx
		impl.setBackendCtx(mockBackendCtx, expectedChannelId, expectedKey);
		assertTrue(backendPromise.isDone());
		assertEquals(mockBackendCtx, backendPromise.get());
	}

}
