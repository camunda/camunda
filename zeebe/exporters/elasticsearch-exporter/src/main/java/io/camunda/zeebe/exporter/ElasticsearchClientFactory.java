/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.ProxyConfiguration;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;

/**
 * Creates the exporter's Elasticsearch client with the shared {@link ElasticsearchConnector}, from
 * the exporter's own configuration.
 */
final class ElasticsearchClientFactory {

  private ElasticsearchClientFactory() {}

  /**
   * Returns a {@link ElasticsearchClient} instance based on the given configuration. The URL is
   * parsed as a comma separated list of "host:port" formatted strings. Authentication is supported
   * only as basic auth; if there is no authentication present, then nothing is configured for it.
   */
  static ElasticsearchClient of(
      final ElasticsearchExporterConfiguration config,
      final HttpRequestInterceptor... interceptors) {
    return of(config, new ObjectMapper(), interceptors);
  }

  /**
   * Returns a {@link ElasticsearchClient} instance using the given {@link ObjectMapper} for JSON
   * serialization/deserialization. This allows callers to customize the mapper, e.g. to register
   * additional modules for deserializing specific types.
   */
  static ElasticsearchClient of(
      final ElasticsearchExporterConfiguration config,
      final ObjectMapper objectMapper,
      final HttpRequestInterceptor... interceptors) {
    return ElasticsearchConnector.builder()
        .configuration(toConnectConfiguration(config))
        .objectMapper(objectMapper)
        // the exporter runs its client on a single I/O thread, as it has always done
        .ioThreadCount(1)
        // the exporter never set this, so it keeps the HTTP client's own default
        .connectionRequestTimeoutMillis(null)
        .requestInterceptors(interceptors)
        // every partition builds a client, so a missing login would be logged for each of them
        .warnOnMissingCredentials(false)
        .build()
        .createClient();
  }

  /**
   * Maps what the exporter's client needs onto the connector's configuration. The URL and proxy are
   * validated here so that the exporter-specific messages are kept; the URL message in particular
   * must not quote the unparseable text, which may hold credentials.
   */
  static ConnectConfiguration toConnectConfiguration(
      final ElasticsearchExporterConfiguration config) {
    final var connect = new ConnectConfiguration();
    connect.setUrls(parseUrls(config));
    connect.setUsername(config.getAuthentication().getUsername());
    connect.setPassword(config.getAuthentication().getPassword());
    // the exporter has one timeout for both
    connect.setConnectTimeout(config.requestTimeoutMs);
    connect.setSocketTimeout(config.requestTimeoutMs);

    if (config.hasProxyConfigured()) {
      connect.setProxy(toConnectProxy(config.getProxy()));
    }
    return connect;
  }

  private static List<String> parseUrls(final ElasticsearchExporterConfiguration config) {
    final var urls = config.url.split(",");
    final var parsed = new ArrayList<String>(urls.length);

    for (int i = 0; i < urls.length; i++) {
      try {
        HttpHost.create(urls[i]);
        parsed.add(urls[i]);
      } catch (final URISyntaxException | IllegalArgumentException e) {
        // neither the url nor the cause is included: both quote the unparseable text, which may
        // hold credentials
        throw new IllegalArgumentException("Invalid Elasticsearch url at position " + i);
      }
    }

    return parsed;
  }

  private static io.camunda.search.connect.configuration.ProxyConfiguration toConnectProxy(
      final ProxyConfiguration proxyConfig) {
    final String host = proxyConfig.getHost();
    final Integer port = proxyConfig.getPort();

    if (host == null || host.trim().isEmpty()) {
      throw new IllegalArgumentException(
          "Elasticsearch exporter proxy is enabled but no proxy host is configured");
    }

    if (port == null) {
      throw new IllegalArgumentException(
          "Elasticsearch exporter proxy is enabled but no proxy port is configured");
    }

    if (port <= 0 || port > 65_535) {
      throw new IllegalArgumentException(
          "Elasticsearch exporter proxy port must be between 1 and 65535, but was: " + port);
    }

    final var proxy = new io.camunda.search.connect.configuration.ProxyConfiguration();
    proxy.setEnabled(true);
    proxy.setHost(host);
    proxy.setPort(port);
    proxy.setSslEnabled(proxyConfig.isSslEnabled());
    proxy.setUsername(proxyConfig.getUsername());
    proxy.setPassword(proxyConfig.getPassword());
    return proxy;
  }
}
