/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.TransportOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.es.schema.TransportOptionsProvider;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ProxyConfiguration;
import io.camunda.search.connect.es.builder.ProxyConfig;
import io.camunda.search.connect.es.builder.SslConfig;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchClientBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchClientBuilder.class);

  public static ElasticsearchClient build(
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper,
      final PluginRepository pluginRepository) {
    final var transportOptions = getTransportOptions(configurationService);
    return configureBuilder(configurationService, pluginRepository)
        .withObjectMapper(objectMapper)
        .withTransportOptions(transportOptions)
        .build();
  }

  public static String getCurrentESVersion(
      final ElasticsearchClient esClient, final TransportOptions requestOptions)
      throws IOException {
    return esClient.withTransportOptions(requestOptions).info().version().number();
  }

  public static RestClient restClient(
      final ConfigurationService configurationService, final PluginRepository pluginRepository) {
    return configureBuilder(configurationService, pluginRepository).buildRestClient();
  }

  public static TransportOptions getTransportOptions(
      final ConfigurationService configurationService) {
    final TransportOptionsProvider transportOptionsProvider =
        new TransportOptionsProvider(configurationService);
    return transportOptionsProvider.getTransportOptions();
  }

  private static io.camunda.search.connect.es.builder.ElasticsearchClientBuilder configureBuilder(
      final ConfigurationService configurationService, final PluginRepository pluginRepository) {
    final var esConfig = configurationService.getElasticSearchConfiguration();

    // Load plugins
    final List<PluginConfiguration> plugins =
        extractPluginConfigs(esConfig.getInterceptorPlugins());
    pluginRepository.load(plugins);

    // Build URLs from connection nodes
    final boolean sslEnabled = Boolean.TRUE.equals(esConfig.getSecuritySSLEnabled());
    final String protocol = sslEnabled ? "https" : "http";
    final List<String> urls = new ArrayList<>();
    for (final var node : esConfig.getConnectionNodes()) {
      urls.add(protocol + "://" + node.getHost() + ":" + node.getHttpPort());
    }

    final var builder =
        io.camunda.search.connect.es.builder.ElasticsearchClientBuilder.newInstance()
            .withUrls(urls)
            .withBasicAuth(esConfig.getSecurityUsername(), esConfig.getSecurityPassword())
            .withConnectTimeout(esConfig.getConnectionTimeout())
            .withSocketTimeout(0) // Optimize uses infinite socket timeout
            .withRequestInterceptors(pluginRepository.asRequestInterceptor());

    // Path prefix
    if (!StringUtils.isEmpty(esConfig.getPathPrefix())) {
      builder.withPathPrefix(esConfig.getPathPrefix());
    }

    // SSL
    if (sslEnabled) {
      builder.withSslConfig(
          SslConfig.builder()
              .enabled(true)
              .certificatePath(esConfig.getSecuritySSLCertificate())
              .certificateAuthorities(esConfig.getSecuritySSLCertificateAuthorities())
              .selfSigned(Boolean.TRUE.equals(esConfig.getSecuritySslSelfSigned()))
              .verifyHostname(!Boolean.TRUE.equals(esConfig.getSkipHostnameVerification()))
              .build());
    }

    // Proxy
    final ProxyConfiguration proxyConfig = esConfig.getProxyConfig();
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

  private static List<PluginConfiguration> extractPluginConfigs(
      final Map<String, PluginConfiguration> pluginConfigs) {
    if (pluginConfigs != null) {
      return pluginConfigs.values().stream()
          .filter(plugin -> StringUtils.isNotBlank(plugin.id()))
          .toList();
    }
    return List.of();
  }
}
