/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.util.ssl.SslContextBuilders;
import javax.net.ssl.SSLContext;
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

final class RestClientFactory {

  private static final RestClientFactory INSTANCE = new RestClientFactory();

  private RestClientFactory() {}

  /**
   * Returns a {@link RestClient} instance based on the given configuration. The URL is parsed as a
   * comma separated list of "host:port" formatted strings. Authentication is supported only as
   * basic auth; if there is no authentication present, then nothing is configured for it.
   */
  static RestClient of(
      final ElasticsearchExporterConfiguration config,
      final HttpRequestInterceptor... interceptors) {
    return INSTANCE.createRestClient(config, interceptors);
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

    for (final var interceptor : interceptors) {
      builder.addInterceptorLast(interceptor);
    }

    if (config.security.isEnabled()) {
      builder.setSSLContext(buildSslContext(config));
    }

    return builder;
  }

  private SSLContext buildSslContext(final ElasticsearchExporterConfiguration config) {
    try {
      return SslContextBuilders.apacheClientContextBuilder(config.security).build();
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to configure SSL trust manager for the exporter", e);
    }
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

  private HttpHost[] parseUrl(final ElasticsearchExporterConfiguration config) {
    final var urls = config.url.split(",");
    final var hosts = new HttpHost[urls.length];

    for (int i = 0; i < urls.length; i++) {
      hosts[i] = HttpHost.create(urls[i]);
    }

    return hosts;
  }
}
