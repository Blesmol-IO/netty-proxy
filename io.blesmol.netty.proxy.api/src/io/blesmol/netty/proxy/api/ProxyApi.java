package io.blesmol.netty.proxy.api;

public interface ProxyApi {

	// TODO: Make NettyApi and use values from there
	@interface BackendHandler {
		String PID = "io.blesmol.netty.proxy.api.BackendHandler";
		String NAME = "blesmolBackendHandler";

		String handlerName() default NAME;

		String appName();

		String channelId();

		String FRONTEND_CHANNEL_ID = "frontendChannelId";
		String frontendChannelId();
	}

	@interface FrontendHandler {
		String PID = "io.blesmol.netty.proxy.api.FrontendHandler";
		String NAME = "blesmolFrontendHandler";		
		

		String handlerName() default NAME;

		String appName();

		String channelId();

		String CLIENT_FACTORY_PIDS = "clientFactoryPids";
		String[] clientFactoryPids();

		String CLIENT_HANDLER_NAMES = "clientHandlerNames";
		String[] clientHandlerNames();

	}


	@interface HttpDirectProxyHandler {
		String PID = "io.blesmol.netty.proxy.api.HttpDirectProxyHandler";
		String NAME = "blesmolHttpDirectProxyHandler";
	}

	// TODO: move to netty osgi
	
	@interface Channel {
		String CHANNEL_ID = "channelId";
	}

	@interface ChannelHandlerContext {
		String CHANNEL_ID = "channelId";
	}

	// TODO: move below to codec API
	@interface HttpObjectAggegator {
		String PID = "io.netty.handler.codec.http.HttpObjectAggregator";
		String NAME = "nettyHttpObjectAggegator";

		String MAX_CONTENT_LENGTH = "maxContentLength";

		int maxContentLength();

		String CLOSE_ON_EXPECATION_FAILED = "closeOnExpectationFailed";

		boolean closeOnExpectationFailed() default false;
	}

	@interface HttpServerCodec {

		String PID = "io.netty.handler.codec.http.HttpServerCodec";
		String NAME = "nettyHttpServerCodec";

		// io.netty.handler.codec.http.HttpServerCodec.HttpServerCodec()
		String MAX_INITIAL_LINE_LENGTH = "maxInitialLineLength";
		int maxInitialLineLength() default 4096;

		// io.netty.handler.codec.http.HttpServerCodec.HttpServerCodec()
		String MAX_HEADER_SIZE = "maxHeaderSize";
		int maxHeaderSize() default 8192;

		// io.netty.handler.codec.http.HttpServerCodec.HttpServerCodec()
		String MAX_CHUNK_SIZE = "maxChunkSize";
		int maxChunkSize() default 8192;

		// io.netty.handler.codec.http.HttpRequestDecoder.HttpRequestDecoder(int, int,
		// int)
		String VALIDATE_HEADERS = "validateHeaders";
		boolean validateHeaders() default true;

		// io.netty.handler.codec.http.HttpObjectDecoder.HttpObjectDecoder(int, int,
		// int, boolean, boolean)
		String INITIAL_BUFFER_SIZE = "initialBufferSize";	
		int initialBufferSize() default 128;
	}


	@interface HttpRequestEncoder {
		String PID = "io.netty.handler.codec.http.HttpRequestEncoder";
		String NAME = "nettyHttpRequestEncoder";
	}

	// As seen from HttpClientCodec 4.1
	@interface HttpClientCodec {		
		String PID = "io.netty.handler.codec.http.HttpClientCodec";
		String NAME = "nettyHttpClientCodec";

		String MAX_INITIAL_LINE_LENGTH = "maxInitialLineLength";
		int maxInitialLineLength() default 4096;

		String MAX_HEADER_SIZE = "maxHeaderSize";
		int maxHeaderSize() default 8192;

		String MAX_CHUNK_SIZE = "maxChunkSize";
		int maxChunkSize() default 8192;

		String FAIL_ON_MISSING_RESPONSE = "failOnMissingResponse";
		boolean failOnMissingResponse() default false;

		String VALIDATE_HEADERS = "validateHeaders";
		boolean validateHeaders() default true;

		String PARSE_HTTP_AFTER_CONNECT_REQUEST = "parseHttpAfterConnectRequest";
		boolean parseHttpAfterConnectRequest() default false;

	}
}
