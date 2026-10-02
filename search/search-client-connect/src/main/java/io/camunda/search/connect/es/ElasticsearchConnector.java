/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.SearchClientConnectException;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.ProxyConfiguration;
import io.camunda.search.connect.configuration.SecurityConfiguration;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.search.connect.util.SecurityUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig.Builder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElasticsearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchConnector.class);

  private static final int DEFAULT_CONNECT_REQUEST_TIMEOUT_MILLIS = 180_000;
  private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;
  private static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 30_000;

  private final ConnectConfiguration configuration;
  private final ObjectMapper objectMapper;
  private final PluginRepository pluginRepository;

  public ElasticsearchConnector(final ConnectConfiguration configuration) {
    this(
        configuration,
        new JacksonConfiguration(configuration).createObjectMapper(),
        new PluginRepository());
  }

  public ElasticsearchConnector(
      final ConnectConfiguration configuration,
      final ObjectMapper objectMapper,
      final PluginRepository pluginRepository) {
    this.configuration = configuration;
    this.objectMapper = objectMapper;
    this.pluginRepository = pluginRepository;
  }

  public ElasticsearchClient createClient() {
    LOGGER.debug("Creating Elasticsearch Client ...");

    // Load plugins
    pluginRepository.load(configuration.getInterceptorPlugins());

    // create rest client
    final var restClient = createRestClient(configuration);

    // Create the transport with a Jackson mapper
    final var transport =
        new Rest5ClientTransport(restClient, new JacksonJsonpMapper(objectMapper));

    // And create the API client
    return new ElasticsearchClient(transport);
  }

  public ElasticsearchAsyncClient createAsyncClient() {
    LOGGER.debug("Creating async Elasticsearch Client ...");

    // Load plugins
    pluginRepository.load(configuration.getInterceptorPlugins());

    // create rest client
    final var restClient = createRestClient(configuration);

    // Create the transport with a Jackson mapper
    final var transport =
        new Rest5ClientTransport(restClient, new JacksonJsonpMapper(objectMapper));

    // And create the API client
    return new ElasticsearchAsyncClient(transport);
  }

  public ObjectMapper objectMapper() {
    return objectMapper;
  }

  private Rest5Client createRestClient(final ConnectConfiguration configuration) {
    final var httpHosts = getHttpHosts(configuration);
    final var restClientBuilder = Rest5Client.builder(httpHosts);

    if (configuration.getConnectTimeout() != null || configuration.getSocketTimeout() != null) {
      restClientBuilder.setConnectionConfigCallback(
          configCallback -> setConnectionTimeouts(configCallback, configuration));
      restClientBuilder.setRequestConfigCallback(
          configCallback -> setRequestTimeouts(configCallback, configuration));
    }

    final Header[] defaultHeaders =
        new Header[] {
          new BasicHeader("Accept", "application/vnd.elasticsearch+json;compatible-with=9"),
          new BasicHeader("Content-Type", "application/vnd.elasticsearch+json;compatible-with=9")
        };

    return restClientBuilder
        .setDefaultHeaders(defaultHeaders)
        .setConnectionManagerCallback(
            connectionManagerBuilder ->
                configureConnectionManager(connectionManagerBuilder, configuration))
        .setHttpClientConfigCallback(
            httpClientBuilder ->
                configureHttpClient(
                    httpClientBuilder, configuration, pluginRepository.asRequestInterceptor()))
        .build();
  }

  @VisibleForTesting
  void configureConnectionManager(
      final PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder,
      final ConnectConfiguration configuration) {
    final var security = configuration.getSecurity();
    if (security != null && security.isEnabled()) {
      setupTlsStrategy(connectionManagerBuilder, security);
    }

    setupConnectionPool(connectionManagerBuilder, configuration);
  }

  @VisibleForTesting
  void configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final ConnectConfiguration configuration,
      final HttpRequestInterceptor... interceptors) {
    setupAuthentication(httpAsyncClientBuilder, configuration);
    setupKeepAlive(httpAsyncClientBuilder);

    for (final HttpRequestInterceptor interceptor : interceptors) {
      httpAsyncClientBuilder.addRequestInterceptorLast(interceptor);
    }

    final var proxyConfig = configuration.getProxy();
    if (proxyConfig != null && proxyConfig.isEnabled()) {
      setupProxy(httpAsyncClientBuilder, proxyConfig);
      addPreemptiveProxyAuthInterceptor(httpAsyncClientBuilder, proxyConfig);
    }
  }

  private void setupTlsStrategy(
      final PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder,
      final SecurityConfiguration security) {
    try {
      final var tlsStrategyBuilder =
          ClientTlsStrategyBuilder.create()
              .setSslContext(SecurityUtil.getSSLContext(security, "elasticsearch-host"));
      if (!security.isVerifyHostname()) {
        tlsStrategyBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }
      connectionManagerBuilder.setTlsStrategy(tlsStrategyBuilder.buildAsync());
    } catch (final Exception e) {
      LOGGER.error("Error in setting up SSLContext", e);
    }
  }

  private void setupConnectionPool(
      final PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder,
      final ConnectConfiguration elsConfig) {
    if (elsConfig.getMaxConnections() != null) {
      connectionManagerBuilder.setMaxConnTotal(elsConfig.getMaxConnections());
    }
    if (elsConfig.getMaxConnectionsPerRoute() != null) {
      connectionManagerBuilder.setMaxConnPerRoute(elsConfig.getMaxConnectionsPerRoute());
    }
  }

  /**
   * Enables TCP keepalive on the NIO sockets. Without it, a firewall or NAT device between this
   * client and Elasticsearch drops its connection tracking entry for an idle connection without
   * sending a RST or FIN, and the next request to reuse that pooled connection blocks until the
   * socket timeout expires. In HC5 it is active by default, but we keep it explicit in case the
   * default ever changes.
   */
  private void setupKeepAlive(final HttpAsyncClientBuilder httpAsyncClientBuilder) {
    httpAsyncClientBuilder.setIOReactorConfig(
        IOReactorConfig.custom().setSoKeepAlive(true).build());
  }

  @VisibleForTesting
  void setConnectionTimeouts(final Builder builder, final ConnectConfiguration elsConfig) {
    final var socketTimeoutMillis =
        Optional.ofNullable(elsConfig.getSocketTimeout()).orElse(DEFAULT_SOCKET_TIMEOUT_MILLIS);
    builder.setSocketTimeout(Timeout.ofMilliseconds(socketTimeoutMillis));

    final var connectTimeoutMillis =
        Optional.ofNullable(elsConfig.getConnectTimeout()).orElse(DEFAULT_CONNECT_TIMEOUT_MILLIS);
    builder.setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMillis));
  }

  @VisibleForTesting
  void setRequestTimeouts(
      final RequestConfig.Builder builder, final ConnectConfiguration elsConfig) {
    // Rest5ClientBuilder set this to 30s, so we are aligning this with the OS client
    // default of 3 minutes
    builder.setConnectionRequestTimeout(
        Timeout.ofMilliseconds(DEFAULT_CONNECT_REQUEST_TIMEOUT_MILLIS));
  }

  private HttpHost getHttpHost(final ConnectConfiguration elsConfig) {
    try {
      return HttpHost.create(elsConfig.getUrl());
    } catch (final Exception e) {
      throw new SearchClientConnectException("Error in url: " + elsConfig.getUrl(), e);
    }
  }

  private HttpHost[] getHttpHosts(final ConnectConfiguration elsConfig) {
    final var urls = elsConfig.getUrls();
    if (urls != null && !urls.isEmpty()) {
      return urls.stream()
          .map(
              url -> {
                try {
                  return HttpHost.create(url);
                } catch (final Exception e) {
                  throw new SearchClientConnectException("Error in url: " + url, e);
                }
              })
          .toArray(HttpHost[]::new);
    }
    return new HttpHost[] {getHttpHost(elsConfig)};
  }

  private void setupAuthentication(
      final HttpAsyncClientBuilder builder, final ConnectConfiguration configuration) {
    final var username = configuration.getUsername();
    final var password = configuration.getPassword();

    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      LOGGER.warn(
          "Username and/or password for are empty. Basic authentication for elasticsearch is not used.");
      return;
    }

    final var credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new AuthScope(null, -1), new UsernamePasswordCredentials(username, password.toCharArray()));
    builder.setDefaultCredentialsProvider(credentialsProvider);
  }

  private void setupProxy(
      final HttpAsyncClientBuilder httpAsyncClientBuilder, final ProxyConfiguration proxyConfig) {
    httpAsyncClientBuilder.setProxy(
        new HttpHost(
            proxyConfig.isSslEnabled() ? "https" : "http",
            proxyConfig.getHost(),
            proxyConfig.getPort()));
  }

  private void addPreemptiveProxyAuthInterceptor(
      final HttpAsyncClientBuilder httpAsyncClientBuilder, final ProxyConfiguration proxyConfig) {
    final String username = proxyConfig.getUsername();
    final String password = proxyConfig.getPassword();

    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      return;
    }

    final String credentials = username + ":" + password;
    final String encodedCredentials =
        Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    final String proxyAuthHeaderValue = "Basic " + encodedCredentials;

    httpAsyncClientBuilder.addRequestInterceptorFirst(
        (request, entity, context) -> {
          if (!request.containsHeader("Proxy-Authorization")) {
            request.addHeader("Proxy-Authorization", proxyAuthHeaderValue);
          }
        });

    LOGGER.debug("Preemptive proxy authentication enabled for proxy");
  }
}
