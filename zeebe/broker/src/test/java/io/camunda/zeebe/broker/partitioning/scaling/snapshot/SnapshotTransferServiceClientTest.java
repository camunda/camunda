/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class SnapshotTransferServiceClientTest {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(77);

  private final BrokerClient brokerClient = mock(BrokerClient.class);
  private SnapshotTransferServiceClient client;

  @BeforeEach
  void setUp() {
    // the response is irrelevant here, only the arguments of the request are verified
    when(brokerClient.sendRequestWithRetry(any(), any(Duration.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("expected")));
    client = new SnapshotTransferServiceClient(brokerClient, "group", REQUEST_TIMEOUT);
  }

  @Test
  void shouldUseConfiguredTimeoutWhenRequestingLatestSnapshot() {
    // given
    final var transferId = UUID.randomUUID();

    // when
    client.getLatestSnapshot(1, 10L, transferId);

    // then
    verify(brokerClient).sendRequestWithRetry(any(), eq(REQUEST_TIMEOUT));
  }

  @Test
  void shouldUseConfiguredTimeoutWhenRequestingNextChunk() {
    // given
    final var transferId = UUID.randomUUID();

    // when
    client.getNextChunk(1, "snapshot-id", "previous-chunk", transferId);

    // then
    verify(brokerClient).sendRequestWithRetry(any(), eq(REQUEST_TIMEOUT));
  }
}
