/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchClientConnector;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@Conditional(OpensearchCondition.class)
public class OpensearchClientProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchClientProvider.class);

  private final OperateProperties operateProperties;
  private final ObjectMapper objectMapper;
  private final ConnectConfiguration operateConnectConfiguration;
  private final ConnectConfiguration zeebeConnectConfiguration;

  @Autowired
  public OpensearchClientProvider(
      final OperateProperties operateProperties,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    this.operateProperties = operateProperties;
    this.objectMapper = objectMapper;
    operateConnectConfiguration = mapToConnectConfiguration(operateProperties.getOpensearch());
    zeebeConnectConfiguration = mapToConnectConfiguration(operateProperties.getZeebeOpensearch());
  }

  private ConnectConfiguration mapToConnectConfiguration(final OpensearchProperties properties) {
    final var configuration = new ConnectConfiguration();
    configuration.setType("opensearch");
    configuration.setClusterName(properties.getClusterName());
    configuration.setUrl(properties.getUrl());
    configuration.setDateFormat(properties.getDateFormat());
    configuration.setFieldDateFormat(properties.getOsDateFormat());
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

  private OpensearchClientConnector createOpensearchConnector(final ConnectConfiguration configuration) {
    return createOpensearchConnector(configuration, objectMapper);
  }

  protected OpensearchClientConnector createOpensearchConnector(
      final ConnectConfiguration configuration, final ObjectMapper objectMapper) {
    return new OpensearchClientConnector(configuration, objectMapper);
  }

  @Bean
  @Primary
  public OpenSearchClient openSearchClient() {
    final var delegate = createOpensearchConnector(operateConnectConfiguration);
    final var openSearchClient = delegate.createClient();
    try {
      final HealthResponse response = openSearchClient.cluster().health();
      LOGGER.info("OpenSearch cluster health: {}", response.status());
    } catch (IOException e) {
      LOGGER.error("Error in getting health status from {}", "localhost:9205", e);
    }
    return openSearchClient;
  }

  @Bean
  public OpenSearchAsyncClient openSearchAsyncClient() {
    final var delegate = createOpensearchConnector(operateConnectConfiguration);
    final var openSearchClient = delegate.createAsyncClient();
    final CompletableFuture<HealthResponse> healthResponse;
    try {
      healthResponse = openSearchClient.cluster().health();
      healthResponse.whenComplete(
          (response, e) -> {
            if (e != null) {
              LOGGER.error("Error in getting health status from {}", "localhost:9205", e);
            } else {
              LOGGER.info("OpenSearch cluster health: {}", response.status());
            }
          });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return openSearchClient;
  }

  @Bean("zeebeOpensearchClient")
  public OpenSearchClient zeebeOpensearchClient() {
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    return createOsClient(zeebeConnectConfiguration);
  }

  public OpenSearchClient createOsClient(final ConnectConfiguration configuration) {
    LOGGER.debug("Creating OpenSearch connection...");
    final var delegate = createOpensearchConnector(configuration);
    final var openSearchClient = delegate.createClient();

    try {
      final HealthResponse response = openSearchClient.cluster().health();
      LOGGER.info("OpenSearch cluster health: {}", response.status());
    } catch (IOException e) {
      LOGGER.error("Error in getting health status from {}", "localhost:9205", e);
    }

    if (!checkHealth(openSearchClient)) {
      LOGGER.warn("OpenSearch cluster is not accessible");
    } else {
      LOGGER.debug("Opensearch connection was successfully created.");
    }
    return openSearchClient;
  }

  public boolean checkHealth(final OpenSearchClient osClient) {
    final OpensearchProperties osConfig = operateProperties.getOpensearch();
    final RetryPolicy<Boolean> retryPolicy = getConnectionRetryPolicy(osConfig);
    return Failsafe.with(retryPolicy)
        .get(
            () -> {
              final HealthResponse clusterHealthResponse =
                  osClient.cluster().health(new HealthRequest.Builder().build());
              return clusterHealthResponse.clusterName().equals(osConfig.getClusterName());
            });
  }

  public boolean checkHealth(final OpenSearchAsyncClient osAsyncClient) {
    final OpensearchProperties osConfig = operateProperties.getOpensearch();
    final RetryPolicy<Boolean> retryPolicy = getConnectionRetryPolicy(osConfig);
    return Failsafe.with(retryPolicy)
        .get(
            () -> {
              final CompletableFuture<HealthResponse> clusterHealthResponse =
                  osAsyncClient.cluster().health(new HealthRequest.Builder().build());
              clusterHealthResponse.whenComplete(
                  (response, e) -> {
                    if (e != null) {
                      LOGGER.error(String.format("Error checking async health %", e.getMessage()));
                    } else {
                      LOGGER.debug("Succesfully returned checkHealth");
                    }
                  });
              return clusterHealthResponse.get().clusterName().equals(osConfig.getClusterName());
            });
  }

  private RetryPolicy<Boolean> getConnectionRetryPolicy(final OpensearchProperties osConfig) {
    final String logMessage = String.format("connect to OpenSearch at %s", osConfig.getUrl());
    return new RetryPolicy<Boolean>()
        .handle(IOException.class, OpenSearchException.class)
        .withDelay(Duration.ofSeconds(3))
        .withMaxAttempts(50)
        .onRetry(
            e ->
                LOGGER.info(
                    "Retrying #{} {} due to {}",
                    e.getAttemptCount(),
                    logMessage,
                    e.getLastFailure()))
        .onAbort(e -> LOGGER.error("Abort {} by {}", logMessage, e.getFailure()))
        .onRetriesExceeded(
            e -> LOGGER.error("Retries {} exceeded for {}", e.getAttemptCount(), logMessage));
  }
}
