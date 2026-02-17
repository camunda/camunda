/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.property.ElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.ProxyProperties;
import io.camunda.operate.property.SslProperties;
import io.camunda.search.connect.es.builder.ElasticsearchClientBuilder;
import io.camunda.search.connect.es.builder.ElasticsearchHealthCheck;
import io.camunda.search.connect.es.builder.ProxyConfig;
import io.camunda.search.connect.es.builder.SslConfig;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.zeebe.util.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Conditional(ElasticsearchCondition.class)
@Configuration
public class ElasticsearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchConnector.class);

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  private PluginRepository esClientRepository = new PluginRepository();
  private final OperateProperties operateProperties;

  public ElasticsearchConnector(final OperateProperties operateProperties) {
    this.operateProperties = operateProperties;
  }

  @VisibleForTesting
  public void setEsClientRepository(final PluginRepository esClientRepository) {
    this.esClientRepository = esClientRepository;
  }

  @VisibleForTesting
  public void setObjectMapper(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Bean
  public ElasticsearchClient esClient() {
    esClientRepository.load(operateProperties.getElasticsearch().getInterceptorPlugins());
    return createEsClient(operateProperties.getElasticsearch(), esClientRepository);
  }

  public ElasticsearchClient createEsClient(
      final ElasticsearchProperties elsConfig, final PluginRepository pluginRepository) {
    LOGGER.debug("Creating Elasticsearch connection...");

    final var client = configureBuilder(elsConfig, pluginRepository).build();

    if (operateProperties.getElasticsearch().isHealthCheckEnabled()) {
      if (!checkHealth(client)) {
        LOGGER.warn("Elasticsearch cluster is not accessible");
      } else {
        LOGGER.debug("Elasticsearch connection was successfully created.");
      }
    } else {
      LOGGER.warn("Elasticsearch cluster health check is disabled.");
    }
    return client;
  }

  private ElasticsearchClientBuilder configureBuilder(
      final ElasticsearchProperties elsConfig, final PluginRepository pluginRepository) {
    final var builder =
        ElasticsearchClientBuilder.newInstance()
            .withObjectMapper(objectMapper)
            .withBasicAuth(elsConfig.getUsername(), elsConfig.getPassword())
            .withConnectTimeout(elsConfig.getConnectTimeout())
            .withSocketTimeout(elsConfig.getSocketTimeout())
            .withRequestInterceptors(pluginRepository.asRequestInterceptor());

    // URLs
    final var urls = elsConfig.getUrls();
    if (urls != null && !urls.isEmpty()) {
      builder.withUrls(urls);
    } else {
      builder.withUrl(elsConfig.getUrl());
    }

    // SSL
    final SslProperties sslConfig = elsConfig.getSsl();
    if (sslConfig != null && sslConfig.getCertificatePath() != null) {
      builder.withSslConfig(toSslConfig(sslConfig));
    }

    // Proxy
    final ProxyProperties proxyConfig = elsConfig.getProxy();
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

  private SslConfig toSslConfig(final SslProperties sslConfig) {
    return SslConfig.builder()
        .enabled(true)
        .certificatePath(sslConfig.getCertificatePath())
        .selfSigned(sslConfig.isSelfSigned())
        .verifyHostname(sslConfig.isVerifyHostname())
        .build();
  }

  @VisibleForTesting
  boolean checkHealth(final ElasticsearchClient esClient) {
    final ElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    return ElasticsearchHealthCheck.builder()
        .client(esClient)
        .expectedClusterName(elsConfig.getClusterName())
        .build()
        .check();
  }
}
