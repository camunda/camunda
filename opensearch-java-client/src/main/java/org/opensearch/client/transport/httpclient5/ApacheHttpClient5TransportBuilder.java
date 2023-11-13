/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.client.transport.httpclient5;

import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.transport.httpclient5.internal.Node;
import org.opensearch.client.transport.httpclient5.internal.NodeSelector;

public class ApacheHttpClient5TransportBuilder {
    /**
     * The default connection timeout in milliseconds.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 1000;

    /**
     * The default response timeout in milliseconds.
     */
    public static final int DEFAULT_RESPONSE_TIMEOUT_MILLIS = 30000;

    /**
     * The default maximum of connections per route.
     */
    public static final int DEFAULT_MAX_CONN_PER_ROUTE = 10;

    /**
     * The default maximum total connections.
     */
    public static final int DEFAULT_MAX_CONN_TOTAL = 30;

    private static final Header[] EMPTY_HEADERS = new Header[0];

    private final List<Node> nodes;
    private Header[] defaultHeaders = EMPTY_HEADERS;
    private ApacheHttpClient5Transport.FailureListener failureListener;
    private HttpClientConfigCallback httpClientConfigCallback;
    private RequestConfigCallback requestConfigCallback;
    private String pathPrefix;
    private NodeSelector nodeSelector = NodeSelector.ANY;
    private boolean strictDeprecationMode = false;
    private boolean compressionEnabled = false;
    private Optional<Boolean> chunkedEnabled;
    private JsonpMapper mapper;
    private TransportOptions options;

