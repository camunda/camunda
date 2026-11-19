/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.SearchClientConnectException;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.ProxyConfiguration;
import io.camunda.search.connect.configuration.SecurityConfiguration;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.os.json.SearchRequestJacksonJsonpMapperWrapper;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.search.connect.util.SecurityUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

public final class OpensearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchConnector.class);

  private static final int DEFAULT_CONNECT_REQUEST_TIMEOUT_MILLIS = 180_000;
  private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;
  private static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 30_000;

  private final ConnectConfiguration configuration;
  private final ObjectMapper objectMapper;
  private final PluginRepository pluginRepository;

  private final AwsCredentialsProvider credentialsProvider;

  // the options below are null unless the connector was created through the builder
  private final Supplier<HttpHost[]> httpHosts;
  private final JsonpMapper jsonpMapper;
  private final Supplier<Region> awsRegion;
  private final AwsCredentialsProvider awsTransportCredentials;
  private final TlsStrategy tlsStrategy;

  public OpensearchConnector(final ConnectConfiguration configuration) {
    this(
        configuration,
        new JacksonConfiguration(configuration).createObjectMapper(),
        DefaultCredentialsProvider.builder().build(),
        new PluginRepository());
  }

  public OpensearchConnector(
      final ConnectConfiguration configuration,
      final ObjectMapper objectMapper,
      final AwsCredentialsProvider credentialsProvider,
      final PluginRepository pluginRepository) {
    this(
        configuration,
        objectMapper,
        credentialsProvider,
        pluginRepository,
        null,
        new SearchRequestJacksonJsonpMapperWrapper(objectMapper),
        null,
        null,
        null);
  }

  private OpensearchConnector(
      final ConnectConfiguration configuration,
      final ObjectMapper objectMapper,
      final AwsCredentialsProvider credentialsProvider,
      final PluginRepository pluginRepository,
      final Supplier<HttpHost[]> httpHosts,
      final JsonpMapper jsonpMapper,
      final Supplier<Region> awsRegion,
      final AwsCredentialsProvider awsTransportCredentials,
      final TlsStrategy tlsStrategy) {
    this.configuration = configuration;
    this.objectMapper = objectMapper;
    this.credentialsProvider = credentialsProvider;
    this.pluginRepository = pluginRepository;
    this.httpHosts = httpHosts;
    this.jsonpMapper = jsonpMapper;
    this.awsRegion = awsRegion;
    this.awsTransportCredentials = awsTransportCredentials;
    this.tlsStrategy = tlsStrategy;
  }

  public static Builder builder() {
    return new Builder();
  }

  public OpenSearchClient createClient() {
    // Load plugins
    pluginRepository.load(configuration.getInterceptorPlugins());

    final var transport = createTransport(configuration);

    return new OpenSearchClient(transport);
  }

  public OpenSearchAsyncClient createAsyncClient() {
    // Load plugins
    pluginRepository.load(configuration.getInterceptorPlugins());

    final var transport = createTransport(configuration);

    return new OpenSearchAsyncClient(transport);
  }

  public ObjectMapper objectMapper() {
    return objectMapper;
  }

  private OpenSearchTransport createTransport(final ConnectConfiguration configuration) {
    if (shouldCreateAWSBasedTransport()) {
      return createAWSBasedTransport(configuration);
    } else {
      return createDefaultTransport(configuration);
    }
  }

  private OpenSearchTransport createAWSBasedTransport(final ConnectConfiguration configuration) {
    final var hostname = hosts(configuration)[0].getHostName();
    final var region =
        awsRegion != null ? awsRegion.get() : new DefaultAwsRegionProviderChain().getRegion();
    final var httpClient = AwsCrtHttpClient.builder().build();
    final var transportOptions = AwsSdk2TransportOptions.builder().setMapper(jsonpMapper);
    if (awsTransportCredentials != null) {
      transportOptions.setCredentials(awsTransportCredentials);
    }
    return new AwsSdk2Transport(httpClient, hostname, region, transportOptions.build());
  }

  private HttpHost[] hosts(final ConnectConfiguration configuration) {
    return httpHosts != null ? httpHosts.get() : getHttpHosts(configuration);
  }

  private OpenSearchTransport createDefaultTransport(final ConnectConfiguration configuration) {
    final var hosts = hosts(configuration);
    final var builder = ApacheHttpClient5TransportBuilder.builder(hosts);

    builder.setHttpClientConfigCallback(
        httpClientBuilder -> {
          configureHttpClient(
              httpClientBuilder, configuration, pluginRepository.asRequestInterceptor());
          return httpClientBuilder;
        });

    builder.setRequestConfigCallback(
        requestConfigBuilder -> {
          setTimeouts(requestConfigBuilder, configuration);
          return requestConfigBuilder;
        });

    builder.setMapper(jsonpMapper);

    return builder.build();
  }

  private boolean shouldCreateAWSBasedTransport() {
    if (credentialsProvider == null || !configuration.isAwsEnabled()) {
      return false;
    }

    try {
      credentialsProvider.resolveCredentials();
      LOGGER.info("AWS Credentials can be resolved. Use AWS Opensearch");
      return true;
    } catch (final Exception e) {
      LOGGER.warn("AWS not configured due to: {} ", e.getMessage());
      return false;
    }
  }

  private HttpHost getHttpHost(final ConnectConfiguration osConfig) {
    try {
      final var uri = new URI(osConfig.getUrl());
      return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    } catch (final URISyntaxException e) {
      throw new SearchClientConnectException("Error in url: " + osConfig.getUrl(), e);
    }
  }

  private HttpHost[] getHttpHosts(final ConnectConfiguration osConfig) {
    final var urls = osConfig.getUrls();
    if (urls != null && !urls.isEmpty()) {
      return urls.stream()
          .map(
              url -> {
                try {
                  final var uri = new URI(url);
                  return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
                } catch (final URISyntaxException e) {
                  throw new SearchClientConnectException("Error in url: " + url, e);
                }
              })
          .toArray(HttpHost[]::new);
    }
    return new HttpHost[] {getHttpHost(osConfig)};
  }

  @VisibleForTesting
  protected HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final ConnectConfiguration osConfig,
      final HttpRequestInterceptor... interceptors) {
    httpAsyncClientBuilder.disableContentCompression();
    setupAuthentication(httpAsyncClientBuilder, osConfig);

    for (final HttpRequestInterceptor interceptor : interceptors) {
      httpAsyncClientBuilder.addRequestInterceptorLast(interceptor);
    }

    setupConnectionManager(httpAsyncClientBuilder, osConfig);
    setupKeepAlive(httpAsyncClientBuilder);

    final var proxyConfig = osConfig.getProxy();
    if (proxyConfig != null && proxyConfig.isEnabled()) {
      setupProxy(httpAsyncClientBuilder, proxyConfig);
      addPreemptiveProxyAuthInterceptor(httpAsyncClientBuilder, proxyConfig);
    }

    return httpAsyncClientBuilder;
  }

  @VisibleForTesting
  protected RequestConfig.Builder setTimeouts(
      final RequestConfig.Builder builder, final ConnectConfiguration os) {
    // ensure we have default timeouts - as otherwise timeouts are infinite
    final var socketTimeoutMillis =
        Optional.ofNullable(os.getSocketTimeout()).orElse(DEFAULT_SOCKET_TIMEOUT_MILLIS);
    builder.setResponseTimeout(Timeout.ofMilliseconds(socketTimeoutMillis));

    final var connectTimeoutMillis =
        Optional.ofNullable(os.getConnectTimeout()).orElse(DEFAULT_CONNECT_TIMEOUT_MILLIS);
    builder.setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMillis));

    // this already gets set, but making it more explicit, so it's more visible and we can easily
    // tune it later
    builder.setConnectionRequestTimeout(
        Timeout.ofMilliseconds(DEFAULT_CONNECT_REQUEST_TIMEOUT_MILLIS));

    return builder;
  }

  private HttpAsyncClientBuilder setupAuthentication(
      final HttpAsyncClientBuilder builder, final ConnectConfiguration configuration) {
    final var username = configuration.getUsername();
    final var password = configuration.getPassword();

    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      LOGGER.warn(
          "Username and/or password for are empty. Basic authentication for OpenSearch is not used.");
      return builder;
    }

    final var credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        SecurityUtil.ANY_AUTH_SCOPE,
        new UsernamePasswordCredentials(
            configuration.getUsername(), configuration.getPassword().toCharArray()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
    return builder;
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

  /**
   * Builds and assigns a {@link
   * org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager} when TLS or connection
   * pool limits need to be configured. When neither is configured the builder is left untouched so
   * the Apache HttpClient defaults remain in effect.
   */
  private void setupConnectionManager(
      final HttpAsyncClientBuilder httpAsyncClientBuilder, final ConnectConfiguration osConfig) {
    final var security = osConfig.getSecurity();
    final boolean securityEnabled = security != null && security.isEnabled();
    final boolean poolConfigured =
        osConfig.getMaxConnections() != null || osConfig.getMaxConnectionsPerRoute() != null;

    if (tlsStrategy == null && !securityEnabled && !poolConfigured) {
      return;
    }

    final var connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create();

    if (tlsStrategy != null) {
      connectionManagerBuilder.setTlsStrategy(tlsStrategy);
    } else if (securityEnabled) {
      final var tlsStrategy = buildTlsStrategy(security);
      if (tlsStrategy != null) {
        connectionManagerBuilder.setTlsStrategy(tlsStrategy);
      }
    }
    if (osConfig.getMaxConnections() != null) {
      connectionManagerBuilder.setMaxConnTotal(osConfig.getMaxConnections());
    }
    if (osConfig.getMaxConnectionsPerRoute() != null) {
      connectionManagerBuilder.setMaxConnPerRoute(osConfig.getMaxConnectionsPerRoute());
    }

    httpAsyncClientBuilder.setConnectionManager(connectionManagerBuilder.build());
  }

  /**
   * Enables TCP keepalive on the NIO sockets, which the Apache HttpAsyncClient leaves off by
   * default. Without it, a firewall or NAT device between this client and OpenSearch drops its
   * connection tracking entry for an idle connection without sending a RST or FIN, and the next
   * request to reuse that pooled connection blocks until the response timeout expires.
   */
  private void setupKeepAlive(final HttpAsyncClientBuilder httpAsyncClientBuilder) {
    httpAsyncClientBuilder.setIOReactorConfig(
        IOReactorConfig.custom().setSoKeepAlive(true).build());
  }

  private TlsStrategy buildTlsStrategy(final SecurityConfiguration configuration) {
    try {
      final var tlsStrategyBuilder = ClientTlsStrategyBuilder.create();
      final var sslContext = SecurityUtil.getSSLContext(configuration, "opensearch-host");

      tlsStrategyBuilder.setSslContext(sslContext);
      if (!configuration.isVerifyHostname()) {
        tlsStrategyBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }

      return tlsStrategyBuilder.build();
    } catch (final Exception e) {
      LOGGER.error("Error in setting up SSLContext", e);
      return null;
    }
  }

  /**
   * Builds a connector from a {@link ConnectConfiguration} plus the options that must not be bound
   * from user properties. Unset options behave exactly like the constructors of {@link
   * OpensearchConnector}.
   */
  public static final class Builder {
    private ConnectConfiguration configuration;
    private ObjectMapper objectMapper;
    private AwsCredentialsProvider credentialsProvider;
    private PluginRepository pluginRepository = new PluginRepository();
    private Supplier<HttpHost[]> httpHosts;
    private JsonpMapper jsonpMapper;
    private Supplier<Region> awsRegion;
    private AwsCredentialsProvider awsTransportCredentials;
    private TlsStrategy tlsStrategy;

    private Builder() {}

    public Builder configuration(final ConnectConfiguration configuration) {
      this.configuration = configuration;
      return this;
    }

    /** Defaults to the mapper derived from the configuration. */
    public Builder objectMapper(final ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
      return this;
    }

    /** Used to decide whether AWS is available; defaults to the default AWS provider chain. */
    public Builder credentialsProvider(final AwsCredentialsProvider credentialsProvider) {
      this.credentialsProvider = credentialsProvider;
      return this;
    }

    public Builder pluginRepository(final PluginRepository pluginRepository) {
      this.pluginRepository = pluginRepository;
      return this;
    }

    /**
     * The hosts to connect to, instead of the ones derived from the configuration's URLs. They are
     * resolved only when the client is created, after the plugins have been loaded.
     */
    public Builder httpHosts(final Supplier<HttpHost[]> httpHosts) {
      this.httpHosts = httpHosts;
      return this;
    }

    /** Defaults to the mapper that also serializes search-after fields of search requests. */
    public Builder jsonpMapper(final JsonpMapper jsonpMapper) {
      this.jsonpMapper = jsonpMapper;
      return this;
    }

    /**
     * The AWS region, resolved only when the AWS transport is actually created; defaults to the one
     * resolved by the default region provider chain.
     */
    public Builder awsRegion(final Supplier<Region> awsRegion) {
      this.awsRegion = awsRegion;
      return this;
    }

    /** Credentials the AWS transport signs with; defaults to the transport's own. */
    public Builder awsTransportCredentials(final AwsCredentialsProvider awsTransportCredentials) {
      this.awsTransportCredentials = awsTransportCredentials;
      return this;
    }

    /** TLS setup to use, instead of the one derived from the configuration's security options. */
    public Builder tlsStrategy(final TlsStrategy tlsStrategy) {
      this.tlsStrategy = tlsStrategy;
      return this;
    }

    public OpensearchConnector build() {
      Objects.requireNonNull(configuration, "configuration is required");
      final var mapper =
          objectMapper != null
              ? objectMapper
              : new JacksonConfiguration(configuration).createObjectMapper();
      return new OpensearchConnector(
          configuration,
          mapper,
          credentialsProvider != null
              ? credentialsProvider
              : DefaultCredentialsProvider.builder().build(),
          pluginRepository,
          httpHosts,
          jsonpMapper != null ? jsonpMapper : new SearchRequestJacksonJsonpMapperWrapper(mapper),
          awsRegion,
          awsTransportCredentials,
          tlsStrategy);
    }
  }
}
