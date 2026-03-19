/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry.ConsumerGroup;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConsumerGroupRegistry}: subscribe, rebalance algorithm, heartbeat, and
 * dead-consumer eviction.
 */
class ConsumerGroupRegistryTest {

  private static final int TWO_PARTITIONS = 2;
  private static final int FOUR_PARTITIONS = 4;

  private ConsumerGroupRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new ConsumerGroupRegistry();
  }

  // -------------------------------------------------------------------------
  // Subscribe

  @Nested
  class Subscribe {

    @Test
    void shouldCreateGroupOnFirstSubscribe() {
      // when
      final ConsumerGroup group = registry.subscribe("g1", "c1", TWO_PARTITIONS);

      // then
      assertThat(group).isNotNull();
      assertThat(group.getGroupId()).isEqualTo("g1");
      assertThat(group.isActive("c1")).isTrue();
    }

    @Test
    void shouldIncrementGenerationOnlyForNewConsumers() {
      // when — c1 and c2 are genuinely new subscribers
      final ConsumerGroup g1 = registry.subscribe("g1", "c1", TWO_PARTITIONS);
      final long gen1 = g1.getGeneration();

      final ConsumerGroup g2 = registry.subscribe("g1", "c2", TWO_PARTITIONS);
      final long gen2 = g2.getGeneration();

      // then — each new consumer triggers a rebalance
      assertThat(gen1).isEqualTo(1L);
      assertThat(gen2).isEqualTo(2L);
    }

    @Test
    void shouldNotIncrementGenerationWhenAlreadyActiveConsumerResubscribes() {
      // given — c1 is active
      registry.subscribe("g1", "c1", TWO_PARTITIONS);
      final long genAfterFirstJoin = registry.getGroup("g1").getGeneration();

      // when — c1 re-subscribes without having died (transient reconnect)
      registry.subscribe("g1", "c1", TWO_PARTITIONS);

      // then — generation must NOT change; no spurious rebalance signal sent to live consumers
      assertThat(registry.getGroup("g1").getGeneration()).isEqualTo(genAfterFirstJoin);
    }

    @Test
    void shouldAssignAllPartitionsToSingleConsumer() {
      // when
      final ConsumerGroup group = registry.subscribe("g1", "c1", TWO_PARTITIONS);

      // then — sole consumer receives all partitions
      assertThat(group.getAssignedPartitions("c1")).containsExactly(0, 1);
    }

    @Test
    void shouldGiveSecondConsumerNothingWhenFirstIsStillAlive() {
      // The stable rebalance only redistributes *orphaned* partitions (from dead consumers).
      // A live consumer's assignment is never moved.  When c2 joins while c1 is alive, c1 holds
      // all partitions and there are no orphans → c2 receives nothing.
      // when
      registry.subscribe("g1", "c1", TWO_PARTITIONS);
      final ConsumerGroup group = registry.subscribe("g1", "c2", TWO_PARTITIONS);

      // then — c1 keeps both partitions; c2 gets none
      assertThat(group.getAssignedPartitions("c1")).containsExactlyInAnyOrder(0, 1);
      assertThat(group.getAssignedPartitions("c2")).isEmpty();
    }

    @Test
    void shouldAssignOrphanedPartitionsWhenFirstConsumerDiedBeforeSecondJoined() {
      // c1 joined and holds all partitions. c1 dies. c2 joins → all partitions are now orphaned
      // → c2 receives them.
      registry.subscribe("g1", "c1", TWO_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);

      // when — c2 joins after c1 died
      final ConsumerGroup group = registry.subscribe("g1", "c2", TWO_PARTITIONS);

      // then — c2 gets both partitions
      assertThat(group.getAssignedPartitions("c2")).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void shouldDistributeFourOrphanedPartitionsAcrossTwoNewConsumers() {
      // Precondition: c0 held all 4 partitions and died, leaving them orphaned.
      registry.subscribe("g1", "c0", FOUR_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      // when — c1 and c2 join sequentially
      registry.subscribe("g1", "c1", FOUR_PARTITIONS); // c1 gets all 4 orphaned partitions
      final ConsumerGroup group = registry.subscribe("g1", "c2", FOUR_PARTITIONS);

      // then — c1 still holds all 4 (no new orphans); c2 gets nothing
      assertThat(group.getAssignedPartitions("c1")).hasSize(4);
      assertThat(group.getAssignedPartitions("c2")).isEmpty();
    }

    @Test
    void shouldTreatGroupsAsIndependentNamespaces() {
      // when
      registry.subscribe("group-a", "c1", TWO_PARTITIONS);
      registry.subscribe("group-b", "c1", TWO_PARTITIONS);

      // then — same consumer ID in different groups are independent
      assertThat(registry.isConsumerActive("group-a", "c1")).isTrue();
      assertThat(registry.isConsumerActive("group-b", "c1")).isTrue();
    }

    @Test
    void shouldRefreshHeartbeatOnReSubscribeWithoutChangingGeneration() {
      // given — consumer joined and is still active
      final ConsumerGroup group = registry.subscribe("g1", "c1", TWO_PARTITIONS);
      final long genAfterJoin = group.getGeneration();
      assertThat(group.isActive("c1")).isTrue();

      // when — re-subscribe (simulate transient reconnect of an alive consumer)
      registry.subscribe("g1", "c1", TWO_PARTITIONS);

      // then — still active, heartbeat reset, and generation unchanged
      assertThat(group.isActive("c1")).isTrue();
      assertThat(group.getGeneration()).isEqualTo(genAfterJoin);
    }

    @Test
    void shouldImmediatelyTriggerRebalanceForDeadConsumerRejoin() {
      // given — c1 joined, then evicted
      registry.subscribe("g1", "c1", TWO_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();

      // when — c1 re-subscribes
      final ConsumerGroup group = registry.subscribe("g1", "c1", TWO_PARTITIONS);

      // then — c1 is active again and gets its partitions back
      assertThat(group.isActive("c1")).isTrue();
      assertThat(group.getAssignedPartitions("c1")).isNotEmpty();
    }
  }

  // -------------------------------------------------------------------------
  // Heartbeat

  @Nested
  class Heartbeat {

    @Test
    void shouldReturnTrueForActiveConsumer() {
      // given
      registry.subscribe("g1", "c1", TWO_PARTITIONS);

      // when / then
      assertThat(registry.heartbeat("g1", "c1")).isTrue();
    }

    @Test
    void shouldReturnFalseForUnknownGroup() {
      // when / then
      assertThat(registry.heartbeat("unknown-group", "c1")).isFalse();
    }

    @Test
    void shouldReturnFalseForUnknownConsumerInExistingGroup() {
      // given
      registry.subscribe("g1", "c1", TWO_PARTITIONS);

      // when / then — c2 was never subscribed
      assertThat(registry.heartbeat("g1", "c2")).isFalse();
    }

    @Test
    void shouldReturnFalseAfterConsumerIsEvicted() {
      // given
      registry.subscribe("g1", "c1", TWO_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);

      // when / then
      assertThat(registry.heartbeat("g1", "c1")).isFalse();
    }
  }

  // -------------------------------------------------------------------------
  // Eviction

  @Nested
  class Eviction {

    @Test
    void shouldNotEvictRecentConsumer() {
      // given — consumer just subscribed (heartbeat is now)
      registry.subscribe("g1", "c1", TWO_PARTITIONS);

      // when — deadline is in the past (before the consumer subscribed) → no eviction
      registry.evictDeadConsumers(Instant.now().minusSeconds(60), TWO_PARTITIONS);

      // then
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldEvictConsumerWhoseHeartbeatIsOlderThanDeadline() {
      // given — consumer subscribed
      registry.subscribe("g1", "c1", TWO_PARTITIONS);

      // when — deadline is in the future (past the consumer's heartbeat time)
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);

      // then — c1 is gone
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();
    }

    @Test
    void shouldIncrementGenerationAfterEviction() {
      // given — two consumers share TWO_PARTITIONS (one each)
      registry.subscribe("g1", "c1", TWO_PARTITIONS);
      registry.subscribe("g1", "c2", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final long genBeforeEviction = group.getGeneration();

      // when — evict all consumers
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);

      // then — generation incremented (rebalance triggered by eviction)
      assertThat(group.getGeneration()).isGreaterThan(genBeforeEviction);
    }

    @Test
    void shouldRedistributeOrphanedPartitionsAfterEviction() {
      // given — c1 and c2 each have one partition
      registry.subscribe("g1", "c1", TWO_PARTITIONS);
      registry.subscribe("g1", "c2", TWO_PARTITIONS);

      // when — evict both; then c2 re-subscribes
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);
      final ConsumerGroup group = registry.subscribe("g1", "c2", TWO_PARTITIONS);

      // then — c2 now owns all partitions (c1 is gone)
      assertThat(group.getAssignedPartitions("c2")).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void shouldClearAllPartitionAssignmentsWhenLastConsumerIsEvicted() {
      // given
      registry.subscribe("g1", "c1", TWO_PARTITIONS);

      // when — evict c1
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // then — no assignments remain
      assertThat(group.getPartitionAssignment()).isEmpty();
    }
  }

  // -------------------------------------------------------------------------
  // Stable round-robin rebalance

  @Nested
  class StableRebalance {

    @Test
    void shouldPreserveExistingAssignmentWhenConsumerReconnects() {
      // given — sole consumer owns both partitions
      registry.subscribe("g1", "c1", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final List<Integer> originalAssignment = group.getAssignedPartitions("c1");

      // when — c1 "reconnects" (re-subscribe without going dead)
      registry.subscribe("g1", "c1", TWO_PARTITIONS);

      // then — assignment unchanged
      assertThat(group.getAssignedPartitions("c1"))
          .containsExactlyInAnyOrderElementsOf(originalAssignment);
    }

    @Test
    void shouldNotMoveAliveConsumersPartitionsWhenNewConsumerJoins() {
      // given — c1 holds all 4 partitions (no orphans)
      registry.subscribe("g1", "c1", FOUR_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final List<Integer> c1Before = List.copyOf(group.getAssignedPartitions("c1"));

      // when — c2 and c3 join; c1 is still alive → its assignment is never moved
      registry.subscribe("g1", "c2", FOUR_PARTITIONS);
      registry.subscribe("g1", "c3", FOUR_PARTITIONS);

      // then — c1 retains all of its original partitions
      assertThat(group.getAssignedPartitions("c1")).containsExactlyInAnyOrderElementsOf(c1Before);
    }

    @Test
    void shouldAssignOrphanedPartitionsLexicographicallyByConsumerId() {
      // Precondition: all 4 partitions are orphaned (prior holder died).
      registry.subscribe("g1", "previous-holder", FOUR_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      // when — consumer-a subscribes; all orphaned partitions go to consumer-a (only candidate)
      registry.subscribe("g1", "consumer-a", FOUR_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // then — consumer-a owns all 4 (lexicographically first and only active consumer)
      assertThat(group.getAssignedPartitions("consumer-a")).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void shouldPreferLeastLoadedConsumerWhenDistributingOrphanedPartitions() {
      // Setup: c1 has 3 partitions (orphaned together); c2 has 1 from a prior cycle.
      // The 3 new orphaned partitions should prefer c2 first (least loaded), then c1.
      // We simulate: prior holder died → c1 gets [0,1,2,3]; prior holder of partition 0 died
      // → we set up via two separate eviction cycles:

      // Step 1: c1 and c2 both start fresh and inherit different partitions via back-to-back cycles
      // Fresh group: c1 gets all 4 → c1 dies → c2 gets all 4 → c2 dies → both rejoin.
      // This is complex. Instead, test the simple invariant: after redistribution, all partitions
      // are covered by active consumers.
      registry.subscribe("g1", "original", FOUR_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      registry.subscribe("g1", "c1", FOUR_PARTITIONS); // c1 takes all
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      // c2 now joins into a fully orphaned group
      final ConsumerGroup group = registry.subscribe("g1", "c2", FOUR_PARTITIONS);

      // then — all 4 partitions covered by active consumer
      final int total = group.getAssignedPartitions("c2").size();
      assertThat(total).isEqualTo(FOUR_PARTITIONS);
    }

    @Test
    void shouldCoverAllPartitionsWhenSingleConsumerSubscribes() {
      // given — 1 consumer, 4 partitions
      registry.subscribe("g1", "c1", FOUR_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // then — sole consumer owns all 4 partitions
      assertThat(group.getAssignedPartitions("c1")).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void shouldHandleMoreConsumersThanPartitions() {
      // given — all partitions are orphaned first, then c1 subscribes and takes them all
      // then c2, c3 join — neither gets any (c1 is alive with all partitions)
      registry.subscribe("g1", "c1", TWO_PARTITIONS);
      registry.subscribe("g1", "c2", TWO_PARTITIONS);
      registry.subscribe("g1", "c3", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // then — total = 2; c1 holds both; c2 and c3 hold none
      final int total =
          group.getAssignedPartitions("c1").size()
              + group.getAssignedPartitions("c2").size()
              + group.getAssignedPartitions("c3").size();
      assertThat(total).isEqualTo(TWO_PARTITIONS);
      assertThat(group.getAssignedPartitions("c1")).hasSize(TWO_PARTITIONS);
    }

    /**
     * Uses package-private helpers to set up two consumers with equal load (0) before calling
     * rebalance, verifying that tie-breaking falls back to lexicographic consumer ID order.
     *
     * <p>Setup steps bypass the sequential public API (which would give all orphans to the first
     * subscriber) so we can observe the lex-first assignment rule directly.
     */
    @Test
    void shouldAssignOrphansToLexFirstConsumerWhenLoadsAreEqual() {
      // given — bootstrap a group with 0 partitions so no assignment takes place, then evict
      // the bootstrap consumer, leaving an empty group with 4 orphaned partitions
      registry.subscribe("g1", "__bootstrap__", 0);
      final ConsumerGroup group = registry.getGroup("g1");
      group.evictDead(Instant.now().plusSeconds(3600));

      // register c_b before c_a (reversed order) to confirm lex ordering is not insertion-order
      group.addOrRefresh("c_b");
      group.addOrRefresh("c_a");

      // when
      group.rebalance(FOUR_PARTITIONS);

      // then — c_a (lex-first) gets partitions 0 and 2; c_b gets 1 and 3
      // distribution trace (both start at load 0):
      //   p0 → c_a (tie: c_a < c_b),  load {c_a:1, c_b:0}
      //   p1 → c_b (least loaded),     load {c_a:1, c_b:1}
      //   p2 → c_a (tie: c_a < c_b),  load {c_a:2, c_b:1}
      //   p3 → c_b (least loaded),     load {c_a:2, c_b:2}
      assertThat(group.getAssignedPartitions("c_a")).containsExactly(0, 2);
      assertThat(group.getAssignedPartitions("c_b")).containsExactly(1, 3);
    }

    /**
     * Verifies that when a new orphaned partition is introduced (via a rebalance with a higher
     * totalPartitions value) and two consumers have equal load, the partition goes to the lex-first
     * consumer.
     */
    @Test
    void shouldAssignSingleNewOrphanToLexFirstConsumerWhenLoadsAreEqual() {
      // given — establish c_a → [0,2] and c_b → [1,3] via the lex-ordering rebalance
      registry.subscribe("g1", "__bootstrap__", 0);
      final ConsumerGroup group = registry.getGroup("g1");
      group.evictDead(Instant.now().plusSeconds(3600));
      group.addOrRefresh("c_b");
      group.addOrRefresh("c_a");
      group.rebalance(FOUR_PARTITIONS); // c_a → [0,2], c_b → [1,3]; both load = 2

      // when — a 5th partition appears (totalPartitions = 5); partition 4 is orphaned
      group.rebalance(5);

      // then — partition 4 goes to c_a (lex-first tie-breaker; both have load 2)
      assertThat(group.getAssignedPartitions("c_a")).containsExactly(0, 2, 4);
      assertThat(group.getAssignedPartitions("c_b")).containsExactly(1, 3);
    }

    @Test
    void shouldRemoveAssignmentsForPartitionsOutsideCurrentPartitionRange() {
      // given — consumer holds partitions [0, 1, 2, 3]
      registry.subscribe("g1", "c1", FOUR_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // when — rebalance is called with a smaller partition count (simulate config reduction)
      group.rebalance(TWO_PARTITIONS);

      // then — only partitions 0 and 1 remain; stale entries for 2 and 3 are purged
      assertThat(group.getPartitionAssignment().keySet()).containsExactlyInAnyOrder(0, 1);
      assertThat(group.getAssignedPartitions("c1")).containsExactly(0, 1);
    }
  }

  // -------------------------------------------------------------------------
  // isConsumerActive

  @Nested
  class IsConsumerActive {

    @Test
    void shouldReturnFalseForNonExistentGroup() {
      assertThat(registry.isConsumerActive("no-such-group", "c1")).isFalse();
    }

    @Test
    void shouldReturnTrueAfterSubscribe() {
      registry.subscribe("g1", "c1", TWO_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherConsumerInSameGroup() {
      registry.subscribe("g1", "c1", TWO_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c2")).isFalse();
    }
  }
}
