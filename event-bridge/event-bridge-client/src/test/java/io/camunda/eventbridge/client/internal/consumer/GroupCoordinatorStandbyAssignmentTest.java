/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.client.OffsetResetPolicy;
import io.camunda.eventbridge.client.RebalanceListener;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * {@link GroupCoordinator#applyStandbyAssignment} — the client-side seam for a member's standby
 * target (event-bridge-streaming ADR 0009 decision 6 / consumer-groups ADR 0006 decision 1), diffed
 * into {@link RebalanceListener#onStandbyPartitionsAssigned}/{@link
 * RebalanceListener#onStandbyPartitionsRevoked} deltas the same way {@link
 * GroupCoordinatorRebalanceTest} exercises active ownership. See the method's javadoc for why a
 * real heartbeat cannot reach it yet.
 */
final class GroupCoordinatorStandbyAssignmentTest {

  private ScheduledExecutorService executor;
  private GroupCoordinator coordinator;

  private final List<TopicPartition> standbyRevoked = new ArrayList<>();
  private final List<TopicPartition> standbyAssigned = new ArrayList<>();

  @BeforeEach
  void setUp() {
    final HttpTransport transport = Mockito.mock(HttpTransport.class);
    executor = Executors.newSingleThreadScheduledExecutor();
    final SubscriptionState subscription =
        new SubscriptionState(
            (topic, partition, offset, maxBytes, minBytes, maxWaitMs) ->
                CompletableFuture.completedFuture(null),
            OffsetResetPolicy.EARLIEST);
    final PrefetchBuffer buffer = new PrefetchBuffer(1);
    final Prefetcher prefetcher =
        new Prefetcher(
            (topic, partition, offset, maxBytes, minBytes, maxWaitMs) ->
                CompletableFuture.completedFuture(null),
            executor,
            subscription,
            buffer,
            () -> false,
            1 << 20,
            0,
            5_000L);
    coordinator =
        new GroupCoordinator(
            transport,
            executor,
            subscription,
            buffer,
            prefetcher,
            () -> false,
            "g1",
            List.of("t1"),
            "c1",
            () -> {},
            3_000L);
    coordinator.setRebalanceListener(
        new RebalanceListener() {
          @Override
          public void onStandbyPartitionsRevoked(final Collection<TopicPartition> partitions) {
            standbyRevoked.addAll(partitions);
          }

          @Override
          public void onStandbyPartitionsAssigned(final Collection<TopicPartition> partitions) {
            standbyAssigned.addAll(partitions);
          }
        });
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldReportNewlyAssignedStandbyPartitionsOnFirstTarget() {
    // when
    coordinator.applyStandbyAssignment(List.of(tp(0), tp(1)));

    // then
    assertThat(standbyAssigned).containsExactlyInAnyOrder(tp(0), tp(1));
    assertThat(standbyRevoked).isEmpty();
  }

  @Test
  void shouldReportOnlyTheDeltaWhenTheStandbyTargetChanges() {
    // given — the standby target starts as {0, 1}
    coordinator.applyStandbyAssignment(List.of(tp(0), tp(1)));
    standbyAssigned.clear();
    standbyRevoked.clear();

    // when — 0 drops out, 1 is retained, 2 joins
    coordinator.applyStandbyAssignment(List.of(tp(1), tp(2)));

    // then — only the delta is reported (retained partition 1 is not re-signalled)
    assertThat(standbyRevoked).containsExactly(tp(0));
    assertThat(standbyAssigned).containsExactly(tp(2));
  }

  @Test
  void shouldNotAffectActivePartitionNotifications() {
    // given — a standby target
    coordinator.applyStandbyAssignment(List.of(tp(0)));

    // when — active ownership changes independently
    coordinator.applyOwnedPartitions(List.of(tp(5)));

    // then — the standby target is untouched by an active-ownership change
    coordinator.applyStandbyAssignment(List.of(tp(0)));
    assertThat(standbyRevoked).isEmpty();
    assertThat(standbyAssigned).containsExactly(tp(0));
  }

  private static TopicPartition tp(final int partition) {
    return new TopicPartition("t1", partition);
  }
}
