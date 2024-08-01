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
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.SecurityConfiguration;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.os.json.SearchRequestJacksonJsonpMapperWrapper;
import io.camunda.search.connect.util.SecurityUtil;
import java.util.Optional;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;

public class OpensearchClientConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchClientConnector.class);

  private final ConnectConfiguration configuration;
  private final ObjectMapper objectMapper;
  private final OpensearchRestClientConnector restClientDelegate;

  public OpensearchClientConnector(final ConnectConfiguration configuration) {
    this(configuration, new JacksonConfiguration(configuration).createObjectMapper());
  }

  public OpensearchClientConnector(
      final ConnectConfiguration configuration, final ObjectMapper objectMapper) {
    this.configuration = configuration;
    this.objectMapper = objectMapper;
    this.restClientDelegate = new OpensearchRestClientConnector(configuration);
  }

  public OpenSearchClient createClient() {
    final var transport = createTransport(configuration);
    return new OpenSearchClient(transport);
  }

  public OpenSearchAsyncClient createAsyncClient() {
    final var transport = createTransport(configuration);
    return new OpenSearchAsyncClient(transport);
  }

  public RestClient createRestClient() {
    return restClientDelegate.createRestClient();
  }

  private OpenSearchTransport createTransport(final ConnectConfiguration configuration) {
    return createAWSTransportIfPresent().orElseGet(this::createDefaultTransport);
  }

  private Optional<OpenSearchTransport> createAWSTransportIfPresent() {
    if (shouldCreateAWSBasedTransport()) {
      final var httpHost = getHttpHost();
      final var region = new DefaultAwsRegionProviderChain().getRegion();
      final var httpClient = NettyNioAsyncHttpClient.builder().build();
      final var transport =
          new AwsSdk2Transport(
              httpClient,
              httpHost.getHostName(),
              Region.of(region),
              AwsSdk2TransportOptions.builder()
                  .setMapper(new SearchRequestJacksonJsonpMapperWrapper(objectMapper))
                  .build());
      return Optional.of(transport);
    }
    return Optional.empty();
  }

  private OpenSearchTransport createDefaultTransport() {
    final var host = getHttpHost();
    final var builder = ApacheHttpClient5TransportBuilder.builder(host);
    final var jsonpMapper = new SearchRequestJacksonJsonpMapperWrapper(objectMapper);

    builder
        .setHttpClientConfigCallback(this::configureHttpClient)
        .setRequestConfigCallback(this::configureTimeouts)
        .setMapper(jsonpMapper);

    return builder.build();
  }

  private boolean shouldCreateAWSBasedTransport() {
    final var credentialsProvider = DefaultCredentialsProvider.create();
    try {
      credentialsProvider.resolveCredentials();
      LOGGER.info("AWS Credentials can be resolved. Use AWS Opensearch");
      return true;
    } catch (Exception e) {
      LOGGER.warn("AWS not configured due to: {} ", e.getMessage());
      return false;
    }
  }

  private HttpHost getHttpHost() {
    final var uri = configuration.getUrlAsUri();
    return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
  }

  protected HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder) {
    configureAuthentication(httpAsyncClientBuilder);
    configureSSLContext(httpAsyncClientBuilder, configuration.getSecurity());
    return httpAsyncClientBuilder;
  }

  private RequestConfig.Builder configureTimeouts(final RequestConfig.Builder builder) {
    if (configuration.getSocketTimeout() != null) {
      // builder.setSocketTimeout(os.getSocketTimeout());
      builder.setResponseTimeout(Timeout.ofMilliseconds(configuration.getSocketTimeout()));
    }
    if (configuration.getConnectTimeout() != null) {
      builder.setConnectTimeout(Timeout.ofMilliseconds(configuration.getConnectTimeout()));
    }
    return builder;
  }

  private void configureAuthentication(final HttpAsyncClientBuilder builder) {
    createDefaultCredentialsProviderIfPresent().map(builder::setDefaultCredentialsProvider);
  }

  private Optional<CredentialsProvider> createDefaultCredentialsProviderIfPresent() {
    if (configuration.hasBasicAuthenticationConfigured()) {
      final var credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
          new AuthScope(getHttpHost()),
          new UsernamePasswordCredentials(
              configuration.getUsername(), configuration.getPassword().toCharArray()));
      return Optional.of(credentialsProvider);
    }
    LOGGER.warn(
        "Username and/or password for are empty. Basic authentication for OpenSearch is not used.");
    return Optional.empty();
  }

  private void configureSSLContext(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final SecurityConfiguration configuration) {
    if (configuration != null && configuration.isEnabled()) {
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
      } catch (Exception e) {
        LOGGER.error("Error in setting up SSLContext", e);
      }
    }
  }
}
