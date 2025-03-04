/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os;

import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.se.config.ConnectConfiguration;
import io.camunda.db.se.config.SecurityConfiguration;
import io.camunda.search.connect.SearchClientConnectException;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.os.json.SearchRequestJacksonJsonpMapperWrapper;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.search.connect.util.SecurityUtil;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.util.Timeout;
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

public final class OpensearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchConnector.class);

  private final ConnectConfiguration configuration;
  private final ObjectMapper objectMapper;
  private final PluginRepository pluginRepository;

  private final AwsCredentialsProvider credentialsProvider;

  public OpensearchConnector(final ConnectConfiguration configuration) {
    this(
        configuration,
        new JacksonConfiguration(configuration).createObjectMapper(),
        DefaultCredentialsProvider.create(),
        new PluginRepository());
  }

  public OpensearchConnector(
      final ConnectConfiguration configuration,
      final ObjectMapper objectMapper,
      final AwsCredentialsProvider credentialsProvider,
      final PluginRepository pluginRepository) {
    this.configuration = configuration;
    this.objectMapper = objectMapper;
    this.credentialsProvider = credentialsProvider;
    this.pluginRepository = pluginRepository;
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
    final var httpHost = getHttpHost(configuration);
    final var region = new DefaultAwsRegionProviderChain().getRegion();
    final var httpClient = AwsCrtHttpClient.builder().build();
    return new AwsSdk2Transport(
        httpClient,
        httpHost.getHostName(),
        Region.of(region),
        AwsSdk2TransportOptions.builder()
            .setMapper(new SearchRequestJacksonJsonpMapperWrapper(objectMapper))
            .build());
  }

  private OpenSearchTransport createDefaultTransport(final ConnectConfiguration configuration) {
    final var host = getHttpHost(configuration);
    final var builder = ApacheHttpClient5TransportBuilder.builder(host);

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

    final var jsonpMapper = new SearchRequestJacksonJsonpMapperWrapper(objectMapper);
    builder.setMapper(jsonpMapper);

    return builder.build();
  }

  private boolean shouldCreateAWSBasedTransport() {
    if (credentialsProvider == null) {
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

  protected HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final ConnectConfiguration osConfig,
      final HttpRequestInterceptor... interceptors) {
    setupAuthentication(httpAsyncClientBuilder, osConfig);

    for (final HttpRequestInterceptor interceptor : interceptors) {
      httpAsyncClientBuilder.addRequestInterceptorLast(interceptor);
    }

    if (osConfig.getSecurity() != null && osConfig.getSecurity().isEnabled()) {
      setupSSLContext(httpAsyncClientBuilder, osConfig.getSecurity());
    }
    return httpAsyncClientBuilder;
  }

  private RequestConfig.Builder setTimeouts(
      final RequestConfig.Builder builder, final ConnectConfiguration os) {
    if (os.getSocketTimeout() != null) {
      // builder.setSocketTimeout(os.getSocketTimeout());
      builder.setResponseTimeout(Timeout.ofMilliseconds(os.getSocketTimeout()));
    }
    if (os.getConnectTimeout() != null) {
      builder.setConnectTimeout(Timeout.ofMilliseconds(os.getConnectTimeout()));
    }
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
        new AuthScope(getHttpHost(configuration)),
        new UsernamePasswordCredentials(
            configuration.getUsername(), configuration.getPassword().toCharArray()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
    return builder;
  }

  private void setupSSLContext(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final SecurityConfiguration configuration) {
    try {
      final var tlsStrategyBuilder = ClientTlsStrategyBuilder.create();
      final var sslContext = SecurityUtil.getSSLContext(configuration, "opensearch-host");

      tlsStrategyBuilder.setSslContext(sslContext);
      if (!configuration.isVerifyHostname()) {
        tlsStrategyBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }

      final var tlsStrategy = tlsStrategyBuilder.build();
      final var connectionManager =
          PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();

      httpAsyncClientBuilder.setConnectionManager(connectionManager);

    } catch (final Exception e) {
      LOGGER.error("Error in setting up SSLContext", e);
    }
  }
}
