/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.SecurityConfiguration;
import io.camunda.search.connect.es.builder.ElasticsearchClientBuilder;
import io.camunda.search.connect.es.builder.ProxyConfig;
import io.camunda.search.connect.es.builder.SslConfig;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElasticsearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchConnector.class);

  private final ConnectConfiguration configuration;
  private final ObjectMapper objectMapper;
  private final PluginRepository pluginRepository;

  public ElasticsearchConnector(final ConnectConfiguration configuration) {
    this(
        configuration,
        new JacksonConfiguration(configuration).createObjectMapper(),
        new PluginRepository());
  }

  public ElasticsearchConnector(
      final ConnectConfiguration configuration,
      final ObjectMapper objectMapper,
      final PluginRepository pluginRepository) {
    this.configuration = configuration;
    this.objectMapper = objectMapper;
    this.pluginRepository = pluginRepository;
  }

  public ElasticsearchClient createClient() {
    LOGGER.debug("Creating Elasticsearch Client ...");
    return configureBuilder().build();
  }

  public ElasticsearchAsyncClient createAsyncClient() {
    LOGGER.debug("Creating async Elasticsearch Client ...");
    return configureBuilder().buildAsync();
  }

  public ObjectMapper objectMapper() {
    return objectMapper;
  }

  private ElasticsearchClientBuilder configureBuilder() {
    // Load plugins
    pluginRepository.load(configuration.getInterceptorPlugins());

    final var builder =
        ElasticsearchClientBuilder.newInstance()
            .withObjectMapper(objectMapper)
            .withBasicAuth(configuration.getUsername(), configuration.getPassword())
            .withConnectTimeout(configuration.getConnectTimeout())
            .withSocketTimeout(configuration.getSocketTimeout())
            .withRequestInterceptors(pluginRepository.asRequestInterceptor());

    // URLs
    final var urls = configuration.getUrls();
    if (urls != null && !urls.isEmpty()) {
      builder.withUrls(urls);
    } else {
      builder.withUrl(configuration.getUrl());
    }

    // SSL
    final var security = configuration.getSecurity();
    if (security != null && security.isEnabled()) {
      builder.withSslConfig(toSslConfig(security));
    }

    // Proxy
    final var proxyConfig = configuration.getProxy();
    if (proxyConfig != null && proxyConfig.isEnabled()) {
      builder.withProxyConfig(
          ProxyConfig.builder()
              .host(proxyConfig.getHost())
              .port(proxyConfig.getPort())
              .sslEnabled(proxyConfig.isSslEnabled())
              .username(proxyConfig.getUsername())
              .password(proxyConfig.getPassword())
              .build());
    }

    return builder;
  }

  private SslConfig toSslConfig(final SecurityConfiguration security) {
    return SslConfig.builder()
        .enabled(true)
        .certificatePath(security.getCertificatePath())
        .selfSigned(security.isSelfSigned())
        .verifyHostname(security.isVerifyHostname())
        .build();
  }
}
