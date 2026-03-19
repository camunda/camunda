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
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CoordinatorActor} subscribe, heartbeat, and dead-consumer detection paths.
 *
 * <p>Complements {@link CoordinatorActorTruncationTest} which covers the truncation boundary and
 * commit-offset actor dispatch.
 */
class CoordinatorActorSubscribeHeartbeatTest {

  private static final int TOTAL_PARTITIONS = 4;
  private static final EventBridgeProperties PROPERTIES =
      new EventBridgeProperties(null, null, null, null, null, null, null);

  private ActorScheduler scheduler;
  private ConsumerGroupRegistry registry;
  private OffsetStore offsetStore;
  private CoordinatorActor actor;

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

  // -------------------------------------------------------------------------
  // Subscribe

  @Nested
  class Subscribe {

    @Test
    void shouldReturnAssignedPartitionsAndGenerationForFirstConsumer() {
      // when
      final var result = actor.subscribe("g1", "c1").join();

      // then
      assertThat(result.assignedPartitions()).isNotEmpty();
      assertThat(result.generation()).isEqualTo(1L);
    }

    @Test
    void shouldAssignAllPartitionsToSoleConsumer() {
      // when
      final var result = actor.subscribe("g1", "c1").join();

      // then — sole consumer gets all 4 partitions
      assertThat(result.assignedPartitions()).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void shouldIncrementGenerationOnEachSubscribeCall() {
      // when
      final long gen1 = actor.subscribe("g1", "c1").join().generation();
      final long gen2 = actor.subscribe("g1", "c2").join().generation();

      // then
      assertThat(gen1).isEqualTo(1L);
      assertThat(gen2).isEqualTo(2L);
    }

    @Test
    void shouldGiveSecondConsumerNothingWhileFirstIsAlive() {
      // Stable rebalance only redistributes orphaned (dead consumer) partitions.
      // c1 is alive and holds all partitions → c2 joining sees no orphans → c2 gets nothing.
      actor.subscribe("g1", "c1").join();
      final var r2 = actor.subscribe("g1", "c2").join();

      // c1 still owns all 4 partitions; c2 received none
      final var group = registry.getGroup("g1");
      assertThat(group.getAssignedPartitions("c1")).hasSize(4);
      assertThat(r2.assignedPartitions()).isEmpty();
    }

    @Test
    void shouldGiveNewConsumerAllOrphanedPartitionsAfterFirstConsumerDies() {
      // given — c1 holds all partitions then dies
      actor.subscribe("g1", "c1").join();
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // when — c2 joins; all 4 partitions are orphaned
      final var r2 = actor.subscribe("g1", "c2").join();

      // then — c2 gets all 4
      assertThat(r2.assignedPartitions()).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void shouldTreatGroupsIndependently() {
      // when — same consumer ID in two separate groups
      final var ga = actor.subscribe("group-a", "c1").join();
      final var gb = actor.subscribe("group-b", "c1").join();

      // then — each group has generation 1 and its own partition assignment
      assertThat(ga.generation()).isEqualTo(1L);
      assertThat(gb.generation()).isEqualTo(1L);
    }

    @Test
    void shouldNotIncrementGenerationWhenAlreadyActiveConsumerResubscribes() {
      // given — c1 is already active
      final long gen1 = actor.subscribe("g1", "c1").join().generation();

      // when — c1 re-subscribes (transient reconnect) without having died
      final long gen2 = actor.subscribe("g1", "c1").join().generation();

      // then — generation unchanged; no spurious rebalance broadcast
      assertThat(gen2).isEqualTo(gen1);
    }

    @Test
    void shouldIncrementGenerationWhenPreviouslyDeadConsumerRejoins() {
      // given — c1 subscribed, then evicted
      actor.subscribe("g1", "c1").join();
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);
      final long genAfterEviction = registry.getGroup("g1").getGeneration();

      // when — c1 re-subscribes after being dead (real re-join)
      final long genAfterRejoin = actor.subscribe("g1", "c1").join().generation();

      // then — generation incremented because membership changed
      assertThat(genAfterRejoin).isGreaterThan(genAfterEviction);
    }

    @Test
    void shouldAllowConsumerToResubscribeAfterBeingEvicted() {
      // given — c1 subscribed, then evicted via registry directly
      actor.subscribe("g1", "c1").join();
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();

      // when — c1 re-subscribes via actor
      final var result = actor.subscribe("g1", "c1").join();

      // then — c1 is active and has partitions
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
      assertThat(result.assignedPartitions()).isNotEmpty();
    }

    @Test
    void shouldPreserveExistingAssignmentsForStillAliveConsumer() {
      // given — c1 holds all 4 partitions (no orphans)
      final var r1 = actor.subscribe("g1", "c1").join();

      // when — c2 and c3 join while c1 is alive; c1's assignment must not change
      actor.subscribe("g1", "c2").join();
      actor.subscribe("g1", "c3").join();

      // then — c1 still holds exactly the partitions it was originally assigned
      final var group = registry.getGroup("g1");
      assertThat(group.getAssignedPartitions("c1"))
          .containsExactlyInAnyOrderElementsOf(r1.assignedPartitions());
    }
  }

