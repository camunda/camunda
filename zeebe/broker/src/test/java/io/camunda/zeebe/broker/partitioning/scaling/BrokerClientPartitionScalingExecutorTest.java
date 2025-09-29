/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerResponseConsumer;
import io.camunda.zeebe.broker.client.api.BrokerResponseException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse.DeleteSnapshotForBootstrapResponse;
import io.camunda.zeebe.broker.transport.snapshotapi.SnapshotBrokerRequest;
import io.camunda.zeebe.gateway.impl.broker.request.scaling.GetScaleUpProgress;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BrokerClientPartitionScalingExecutorTest {

  static final List<Set<Integer>> INCOMPLETE_PARTITION_LIST =
      List.of(Set.of(1, 2, 3, 4), Set.of(4));

  static final List<Set<Integer>> COMPLETE_PARTITION_LIST =
      List.of(Set.of(1, 2, 3, 4, 5), Set.of(4, 5));

  TestConcurrencyControl concurrencyControl = new TestConcurrencyControl();
  BrokerClientPartitionScalingExecutor executor;
  @Mock BrokerClient brokerClient;

  @BeforeEach
  void setup() {
    executor = new BrokerClientPartitionScalingExecutor(brokerClient, concurrencyControl);
  }

  @Test
  public void shouldFailFutureWhenInvalidDesiredPartitionCount() {
    final var scaleRecord = new ScaleRecord().setDesiredPartitionCount(2);
    assertThat(awaitRedistributionWith(Either.right(scaleRecord)))
        .completesExceptionallyWithin(Duration.ZERO)
        .withThrowableThat()
        .withMessageContaining(
            "expected to await redistribution completion for partition count 5, but got 2");
  }

  @ParameterizedTest
  @FieldSource("INCOMPLETE_PARTITION_LIST")
  public void shouldFailFutureResponseWhenNotAllPartitionsAreReady(final Set<Integer> partitions) {
    final var scaleRecord =
        new ScaleRecord().setDesiredPartitionCount(5).setRedistributedPartitions(partitions);

    // then
    assertThat(awaitRedistributionWith(Either.right(scaleRecord)))
        .completesExceptionallyWithin(Duration.ZERO)
        .withThrowableThat()
        .withMessageContaining(
            "Redistribution not completed yet: waiting for these partitions: [5]");
  }

  @ParameterizedTest
  @FieldSource("COMPLETE_PARTITION_LIST")
  public void shouldCompleteResponseWhenAllPartitionsAreReady(final Set<Integer> partitions) {
    final var scaleRecord =
        new ScaleRecord().setDesiredPartitionCount(5).setRedistributedPartitions(partitions);

    // then
    // when all partitions are ready all partitions receive a request
    assertThat(awaitRedistributionWith(Either.right(scaleRecord), 5, true)).isCompleted();
  }

  @Test
  public void shoulFailFutureWhenBrokerReturnsError() {
    assertThat(awaitRedistributionWith(Either.left(new BrokerResponseException("expected"))))
        .completesExceptionallyWithin(Duration.ZERO)
        .withThrowableThat()
        .withCause(new BrokerResponseException("expected"));
  }

  private CompletableFuture<Void> awaitRedistributionWith(
      final Either<Throwable, ScaleRecord> scaleRecord) {
    return awaitRedistributionWith(scaleRecord, 1, false);
  }

  @SuppressWarnings("unchecked")
  private CompletableFuture<Void> awaitRedistributionWith(
      final Either<Throwable, ScaleRecord> scaleRecord,
      final int expectedRequests,
      final boolean expectSnapshotRequestSent) {
    // given
    doNothing().when(brokerClient).sendRequestWithRetry(any(), any(), any());
    final var responseConsumerCaptor = ArgumentCaptor.forClass(BrokerResponseConsumer.class);
    final var requestCaptor = ArgumentCaptor.forClass(BrokerRequest.class);
    final var throwableCaptor = ArgumentCaptor.forClass(Consumer.class);

    // when
    final var future =
        executor
            .awaitRedistributionCompletion(5, Set.of(4, 5), Duration.ofSeconds(5))
            .toCompletableFuture();
    // first request is sent to partition 1
    for (int i = Protocol.DEPLOYMENT_PARTITION; i <= expectedRequests; i++) {
      verify(brokerClient)
          .sendRequestWithRetry(
              requestCaptor.capture(), responseConsumerCaptor.capture(), throwableCaptor.capture());
      final var request = requestCaptor.getValue();
      assertThat(request).isInstanceOf(GetScaleUpProgress.class);
      final var getScaleUpProgress = (GetScaleUpProgress) request;
      assertThat(request.getPartitionId()).isEqualTo(i);

      clearInvocations(brokerClient);
      scaleRecord.ifRightOrLeft(
          record -> responseConsumerCaptor.getValue().accept(1L, record),
          ex -> throwableCaptor.getValue().accept(ex));
    }

    if (expectSnapshotRequestSent) {
      verify(brokerClient, timeout(5000))
          .sendRequestWithRetry(
              requestCaptor.capture(), responseConsumerCaptor.capture(), throwableCaptor.capture());

      assertThat(requestCaptor.getValue()).isInstanceOf(SnapshotBrokerRequest.class);
      final var snapshotRequest = (SnapshotBrokerRequest) requestCaptor.getValue();
      assertThat(snapshotRequest.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
      assertThat(snapshotRequest.getRequest().partitionId())
          .isEqualTo(Protocol.DEPLOYMENT_PARTITION);

      responseConsumerCaptor.getValue().accept(1L, new DeleteSnapshotForBootstrapResponse(1));
    }
    return future;
  }
}
