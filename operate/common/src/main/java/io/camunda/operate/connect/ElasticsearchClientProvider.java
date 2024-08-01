/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.ElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.RetryOperation;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RestHighLevelClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Conditional(ElasticsearchCondition.class)
@Configuration
public class ElasticsearchClientProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchClientProvider.class);

  private final OperateProperties operateProperties;
  private final ObjectMapper objectMapper;
  private final ConnectConfiguration operateConnectConfiguration;
  private final ConnectConfiguration zeebeConnectConfiguration;

  private ElasticsearchClient elasticsearchClient;

  @Autowired
  public ElasticsearchClientProvider(
      final OperateProperties operateProperties,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    this.operateProperties = operateProperties;
    this.objectMapper = objectMapper;
    operateConnectConfiguration = mapToConnectConfiguration(operateProperties.getElasticsearch());
    zeebeConnectConfiguration =
        mapToConnectConfiguration(operateProperties.getZeebeElasticsearch());
  }

  public static void closeEsClient(final RestHighLevelClient esClient) {
    if (esClient != null) {
      try {
        esClient.close();
      } catch (IOException e) {
        LOGGER.error("Could not close esClient", e);
      }
    }
  }

  public static void closeEsClient(final ElasticsearchClient esClient) {
    if (esClient != null) {
      esClient.shutdown();
    }
  }

  protected ConnectConfiguration mapToConnectConfiguration(
      final ElasticsearchProperties properties) {
    final var configuration = new ConnectConfiguration();
    configuration.setType("elasticsearch");
    configuration.setClusterName(properties.getClusterName());
    configuration.setUrl(properties.getUrl());
    configuration.setDateFormat(properties.getDateFormat());
    configuration.setFieldDateFormat(properties.getElsDateFormat());
    configuration.setSocketTimeout(properties.getSocketTimeout());
    configuration.setConnectTimeout(properties.getConnectTimeout());
    configuration.setUsername(properties.getUsername());
    configuration.setPassword(properties.getPassword());

    final var sslProperties = properties.getSsl();
    if (sslProperties != null) {
      final var securityConfiguration = configuration.getSecurity();
      securityConfiguration.setEnabled(true);
      securityConfiguration.setCertificatePath(sslProperties.getCertificatePath());
      securityConfiguration.setVerifyHostname(sslProperties.isVerifyHostname());
      securityConfiguration.setSelfSigned(sslProperties.isSelfSigned());
    }

    return configuration;
  }

  private ElasticsearchConnector createElasticsearchConnector(
      final ConnectConfiguration configuration) {
    return createElasticsearchConnector(configuration, objectMapper);
  }

  protected ElasticsearchConnector createElasticsearchConnector(
      final ConnectConfiguration configuration, final ObjectMapper objectMapper) {
    return new ElasticsearchConnector(configuration, objectMapper);
  }

  @Bean
  public ElasticsearchClient elasticsearchClient() {
    final var delegate = createElasticsearchConnector(operateConnectConfiguration);
    elasticsearchClient = delegate.createClient();

    if (!checkHealth(elasticsearchClient)) {
      LOGGER.warn("Elasticsearch cluster is not accessible");
    } else {
      LOGGER.debug("Elasticsearch connection was successfully created.");
    }
    return elasticsearchClient;
  }

  @Bean
  public RestHighLevelClient esClient() {
    // some weird error when ELS sets available processors number for Netty - see
    // https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    return createHighLevelClient(operateConnectConfiguration);
  }

  @Bean("zeebeEsClient")
  public RestHighLevelClient zeebeEsClient() {
    // some weird error when ELS sets available processors number for Netty - see
    // https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    return createHighLevelClient(zeebeConnectConfiguration);
  }

  private RestHighLevelClient createHighLevelClient(final ConnectConfiguration configuration) {
    LOGGER.debug("Creating Elasticsearch connection...");
    final var delegate = createElasticsearchConnector(configuration);
    final var restClient = delegate.createRestClient();

    final RestHighLevelClient esClient =
        new RestHighLevelClientBuilder(restClient).setApiCompatibilityMode(true).build();

    if (!checkHealth(esClient)) {
      LOGGER.warn("Elasticsearch cluster is not accessible");
    } else {
      LOGGER.debug("Elasticsearch connection was successfully created.");
    }
    return esClient;
  }

  @PreDestroy
  public void tearDown() {
    if (elasticsearchClient != null) {
      try {
        elasticsearchClient._transport().close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public boolean checkHealth(final ElasticsearchClient elasticsearchClient) {
    final ElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    try {
      return RetryOperation.<Boolean>newBuilder()
          .noOfRetry(50)
          .retryOn(
              IOException.class,
              co.elastic.clients.elasticsearch._types.ElasticsearchException.class)
          .delayInterval(3, TimeUnit.SECONDS)
          .message(
              String.format(
                  "Connect to Elasticsearch cluster [%s] at %s",
                  elsConfig.getClusterName(), elsConfig.getUrl()))
          .retryConsumer(
              () -> {
                final HealthResponse healthResponse = elasticsearchClient.cluster().health();
                LOGGER.info("Elasticsearch cluster health: {}", healthResponse.status());
                return healthResponse.clusterName().equals(elsConfig.getClusterName());
              })
          .build()
          .retry();
    } catch (Exception e) {
      throw new OperateRuntimeException("Couldn't connect to Elasticsearch. Abort.", e);
    }
  }

  public boolean checkHealth(final RestHighLevelClient esClient) {
    final ElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    try {
      return RetryOperation.<Boolean>newBuilder()
          .noOfRetry(50)
          .retryOn(IOException.class, ElasticsearchException.class)
          .delayInterval(3, TimeUnit.SECONDS)
          .message(
              String.format(
                  "Connect to Elasticsearch cluster [%s] at %s",
                  elsConfig.getClusterName(), elsConfig.getUrl()))
          .retryConsumer(
              () -> {
                final ClusterHealthResponse clusterHealthResponse =
                    esClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
                return clusterHealthResponse.getClusterName().equals(elsConfig.getClusterName());
              })
          .build()
          .retry();
    } catch (Exception e) {
      throw new OperateRuntimeException("Couldn't connect to Elasticsearch. Abort.", e);
    }
  }
}