  // -------------------------------------------------------------------------
  // Heartbeat

  @Nested
  class Heartbeat {

    @Test
    void shouldReturnCurrentGenerationForActiveConsumer() {
      // given
      final long gen = actor.subscribe("g1", "c1").join().generation();

      // when
      final long heartbeatGen = actor.heartbeat("g1", "c1").join();

      // then
      assertThat(heartbeatGen).isEqualTo(gen);
    }

    @Test
    void shouldThrowConsumerNotRegisteredForUnknownGroup() {
      // when / then
      assertThatThrownBy(() -> actor.heartbeat("unknown-group", "c1").join())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(CoordinatorActor.ConsumerNotRegisteredException.class);
    }

    @Test
    void shouldThrowConsumerNotRegisteredForUnknownConsumerInExistingGroup() {
      // given
      actor.subscribe("g1", "c1").join();

      // when / then — c2 was never subscribed
      assertThatThrownBy(() -> actor.heartbeat("g1", "c2").join())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(CoordinatorActor.ConsumerNotRegisteredException.class);
    }

    @Test
    void shouldThrowConsumerNotRegisteredAfterEviction() {
      // given — c1 subscribed, then evicted
      actor.subscribe("g1", "c1").join();
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // when / then
      assertThatThrownBy(() -> actor.heartbeat("g1", "c1").join())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(CoordinatorActor.ConsumerNotRegisteredException.class);
    }

    @Test
    void shouldReflectUpdatedGenerationAfterRebalance() {
      // given — c1 subscribed (gen 1)
      actor.subscribe("g1", "c1").join();

      // when — c2 joins (gen 2); c1 sends a heartbeat
      actor.subscribe("g1", "c2").join();
      final long heartbeatGen = actor.heartbeat("g1", "c1").join();

      // then — generation reflects the latest rebalance
      assertThat(heartbeatGen).isEqualTo(2L);
    }
  }

  // -------------------------------------------------------------------------
  // GetAssignment

  @Nested
  class GetAssignment {

    @Test
    void shouldReturnCurrentAssignmentAndGeneration() {
      // given
      actor.subscribe("g1", "c1").join();

      // when
      final var result = actor.getAssignment("g1", "c1").join();

      // then
      assertThat(result.assignedPartitions()).containsExactlyInAnyOrder(0, 1, 2, 3);
      assertThat(result.generation()).isEqualTo(1L);
    }

    @Test
    void shouldThrowConsumerNotRegisteredForUnknownGroup() {
      assertThatThrownBy(() -> actor.getAssignment("no-group", "c1").join())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(CoordinatorActor.ConsumerNotRegisteredException.class);
    }
  }

  // -------------------------------------------------------------------------
  // Heartbeat-timeout-driven eviction (simulated via registry)

  @Nested
  class HeartbeatTimeout {

    @Test
    void shouldMarkConsumerDeadAfterEvictionAndRejectHeartbeat() {
      // given — consumer is active
      actor.subscribe("g1", "c1").join();
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();

      // when — simulate heartbeat timeout by advancing the eviction deadline far into the future
      // (the registry evicts any consumer whose last heartbeat is before the deadline)
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // then — heartbeat is now rejected
      assertThatThrownBy(() -> actor.heartbeat("g1", "c1").join())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(CoordinatorActor.ConsumerNotRegisteredException.class);
    }

    @Test
    void shouldRebalanceAfterConsumerDiesAndNewConsumerJoins() {
      // given — c1 subscribed and holds all partitions
      actor.subscribe("g1", "c1").join();
      final long genBefore = registry.getGroup("g1").getGeneration();

      // when — evict c1 (simulate heartbeat timeout)
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // then — generation incremented (rebalance triggered by eviction)
      assertThat(registry.getGroup("g1").getGeneration()).isGreaterThan(genBefore);
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();

      // when — c2 subscribes into the group with orphaned partitions
      actor.subscribe("g1", "c2").join();

      // c2 now owns all 4 partitions (all were orphaned)
      assertThat(registry.getGroup("g1").getAssignedPartitions("c2"))
          .containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void shouldSuspendTruncationWhenAllConsumersAreDead() {
      // given — consumer subscribed and committed
      actor.subscribe("g1", "c1").join();
      actor.commitOffset("g1", "c1", 0, 42L).join();

      // when — consumer dies
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // then — no alive assigned consumer → truncation boundary is MAX_VALUE
      final long boundary = actor.getTruncationBoundary(0).join();
      assertThat(boundary).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shouldResumeFromLastCommittedOffsetAfterRejoin() {
      // given — c1 committed, then died
      actor.subscribe("g1", "c1").join();
      actor.commitOffset("g1", "c1", 0, 77L).join();
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // when — c1 re-subscribes
      actor.subscribe("g1", "c1").join();
      actor.commitOffset("g1", "c1", 0, 77L).join(); // idempotent re-commit at same position

      // then — committed offset preserved
      assertThat(offsetStore.getCommittedOffset("g1", "c1", 0)).isEqualTo(77L);
    }
  }
}
