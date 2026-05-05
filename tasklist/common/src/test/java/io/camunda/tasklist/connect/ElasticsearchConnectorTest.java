/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.connect;

import static io.camunda.search.connect.configuration.ConnectConfiguration.DEFAULT_SOCKET_TIMEOUT_MS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.property.ElasticsearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import org.apache.http.client.config.RequestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ElasticsearchConnectorTest {

  @Test
  void shouldUseDefaultSocketAndConnectTimeoutWhenNotConfigured() {
    // given
    final var connector = Mockito.spy(new ElasticsearchConnector());
    connector.setTasklistProperties(new TasklistProperties());
    final var config = new ElasticsearchProperties();

    // when
    final var requestConfig = connector.setTimeouts(RequestConfig.custom(), config).build();

    // then
    assertThat(requestConfig.getSocketTimeout()).isEqualTo(DEFAULT_SOCKET_TIMEOUT_MS);
    assertThat(requestConfig.getConnectTimeout()).isEqualTo(DEFAULT_SOCKET_TIMEOUT_MS);
  }

  @Test
  void shouldUseConfiguredSocketAndConnectTimeoutWhenSet() {
    // given
    final var connector = Mockito.spy(new ElasticsearchConnector());
    connector.setTasklistProperties(new TasklistProperties());
    final var config = new ElasticsearchProperties();
    config.setSocketTimeout(5_000);
    config.setConnectTimeout(3_000);

    // when
    final var requestConfig = connector.setTimeouts(RequestConfig.custom(), config).build();

    // then
    assertThat(requestConfig.getSocketTimeout()).isEqualTo(5_000);
    assertThat(requestConfig.getConnectTimeout()).isEqualTo(3_000);
  }
}


