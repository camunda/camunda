/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import static io.camunda.search.connect.configuration.ConnectConfiguration.DEFAULT_SOCKET_TIMEOUT_MS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.ElasticsearchProperties;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import org.apache.http.client.config.RequestConfig;
import org.junit.jupiter.api.Test;

class ElasticsearchConnectorTest {

  @Test
  public void shouldNotDoClusterHealthCheckWhenDisabled() {
    final OperateProperties operateProperties = new OperateProperties();
    final OperateElasticsearchProperties esProperties = new OperateElasticsearchProperties();
    esProperties.setHealthCheckEnabled(false);
    operateProperties.setElasticsearch(esProperties);
    final ElasticsearchConnector connector = spy(new ElasticsearchConnector(operateProperties));
    connector.setObjectMapper(new ObjectMapper());

    connector.createEsClient(esProperties, mock());

    verify(connector, never()).checkHealth(any(ElasticsearchClient.class));
  }

  @Test
  public void shouldDoClusterHealthCheckWhenDefaultPropertyValuesUsed() {
    final OperateProperties operateProperties = new OperateProperties();
    final OperateElasticsearchProperties esProperties = new OperateElasticsearchProperties();
    operateProperties.setElasticsearch(esProperties);
    final ElasticsearchConnector connector = spy(new ElasticsearchConnector(operateProperties));
    connector.setObjectMapper(new ObjectMapper());
    doReturn(true).when(connector).checkHealth(any(ElasticsearchClient.class));

    connector.createEsClient(esProperties, mock());

    verify(connector, times(1)).checkHealth(any(ElasticsearchClient.class));
  }

  @Test
  void shouldUseDefaultSocketAndConnectTimeoutWhenNotConfigured() {
    // given
    final var connector = new ElasticsearchConnector(new OperateProperties());
    final var config = new ElasticsearchProperties();

    // when
    final var requestConfig =
        connector.setTimeouts(RequestConfig.custom(), config).build();

    // then
    assertThat(requestConfig.getSocketTimeout()).isEqualTo(DEFAULT_SOCKET_TIMEOUT_MS);
    assertThat(requestConfig.getConnectTimeout()).isEqualTo(DEFAULT_SOCKET_TIMEOUT_MS);
  }

  @Test
  void shouldUseConfiguredSocketAndConnectTimeoutWhenSet() {
    // given
    final var connector = new ElasticsearchConnector(new OperateProperties());
    final var config = new ElasticsearchProperties();
    config.setSocketTimeout(5_000);
    config.setConnectTimeout(3_000);

    // when
    final var requestConfig =
        connector.setTimeouts(RequestConfig.custom(), config).build();

    // then
    assertThat(requestConfig.getSocketTimeout()).isEqualTo(5_000);
    assertThat(requestConfig.getConnectTimeout()).isEqualTo(3_000);
  }
}
