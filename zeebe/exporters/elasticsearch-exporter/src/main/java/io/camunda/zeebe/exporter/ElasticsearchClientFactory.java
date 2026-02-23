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
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.ProxyConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

final class ElasticsearchClientFactory {

  private static final ElasticsearchClientFactory INSTANCE = new ElasticsearchClientFactory();

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
    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    return new ElasticsearchClient(transport);
  }

  private RestClient createRestClient(
      final ElasticsearchExporterConfiguration config,
      final HttpRequestInterceptor... interceptors) {
    final HttpHost[] httpHosts = parseUrl(config);
    final RestClientBuilder builder =
        RestClient.builder(httpHosts)
            .setRequestConfigCallback(
                b ->
                    b.setConnectTimeout(config.requestTimeoutMs)
                        .setSocketTimeout(config.requestTimeoutMs))
            .setHttpClientConfigCallback(b -> configureHttpClient(config, b, interceptors));

    return builder.build();
  }

  private HttpAsyncClientBuilder configureHttpClient(
      final ElasticsearchExporterConfiguration config,
      final HttpAsyncClientBuilder builder,
      final HttpRequestInterceptor... interceptors) {
    // use single thread for rest client
    builder.setDefaultIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1).build());

    if (config.hasAuthenticationPresent()) {
      setupBasicAuthentication(config, builder);
    }

    if (config.hasProxyConfigured()) {
      setupProxy(builder, config.getProxy());
      addPreemptiveProxyAuthInterceptor(builder, config.getProxy());
    }

    for (final var interceptor : interceptors) {
      builder.addInterceptorLast(interceptor);
    }

    return builder;
  }

  private void setupBasicAuthentication(
      final ElasticsearchExporterConfiguration config, final HttpAsyncClientBuilder builder) {
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(
            config.getAuthentication().getUsername(), config.getAuthentication().getPassword()));

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

    builder.setProxy(new HttpHost(host, port, proxyConfig.isSslEnabled() ? "https" : "http"));
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

    builder.addInterceptorFirst(
        (HttpRequestInterceptor)
            (request, context) -> {
              if (!request.containsHeader("Proxy-Authorization")) {
                request.addHeader("Proxy-Authorization", proxyAuthHeaderValue);
              }
            });
  }

  private HttpHost[] parseUrl(final ElasticsearchExporterConfiguration config) {
    final var urls = config.url.split(",");
    final var hosts = new HttpHost[urls.length];

    for (int i = 0; i < urls.length; i++) {
      hosts[i] = HttpHost.create(urls[i]);
    }

    return hosts;
  }
}
