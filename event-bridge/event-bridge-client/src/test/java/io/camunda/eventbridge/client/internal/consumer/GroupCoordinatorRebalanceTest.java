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

final class GroupCoordinatorRebalanceTest {

  private ScheduledExecutorService executor;
  private GroupCoordinator coordinator;

  private final List<TopicPartition> revoked = new ArrayList<>();
  private final List<TopicPartition> assigned = new ArrayList<>();

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
          public void onPartitionsRevoked(final Collection<TopicPartition> partitions) {
            revoked.addAll(partitions);
          }

          @Override
          public void onPartitionsAssigned(final Collection<TopicPartition> partitions) {
            assigned.addAll(partitions);
          }
        });
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldReportNewlyAssignedPartitionsOnFirstAssignment() {
    // when
    coordinator.applyOwnedPartitions(List.of(tp(0), tp(1)));

    // then
    assertThat(assigned).containsExactlyInAnyOrder(tp(0), tp(1));
    assertThat(revoked).isEmpty();
  }

  @Test
  void shouldReportOnlyTheDeltaWhenAssignmentChanges() {
    // given — start owning 0 and 1
    coordinator.applyOwnedPartitions(List.of(tp(0), tp(1)));
    revoked.clear();
    assigned.clear();

    // when — 0 revoked, 1 retained, 2 assigned
    coordinator.applyOwnedPartitions(List.of(tp(1), tp(2)));

    // then — only the delta is reported (retained partition 1 is not re-signalled)
    assertThat(revoked).containsExactly(tp(0));
    assertThat(assigned).containsExactly(tp(2));
  }

  private static TopicPartition tp(final int partition) {
    return new TopicPartition("t1", partition);
  }
}
