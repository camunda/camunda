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
import io.camunda.search.connect.es.builder.ElasticsearchClientBuilder;
import io.camunda.search.connect.es.builder.ProxyConfig;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.ProxyConfiguration;
import java.util.Arrays;
import org.apache.http.HttpRequestInterceptor;

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
    final var builder =
        ElasticsearchClientBuilder.builder()
            .withUrls(Arrays.asList(config.url.split(",")))
            .withObjectMapper(objectMapper)
            .withConnectTimeout(config.requestTimeoutMs)
            .withSocketTimeout(config.requestTimeoutMs)
            .withIoThreadCount(1)
            .withRequestInterceptors(interceptors);

    if (config.hasAuthenticationPresent()) {
      builder.withBasicAuth(
          config.getAuthentication().getUsername(), config.getAuthentication().getPassword());
    }

    if (config.hasProxyConfigured()) {
      final ProxyConfiguration proxyConfig = config.getProxy();
      builder.withProxyConfig(
          ProxyConfig.builder()
              .host(proxyConfig.getHost())
              .port(proxyConfig.getPort() != null ? proxyConfig.getPort() : 0)
              .sslEnabled(proxyConfig.isSslEnabled())
              .username(proxyConfig.getUsername())
              .password(proxyConfig.getPassword())
              .build());
    }

    return builder.build();
  }
}
