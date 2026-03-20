/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry;
import io.camunda.eventbridge.broker.offset.OffsetStore;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CoordinatorActor#getTruncationBoundary(int)} and the actor-dispatch path for
 * {@link CoordinatorActor#commitOffset}.
 */
class CoordinatorActorTruncationTest {

  private ActorScheduler scheduler;
  private ConsumerGroupRegistry registry;
  private OffsetStore offsetStore;
  private CoordinatorActor actor;

  // 2 partitions; all default config values
  private static final int TOTAL_PARTITIONS = 2;
  private static final EventBridgeProperties PROPERTIES =
      new EventBridgeProperties(null, null, null, null, null, null, null, null);

  @BeforeEach
  void setUp() {
    scheduler =
        ActorScheduler.newActorScheduler()
            .setSchedulerName("test-scheduler")
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .build();
    scheduler.start();

    registry = new ConsumerGroupRegistry();
    offsetStore = new OffsetStore();
    actor = new CoordinatorActor(registry, offsetStore, PROPERTIES, TOTAL_PARTITIONS);
    scheduler.submitActor(actor).join();
  }

  @AfterEach
  void tearDown() throws Exception {
    actor.closeAsync().join();
    scheduler.close();
  }

  @Nested
  class GetTruncationBoundary {

    @Test
    void shouldReturnMaxValueWhenNoConsumersAreSubscribed() {
      // given — no subscriptions, no commits

      // when
      final long boundary = actor.getTruncationBoundary(0).join();

      // then
      assertThat(boundary).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shouldReturnMaxValueWhenSubscribedConsumerHasNotCommitted() {
      // given
      register("g1", "c1");

      // when — no commit yet
      final long boundary = actor.getTruncationBoundary(0).join();

      // then
      assertThat(boundary).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shouldReturnCommittedPositionForSingleConsumer() {
      // given
      register("g1", "c1");
      actor.commitOffset("g1", "c1", 0, 100L).join();

      // when
      final long boundary = actor.getTruncationBoundary(0).join();

      // then
      assertThat(boundary).isEqualTo(100L);
    }

    @Test
    void shouldReturnMinAcrossTwoGroupsOnSamePartition() {
      // given — two separate groups, each with one consumer, both assigned to partition 0
      // With 2 partitions and 1 consumer per group the rebalance assigns partition 0 to that
      // consumer. Subscribe with different groups to guarantee distinct assignment entries.
      register("group-a", "consumer-a");
      register("group-b", "consumer-b");

      actor.commitOffset("group-a", "consumer-a", 0, 300L).join();
      actor.commitOffset("group-b", "consumer-b", 0, 100L).join();

      // when
      final long boundary = actor.getTruncationBoundary(0).join();

      // then — min(300, 100) = 100
      assertThat(boundary).isEqualTo(100L);
    }

    @Test
    void shouldExcludeDeadConsumerFromBoundaryCalculation() {
      // given — two consumers subscribed to the same partition 0; both commit; one is evicted
      register("g1", "c1");

      // Force eviction: register c1, then subscribe c2 which triggers a rebalance.
      // To test dead-consumer exclusion we commit c1's offset before evicting it, then verify
      // that after eviction the boundary is driven by the remaining alive consumer only.
      actor.commitOffset("g1", "c1", 0, 10L).join();

      // Evict c1 by setting its deadline in the past via registry directly (actor thread-safe via
      // the registry's own guard), simulating a heartbeat timeout.
      // We use the registry directly here because eviction is normally triggered internally by the
      // actor timer; calling it directly lets us test the truncation path without time travel.
      registry.evictDeadConsumers(java.time.Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // Subscribe a new consumer — triggers rebalance, c1 is gone, c2 inherits partition 0.
      register("g1", "c2");
      actor.commitOffset("g1", "c2", 0, 500L).join();

      // when
      final long boundary = actor.getTruncationBoundary(0).join();

      // then — c1 is dead; only c2 (position 500) is alive and assigned; boundary = 500
      // NOT 10 (c1's stale offset must not pin the low-watermark)
      assertThat(boundary).isEqualTo(500L);
    }
  }

  @Nested
  class CommitOffset {

    @Test
    void shouldRejectCommitFromUnregisteredConsumer() {
      // given — consumer never subscribed

      // when / then — join() wraps the actor-thread exception in ExecutionException
      assertThatThrownBy(() -> actor.commitOffset("g1", "unknown", 0, 42L).join())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(CoordinatorActor.ConsumerNotRegisteredException.class);
    }

    @Test
    void shouldAcceptIdempotentCommitAtSamePosition() {
      // given
      register("g1", "c1");
      actor.commitOffset("g1", "c1", 0, 50L).join();

      // when — commit same position again
      actor.commitOffset("g1", "c1", 0, 50L).join();

      // then — offset unchanged, no exception
      assertThat(offsetStore.getCommittedOffset("g1", "c1", 0)).isEqualTo(50L);
    }
  }

  // -------------------------------------------------------------------------
  // Test helpers

  private void register(final String groupId, final String consumerId) {
    actor.heartbeat(groupId, consumerId, 0L, java.util.List.of()).join();
    registry.evictDeadConsumers(java.time.Instant.MIN, TOTAL_PARTITIONS);
  }
}