    /**
     * Creates a new builder instance and sets the hosts that the client will send requests to.
     *
     * @throws IllegalArgumentException if {@code nodes} is {@code null} or empty.
     */
    ApacheHttpClient5TransportBuilder(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must not be null or empty");
        }
        for (Node node : nodes) {
            if (node == null) {
                throw new IllegalArgumentException("node cannot be null");
            }
        }
        this.nodes = nodes;
        this.chunkedEnabled = Optional.empty();
    }

    /**
     * Sets the default request headers, which will be sent along with each request.
     * <p>
     * Request-time headers will always overwrite any default headers.
     *
     * @param defaultHeaders array of default header
     * @throws NullPointerException if {@code defaultHeaders} or any header is {@code null}.
     */
    public ApacheHttpClient5TransportBuilder setDefaultHeaders(Header[] defaultHeaders) {
        Objects.requireNonNull(defaultHeaders, "defaultHeaders must not be null");
        for (Header defaultHeader : defaultHeaders) {
            Objects.requireNonNull(defaultHeader, "default header must not be null");
        }
        this.defaultHeaders = defaultHeaders;
        return this;
    }

    /**
     * Sets the {@link RestClient.FailureListener} to be notified for each request failure
     *
     * @param failureListener the {@link RestClient.FailureListener} for each failure
     * @throws NullPointerException if {@code failureListener} is {@code null}.
     */
    public ApacheHttpClient5TransportBuilder setFailureListener(ApacheHttpClient5Transport.FailureListener failureListener) {
        Objects.requireNonNull(failureListener, "failureListener must not be null");
        this.failureListener = failureListener;
        return this;
    }

    /**
     * Sets the {@link HttpClientConfigCallback} to be used to customize http client configuration
     *
     * @param httpClientConfigCallback the {@link HttpClientConfigCallback} to be used
     * @throws NullPointerException if {@code httpClientConfigCallback} is {@code null}.
     */
    public ApacheHttpClient5TransportBuilder setHttpClientConfigCallback(HttpClientConfigCallback httpClientConfigCallback) {
        Objects.requireNonNull(httpClientConfigCallback, "httpClientConfigCallback must not be null");
        this.httpClientConfigCallback = httpClientConfigCallback;
        return this;
    }

    /**
     * Sets the {@link RequestConfigCallback} to be used to customize http client configuration
     *
     * @param requestConfigCallback the {@link RequestConfigCallback} to be used
     * @throws NullPointerException if {@code requestConfigCallback} is {@code null}.
     */
    public ApacheHttpClient5TransportBuilder setRequestConfigCallback(RequestConfigCallback requestConfigCallback) {
        Objects.requireNonNull(requestConfigCallback, "requestConfigCallback must not be null");
        this.requestConfigCallback = requestConfigCallback;
        return this;
    }

    /**
     * Sets the path's prefix for every request used by the http client.
     * <p>
     * For example, if this is set to "/my/path", then any client request will become <code>"/my/path/" + endpoint</code>.
     * <p>
     * In essence, every request's {@code endpoint} is prefixed by this {@code pathPrefix}. The path prefix is useful for when
     * OpenSearch is behind a proxy that provides a base path or a proxy that requires all paths to start with '/';
     * it is not intended for other purposes and it should not be supplied in other scenarios.
     *
     * @param pathPrefix the path prefix for every request.
     * @throws NullPointerException if {@code pathPrefix} is {@code null}.
     * @throws IllegalArgumentException if {@code pathPrefix} is empty, or ends with more than one '/'.
     */
    public ApacheHttpClient5TransportBuilder setPathPrefix(String pathPrefix) {
        this.pathPrefix = cleanPathPrefix(pathPrefix);
        return this;
    }

    /**
     * Sets the {@link JsonpMapper} instance to be used for parsing JSON payloads. If not provided 
     * the {@link JacksonJsonpMapper} is going to be used.
     * @param mapper the {@link JsonpMapper} instance
     */
    public ApacheHttpClient5TransportBuilder setMapper(JsonpMapper mapper) {
        this.mapper = mapper;
        return this;
    }

    /**
     * Sets the default {@link TransportOptions} to be used by the client
     * @param options default {@link TransportOptions}
     */
    public ApacheHttpClient5TransportBuilder setOptions(TransportOptions options) {
        this.options = options;
        return this;
    }

    /**
     * Cleans up the given path prefix to ensure that looks like "/base/path".
     *
     * @param pathPrefix the path prefix to be cleaned up.
     * @return the cleaned up path prefix.
     * @throws NullPointerException if {@code pathPrefix} is {@code null}.
     * @throws IllegalArgumentException if {@code pathPrefix} is empty, or ends with more than one '/'.
     */
    public static String cleanPathPrefix(String pathPrefix) {
        Objects.requireNonNull(pathPrefix, "pathPrefix must not be null");

        if (pathPrefix.isEmpty()) {
            throw new IllegalArgumentException("pathPrefix must not be empty");
        }

        String cleanPathPrefix = pathPrefix;
        if (cleanPathPrefix.startsWith("/") == false) {
            cleanPathPrefix = "/" + cleanPathPrefix;
        }

        // best effort to ensure that it looks like "/base/path" rather than "/base/path/"
        if (cleanPathPrefix.endsWith("/") && cleanPathPrefix.length() > 1) {
            cleanPathPrefix = cleanPathPrefix.substring(0, cleanPathPrefix.length() - 1);

            if (cleanPathPrefix.endsWith("/")) {
                throw new IllegalArgumentException("pathPrefix is malformed. too many trailing slashes: [" + pathPrefix + "]");
            }
        }
        return cleanPathPrefix;
    }

    /**
     * Sets the {@link NodeSelector} to be used for all requests.
     *
     * @param nodeSelector the {@link NodeSelector} to be used
     * @throws NullPointerException if the provided nodeSelector is null
     */
    public ApacheHttpClient5TransportBuilder setNodeSelector(NodeSelector nodeSelector) {
        Objects.requireNonNull(nodeSelector, "nodeSelector must not be null");
        this.nodeSelector = nodeSelector;
        return this;
    }

    /**
     * Whether the REST client should return any response containing at least
     * one warning header as a failure.
     *
     * @param strictDeprecationMode flag for enabling strict deprecation mode
     */
    public ApacheHttpClient5TransportBuilder setStrictDeprecationMode(boolean strictDeprecationMode) {
        this.strictDeprecationMode = strictDeprecationMode;
        return this;
    }

    /**
     * Whether the REST client should compress requests using gzip content encoding and add the "Accept-Encoding: gzip"
     * header to receive compressed responses.
     *
     * @param compressionEnabled flag for enabling compression
     */
    public ApacheHttpClient5TransportBuilder setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
        return this;
    }

    /**
     * Whether the REST client should use Transfer-Encoding: chunked for requests or not"
     *
     * @param chunkedEnabled force enable/disable chunked transfer-encoding.
     */
    public ApacheHttpClient5TransportBuilder setChunkedEnabled(boolean chunkedEnabled) {
        this.chunkedEnabled = Optional.of(chunkedEnabled);
        return this;
    }

    /**
     * Creates a new {@link RestClient} based on the provided configuration.
     */
    public ApacheHttpClient5Transport build() {
        if (failureListener == null) {
            failureListener = new ApacheHttpClient5Transport.FailureListener();
        }
        CloseableHttpAsyncClient httpClient = AccessController.doPrivileged(
            (PrivilegedAction<CloseableHttpAsyncClient>) this::createHttpClient
        );

        if (mapper == null) {
            mapper = new JacksonJsonpMapper();
        }

        final ApacheHttpClient5Transport transport = new ApacheHttpClient5Transport(
                httpClient,
                defaultHeaders,
                nodes,
                mapper,
                options,
                pathPrefix,
                failureListener,
                nodeSelector,
                strictDeprecationMode,
                compressionEnabled,
                chunkedEnabled.orElse(false)
            );

        httpClient.start();
        return transport;
    }

    /**
     * Returns a new {@link ApacheHttpClient5TransportBuilder} to help with {@link ApacheHttpClient5Transport} creation.
     * Creates a new builder instance and sets the hosts that the client will send requests to.
     * <p>
     * Prefer this to {@link #builder(HttpHost...)} if you have metadata up front about the nodes.
     * If you don't either one is fine.
     *
     * @param nodes The nodes that the client will send requests to.
     */
    public static ApacheHttpClient5TransportBuilder builder(Node... nodes) {
        return new ApacheHttpClient5TransportBuilder(nodes == null ? null : Arrays.asList(nodes));
    }

    /**
     * Returns a new {@link ApacheHttpClient5TransportBuilder} to help with {@link ApacheHttpClient5Transport} creation.
     * Creates a new builder instance and sets the nodes that the client will send requests to.
     * <p>
     * You can use this if you do not have metadata up front about the nodes. If you do, prefer
     * {@link #builder(Node...)}.
     * @see Node#Node(HttpHost)
     *
     * @param hosts The hosts that the client will send requests to.
     */
    public static ApacheHttpClient5TransportBuilder builder(HttpHost... hosts) {
        if (hosts == null || hosts.length == 0) {
            throw new IllegalArgumentException("hosts must not be null nor empty");
        }
        List<Node> nodes = Arrays.stream(hosts).map(Node::new).collect(Collectors.toList());
        return new ApacheHttpClient5TransportBuilder(nodes);
    }

    private CloseableHttpAsyncClient createHttpClient() {
        // default timeouts are all infinite
        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(DEFAULT_CONNECT_TIMEOUT_MILLIS))
            .setResponseTimeout(Timeout.ofMilliseconds(DEFAULT_RESPONSE_TIMEOUT_MILLIS));

        if (requestConfigCallback != null) {
            requestConfigBuilder = requestConfigCallback.customizeRequestConfig(requestConfigBuilder);
        }

        try {
            final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
                .setSslContext(SSLContext.getDefault())
                // See https://issues.apache.org/jira/browse/HTTPCLIENT-2219
                .setTlsDetailsFactory(new Factory<SSLEngine, TlsDetails>() {
                    @Override
                    public TlsDetails create(final SSLEngine sslEngine) {
                        return new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol());
                    }
                })
                .build();

            final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnPerRoute(DEFAULT_MAX_CONN_PER_ROUTE)
                .setMaxConnTotal(DEFAULT_MAX_CONN_TOTAL)
                .setTlsStrategy(tlsStrategy)
                .build();

            HttpAsyncClientBuilder httpClientBuilder = HttpAsyncClientBuilder.create()
                .setDefaultRequestConfig(requestConfigBuilder.build())
                .setConnectionManager(connectionManager)
                .setTargetAuthenticationStrategy(DefaultAuthenticationStrategy.INSTANCE)
                .disableAutomaticRetries();
            if (httpClientConfigCallback != null) {
                httpClientBuilder = httpClientConfigCallback.customizeHttpClient(httpClientBuilder);
            }

            final HttpAsyncClientBuilder finalBuilder = httpClientBuilder;
            return AccessController.doPrivileged((PrivilegedAction<CloseableHttpAsyncClient>) finalBuilder::build);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("could not create the default ssl context", e);
        }
    }

    /**
     * Callback used the default {@link RequestConfig} being set to the {@link CloseableHttpClient}
     * @see HttpClientBuilder#setDefaultRequestConfig
     */
    public interface RequestConfigCallback {
        /**
         * Allows to customize the {@link RequestConfig} that will be used with each request.
         * It is common to customize the different timeout values through this method without losing any other useful default
         * value that the {@link RestClientBuilder} internally sets.
         *
         * @param requestConfigBuilder the {@link RestClientBuilder} for customizing the request configuration.
         */
        RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder);
    }

    /**
     * Callback used to customize the {@link CloseableHttpClient} instance used by a {@link RestClient} instance.
     * Allows to customize default {@link RequestConfig} being set to the client and any parameter that
     * can be set through {@link HttpClientBuilder}
     */
    public interface HttpClientConfigCallback {
        /**
         * Allows to customize the {@link CloseableHttpAsyncClient} being created and used by the {@link RestClient}.
         * Commonly used to customize the default {@link CredentialsProvider} for authentication for communication
         * through TLS/SSL without losing any other useful default value that the {@link RestClientBuilder} internally
         * sets, like connection pooling.
         *
         * @param httpClientBuilder the {@link HttpClientBuilder} for customizing the client instance.
         */
        HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder);
    }


}
