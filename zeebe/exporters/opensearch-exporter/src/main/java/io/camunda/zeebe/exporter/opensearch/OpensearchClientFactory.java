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
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.ProxyConfiguration;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.SecurityConfiguration;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.IntStream;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * Creates the exporter's OpenSearch client with the shared {@link
 * io.camunda.search.connect.os.OpensearchConnector}, from the exporter's own configuration.
 */
public final class OpensearchClientFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchClientFactory.class);

  private final OpensearchExporterConfiguration configuration;
  private final ObjectMapper objectMapper;
  private final PluginRepository pluginRepository;

  private final AwsCredentialsProvider credentialsProvider;

  public OpensearchClientFactory(final OpensearchExporterConfiguration configuration) {
    this(
        configuration,
        configuration.getObjectMapper(),
        DefaultCredentialsProvider.builder().build(),
        new PluginRepository());
  }

  public OpensearchClientFactory(
      final OpensearchExporterConfiguration configuration,
      final ObjectMapper objectMapper,
      final AwsCredentialsProvider credentialsProvider,
      final PluginRepository pluginRepository) {
    this.configuration = configuration;
    this.objectMapper = objectMapper;
    this.credentialsProvider = credentialsProvider;
    this.pluginRepository = pluginRepository;
  }

  public static OpensearchClientFactory of(final OpensearchExporterConfiguration configuration) {
    return new OpensearchClientFactory(configuration);
  }

  public OpenSearchClient createClient() {
    return io.camunda.search.connect.os.OpensearchConnector.builder()
        .configuration(toConnectConfiguration())
        .objectMapper(objectMapper)
        .credentialsProvider(credentialsProvider)
        .pluginRepository(pluginRepository)
        .httpHosts(() -> getHttpHosts(configuration))
        // the exporter does not use the mapper that also serializes search-after fields
        .jsonpMapper(new JacksonJsonpMapper(objectMapper))
        // only resolved when the AWS transport is used, as it has always been
        .awsRegion(() -> Region.of(configuration.aws.region))
        .awsTransportCredentials(credentialsProvider)
        .tlsStrategy(tlsStrategy(configuration.getSecurity()))
        .build()
        .createClient();
  }

  /**
   * Maps what the exporter's client needs onto the connector's configuration. The URLs and TLS are
   * not part of it: they are handed to the connector as hosts and as a TLS strategy.
   */
  private ConnectConfiguration toConnectConfiguration() {
    final var connect = new ConnectConfiguration();
    connect.setUsername(configuration.getAuthentication().getUsername());
    connect.setPassword(configuration.getAuthentication().getPassword());
    // the exporter has one timeout for both
    connect.setConnectTimeout(configuration.getRequestTimeoutMs());
    connect.setSocketTimeout(configuration.getRequestTimeoutMs());
    connect.setInterceptorPlugins(configuration.getInterceptorPlugins());
    // without a credentials provider AWS is never used
    connect.setAwsEnabled(credentialsProvider != null && configuration.aws.isEnabled());

    final ProxyConfiguration proxy = configuration.getProxy();
    if (proxy != null && proxy.isEnabled()) {
      connect.setProxy(toConnectProxy(proxy));
    }
    return connect;
  }

  private static io.camunda.search.connect.configuration.ProxyConfiguration toConnectProxy(
      final ProxyConfiguration proxyConfig) {
    final var proxy = new io.camunda.search.connect.configuration.ProxyConfiguration();
    proxy.setEnabled(true);
    proxy.setHost(proxyConfig.getHost());
    proxy.setPort(proxyConfig.getPort());
    proxy.setSslEnabled(proxyConfig.isSslEnabled());
    proxy.setUsername(proxyConfig.getUsername());
    proxy.setPassword(proxyConfig.getPassword());
    return proxy;
  }

  private static TlsStrategy tlsStrategy(final SecurityConfiguration security) {
    if (security == null || !security.isEnabled()) {
      return null;
    }

    try {
      final var tlsStrategyBuilder = ClientTlsStrategyBuilder.create();

      if (security.isSelfSigned()) {
        final SSLContext sslContext =
            SSLContexts.custom().loadTrustMaterial(new TrustSelfSignedStrategy()).build();
        tlsStrategyBuilder.setSslContext(sslContext);
      }

      return tlsStrategyBuilder.build();
    } catch (final Exception e) {
      LOGGER.error("Error in setting up SSLContext", e);
      return null;
    }
  }

  private static HttpHost getHttpHost(final OpensearchExporterConfiguration osConfig) {
    try {
      final var uri = new URI(osConfig.getUrl());
      return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    } catch (final URISyntaxException e) {
      // neither the url nor the cause is included: both quote the unparseable text, which may
      // hold credentials
      throw new SearchClientConnectException("Error in url");
    }
  }

  private static HttpHost[] getHttpHosts(final OpensearchExporterConfiguration osConfig) {
    final var urls = osConfig.getUrls();
    if (urls != null && !urls.isEmpty()) {
      return IntStream.range(0, urls.size())
          .mapToObj(
              i -> {
                try {
                  return HttpHost.create(urls.get(i));
                } catch (final URISyntaxException e) {
                  throw new SearchClientConnectException("Error in url at position " + i);
                }
              })
          .toArray(HttpHost[]::new);
    }
    return new HttpHost[] {getHttpHost(osConfig)};
  }
}
