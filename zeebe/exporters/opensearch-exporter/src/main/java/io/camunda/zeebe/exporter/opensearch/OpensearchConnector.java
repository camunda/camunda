/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.SearchClientConnectException;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.AuthenticationConfiguration;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.SecurityConfiguration;
import java.net.URI;
import java.net.URISyntaxException;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
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

  private final OpensearchExporterConfiguration configuration;
  private final ObjectMapper objectMapper;
  private final PluginRepository pluginRepository;

  private final AwsCredentialsProvider credentialsProvider;

  public OpensearchConnector(final OpensearchExporterConfiguration configuration) {
    this(
        configuration,
        configuration.getObjectMapper(),
        DefaultCredentialsProvider.builder().build(),
        new PluginRepository());
  }

  public OpensearchConnector(
      final OpensearchExporterConfiguration configuration,
      final ObjectMapper objectMapper,
      final AwsCredentialsProvider credentialsProvider,
      final PluginRepository pluginRepository) {
    this.configuration = configuration;
    this.objectMapper = objectMapper;
    this.credentialsProvider = credentialsProvider;
    this.pluginRepository = pluginRepository;
  }

  public static OpensearchConnector of(final OpensearchExporterConfiguration configuration) {
    return new OpensearchConnector(configuration);
  }

  public OpenSearchClient createClient() {
    // Load plugins
    pluginRepository.load(configuration.getInterceptorPlugins());

    final var transport = createTransport(configuration);

    return new OpenSearchClient(transport);
  }

  private OpenSearchTransport createTransport(final OpensearchExporterConfiguration configuration) {
    if (shouldCreateAWSBasedTransport()) {
      return createAWSBasedTransport(configuration);
    } else {
      return createDefaultTransport(configuration);
    }
  }

  private OpenSearchTransport createAWSBasedTransport(
      final OpensearchExporterConfiguration configuration) {
    final var httpClient = AwsCrtHttpClient.builder().build();
    final var hostname = getHttpHosts(configuration)[0].getHostName();
    final Region region = Region.of(configuration.aws.region);

    final AwsSdk2TransportOptions transportOptions =
        AwsSdk2TransportOptions.builder()
            .setCredentials(credentialsProvider)
            .setMapper(new JacksonJsonpMapper(objectMapper))
            .build();

    return new AwsSdk2Transport(httpClient, hostname, region, transportOptions);
  }

  private OpenSearchTransport createDefaultTransport(
      final OpensearchExporterConfiguration configuration) {
    final var hosts = getHttpHosts(configuration);
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

    builder.setMapper(new JacksonJsonpMapper(objectMapper));

    return builder.build();
  }

  private boolean shouldCreateAWSBasedTransport() {
    if (credentialsProvider == null || !configuration.aws.isEnabled()) {
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

  private HttpHost getHttpHost(final OpensearchExporterConfiguration osConfig) {
    try {
      final var uri = new URI(osConfig.getUrl());
      return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    } catch (final URISyntaxException e) {
      throw new SearchClientConnectException("Error in url: " + osConfig.getUrl(), e);
    }
  }

  private HttpHost[] getHttpHosts(final OpensearchExporterConfiguration osConfig) {
    final var urls = osConfig.getUrls();
    if (urls != null && !urls.isEmpty()) {
      return urls.stream()
          .map(
              url -> {
                try {
                  return HttpHost.create(url);
                } catch (final URISyntaxException e) {
                  throw new SearchClientConnectException("Error in url: " + url, e);
                }
              })
          .toArray(HttpHost[]::new);
    }
    return new HttpHost[] {getHttpHost(osConfig)};
  }

  protected HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final OpensearchExporterConfiguration osConfig,
      final HttpRequestInterceptor... interceptors) {
    setupAuthentication(httpAsyncClientBuilder, osConfig.getAuthentication());

    for (final HttpRequestInterceptor interceptor : interceptors) {
      httpAsyncClientBuilder.addRequestInterceptorLast(interceptor);
    }

    if (osConfig.getSecurity() != null && osConfig.getSecurity().isEnabled()) {
      setupSSLContext(httpAsyncClientBuilder, osConfig.getSecurity());
    }
    return httpAsyncClientBuilder;
  }

  private RequestConfig.Builder setTimeouts(
      final RequestConfig.Builder builder, final OpensearchExporterConfiguration os) {
    builder.setResponseTimeout(Timeout.ofMilliseconds(os.getRequestTimeoutMs()));
    builder.setConnectTimeout(Timeout.ofMilliseconds(os.getRequestTimeoutMs()));
    return builder;
  }

  private HttpAsyncClientBuilder setupAuthentication(
      final HttpAsyncClientBuilder builder, final AuthenticationConfiguration configuration) {
    final var username = configuration.getUsername();
    final var password = configuration.getPassword();

    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      LOGGER.warn(
          "Username and/or password for are OpenSearch empty. Basic authentication for OpenSearch is not used.");
      return builder;
    }

    final var credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new AuthScope(null, -1),
        new UsernamePasswordCredentials(
            configuration.getUsername(), configuration.getPassword().toCharArray()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
    return builder;
  }

  private void setupSSLContext(
      final HttpAsyncClientBuilder httpAsyncClientBuilder, final SecurityConfiguration sslConfig) {
    try {
      final var tlsStrategyBuilder = ClientTlsStrategyBuilder.create();

      if (sslConfig.isSelfSigned()) {
        final SSLContext sslContext =
            SSLContexts.custom().loadTrustMaterial(new TrustSelfSignedStrategy()).build();
        tlsStrategyBuilder.setSslContext(sslContext);
      }

      final var connectionManager =
          PoolingAsyncClientConnectionManagerBuilder.create()
              .setTlsStrategy(tlsStrategyBuilder.build())
              .build();

      httpAsyncClientBuilder.setConnectionManager(connectionManager);
    } catch (final Exception e) {
      LOGGER.error("Error in setting up SSLContext", e);
    }
  }
}
