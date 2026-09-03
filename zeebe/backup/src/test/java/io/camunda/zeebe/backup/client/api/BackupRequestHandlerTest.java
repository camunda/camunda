/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.client.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BackupRequestHandlerTest {

  private static final String PHYSICAL_TENANT_ID = "tenant";

  @Mock private BrokerClient brokerClient;
  @Mock private BrokerTopologyManager topologyManager;
  @Mock private BrokerClusterState clusterState;

  private BackupRequestHandler handler;

  @BeforeEach
  void setUp() {
    when(brokerClient.getTopologyManager()).thenReturn(topologyManager);
    when(topologyManager.getTopology(PHYSICAL_TENANT_ID)).thenReturn(clusterState);
    when(clusterState.getPartitionsCount()).thenReturn(1);
    when(clusterState.getPartitions()).thenReturn(List.of(1));
    handler = new BackupRequestHandler(brokerClient);
  }

  @Test
  void shouldUseBackupRequestTimeout() {
    // given
    final var response = new BrokerResponse<>(new BackupListResponse(List.of()));
    when(brokerClient.sendRequestWithRetry(any(BackupListRequest.class), any(Duration.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    // when
    handler.listBackups(PHYSICAL_TENANT_ID, "").toCompletableFuture().join();

    // then
    final var timeoutCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(brokerClient)
        .sendRequestWithRetry(any(BackupListRequest.class), timeoutCaptor.capture());
    assertThat(timeoutCaptor.getValue()).isEqualTo(Duration.ofSeconds(60));
  }
}
