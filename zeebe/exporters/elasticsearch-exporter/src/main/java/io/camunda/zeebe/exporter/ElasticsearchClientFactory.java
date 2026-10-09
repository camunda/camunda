/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.util.SecurityUtil;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.ProxyConfiguration;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

final class ElasticsearchClientFactory {

  static final ElasticsearchClientFactory INSTANCE = new ElasticsearchClientFactory();

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
    final var restClient = INSTANCE.createRestClient(config, interceptors);
    final var transport =
        new Rest5ClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    return new ElasticsearchClient(transport);
  }

  private Rest5Client createRestClient(
      final ElasticsearchExporterConfiguration config,
      final HttpRequestInterceptor... interceptors) {
    final HttpHost[] httpHosts = parseUrl(config);
    final Header[] defaultHeaders =
        new Header[] {
          new BasicHeader("Accept", "application/vnd.elasticsearch+json;compatible-with=9"),
          new BasicHeader("Content-Type", "application/vnd.elasticsearch+json;compatible-with=9")
        };
    final Rest5ClientBuilder builder =
        Rest5Client.builder(httpHosts)
            .setDefaultHeaders(defaultHeaders)
            .setConnectionConfigCallback(
                b ->
                    b.setConnectTimeout(Timeout.ofMilliseconds(config.requestTimeoutMs))
                        .setSocketTimeout(Timeout.ofMilliseconds(config.requestTimeoutMs)))
            .setHttpClientConfigCallback(b -> configureHttpClient(config, b, interceptors));

    return builder.build();
  }

  HttpAsyncClientBuilder configureHttpClient(
      final ElasticsearchExporterConfiguration config,
      final HttpAsyncClientBuilder builder,
      final HttpRequestInterceptor... interceptors) {
    // use single thread for rest client; TCP keepalive guards against a firewall or NAT device in
    // front of Elasticsearch silently dropping its tracking entry for a connection left idle
    // between flushes, which would leave the exporter blocked on a dead socket until it times out.
    // In HC5 it is active by default, but we keep it explicit in case the default ever changes.
    builder.setIOReactorConfig(
        IOReactorConfig.custom().setIoThreadCount(1).setSoKeepAlive(true).build());

    if (config.hasAuthenticationPresent()) {
      setupBasicAuthentication(config, builder);
    }

    if (config.hasProxyConfigured()) {
      setupProxy(builder, config.getProxy());
      addPreemptiveProxyAuthInterceptor(builder, config.getProxy());
    }

    for (final var interceptor : interceptors) {
      builder.addRequestInterceptorLast(interceptor);
    }

    return builder;
  }

  private void setupBasicAuthentication(
      final ElasticsearchExporterConfiguration config, final HttpAsyncClientBuilder builder) {
    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        SecurityUtil.ANY_AUTH_SCOPE,
        new UsernamePasswordCredentials(
            config.getAuthentication().getUsername(),
            config.getAuthentication().getPassword().toCharArray()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
  }

  private void setupProxy(
      final HttpAsyncClientBuilder builder, final ProxyConfiguration proxyConfig) {
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

    builder.setProxy(new HttpHost(proxyConfig.isSslEnabled() ? "https" : "http", host, port));
  }

  private void addPreemptiveProxyAuthInterceptor(
      final HttpAsyncClientBuilder builder, final ProxyConfiguration proxyConfig) {
    final String username = proxyConfig.getUsername();
    final String password = proxyConfig.getPassword();

    if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
      return;
    }

    final String credentials = username + ":" + password;
    final String encodedCredentials =
        Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    final String proxyAuthHeaderValue = "Basic " + encodedCredentials;

    builder.addRequestInterceptorFirst(
        (request, entity, context) -> {
          if (!request.containsHeader("Proxy-Authorization")) {
            request.addHeader("Proxy-Authorization", proxyAuthHeaderValue);
          }
        });
  }

  private HttpHost[] parseUrl(final ElasticsearchExporterConfiguration config) {
    final var urls = config.url.split(",");
    final var hosts = new HttpHost[urls.length];

    for (int i = 0; i < urls.length; i++) {
      try {
        hosts[i] = HttpHost.create(urls[i]);
      } catch (final URISyntaxException e) {
        throw new IllegalArgumentException("Error in url: " + urls[i], e);
      }
    }

    return hosts;
  }
}
