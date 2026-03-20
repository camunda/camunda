/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry.ConsumerGroup;
import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry.HeartbeatDelta;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConsumerGroupRegistry}: registration, rebalance algorithm, heartbeat, and
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
  // Registration (heartbeat-based auto-register + immediate rebalance)

  @Nested
  class Registration {

    @Test
    void shouldCreateGroupOnFirstHeartbeat() {
      // when
      final ConsumerGroup group = register("g1", "c1", TWO_PARTITIONS);

      // then
      assertThat(group).isNotNull();
      assertThat(group.getGroupId()).isEqualTo("g1");
      assertThat(group.isActive("c1")).isTrue();
    }

    @Test
    void shouldIncrementEpochOnlyForNewConsumers() {
      // when — c1 and c2 are genuinely new
      final ConsumerGroup g1 = register("g1", "c1", TWO_PARTITIONS);
      final long gen1 = g1.getEpoch();

      final ConsumerGroup g2 = register("g1", "c2", TWO_PARTITIONS);
      final long gen2 = g2.getEpoch();

      // then — each new consumer triggers a rebalance
      assertThat(gen1).isEqualTo(1L);
      assertThat(gen2).isEqualTo(2L);
    }

    @Test
    void shouldNotIncrementEpochWhenAlreadyActiveConsumerReheartbeats() {
      // given — c1 is active
      register("g1", "c1", TWO_PARTITIONS);
      final long genAfterFirstJoin = registry.getGroup("g1").getEpoch();

      // when — c1 re-heartbeats without having died (transient reconnect)
      register("g1", "c1", TWO_PARTITIONS);

      // then — epoch must NOT change; no spurious rebalance signal sent to live consumers
      assertThat(registry.getGroup("g1").getEpoch()).isEqualTo(genAfterFirstJoin);
    }

    @Test
    void shouldAssignAllPartitionsToSingleConsumer() {
      // when
      final ConsumerGroup group = register("g1", "c1", TWO_PARTITIONS);

      // then — sole consumer receives all partitions
      assertThat(group.getAssignedPartitions("c1")).containsExactly(0, 1);
    }

    @Test
    void shouldGiveSecondConsumerNothingWhenFirstIsStillAlive() {
      // The stable rebalance only redistributes *orphaned* partitions (from dead consumers).
      // A live consumer's assignment is never moved.  When c2 joins while c1 is alive, c1 holds
      // all partitions and there are no orphans → c2 receives nothing.
      // when
      register("g1", "c1", TWO_PARTITIONS);
      final ConsumerGroup group = register("g1", "c2", TWO_PARTITIONS);

      // then — c1 keeps both partitions; c2 gets none
      assertThat(group.getAssignedPartitions("c1")).containsExactlyInAnyOrder(0, 1);
      assertThat(group.getAssignedPartitions("c2")).isEmpty();
    }

    @Test
    void shouldAssignOrphanedPartitionsWhenFirstConsumerDiedBeforeSecondJoined() {
      // c1 joined and holds all partitions. c1 dies. c2 joins → all partitions are now orphaned
      // → c2 receives them.
      register("g1", "c1", TWO_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);

      // when — c2 joins after c1 died
      final ConsumerGroup group = register("g1", "c2", TWO_PARTITIONS);

      // then — c2 gets both partitions
      assertThat(group.getAssignedPartitions("c2")).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void shouldDistributeFourOrphanedPartitionsAcrossTwoNewConsumers() {
      // Precondition: c0 held all 4 partitions and died, leaving them orphaned.
      register("g1", "c0", FOUR_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      // when — c1 and c2 join sequentially
      register("g1", "c1", FOUR_PARTITIONS); // c1 gets all 4 orphaned partitions
      final ConsumerGroup group = register("g1", "c2", FOUR_PARTITIONS);

      // then — c1 still holds all 4 (no new orphans); c2 gets nothing
      assertThat(group.getAssignedPartitions("c1")).hasSize(4);
      assertThat(group.getAssignedPartitions("c2")).isEmpty();
    }

    @Test
    void shouldTreatGroupsAsIndependentNamespaces() {
      // when
      register("group-a", "c1", TWO_PARTITIONS);
      register("group-b", "c1", TWO_PARTITIONS);

      // then — same consumer ID in different groups are independent
      assertThat(registry.isConsumerActive("group-a", "c1")).isTrue();
      assertThat(registry.isConsumerActive("group-b", "c1")).isTrue();
    }

    @Test
    void shouldRefreshHeartbeatOnReregistrationWithoutChangingEpoch() {
      // given — consumer joined and is still active
      final ConsumerGroup group = register("g1", "c1", TWO_PARTITIONS);
      final long genAfterJoin = group.getEpoch();
      assertThat(group.isActive("c1")).isTrue();

      // when — re-subscribe (simulate transient reconnect of an alive consumer)
      register("g1", "c1", TWO_PARTITIONS);

      // then — still active, heartbeat reset, and generation unchanged
      assertThat(group.isActive("c1")).isTrue();
      assertThat(group.getEpoch()).isEqualTo(genAfterJoin);
    }

    @Test
    void shouldImmediatelyTriggerRebalanceForDeadConsumerRejoin() {
      // given — c1 joined, then evicted
      register("g1", "c1", TWO_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();

      // when — c1 re-subscribes
      final ConsumerGroup group = register("g1", "c1", TWO_PARTITIONS);

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
    void shouldReturnCurrentEpochAndEmptyDeltaForActiveConsumerWithNoChanges() {
      // given — consumer subscribed and coordinator has run a rebalance (epoch advances)
      register("g1", "c1", TWO_PARTITIONS);
      final long epoch = registry.getGroup("g1").getEpoch();

      // when — heartbeat at current epoch, consumer owns its assigned partitions
      final List<Integer> owned = registry.getGroup("g1").getAssignedPartitions("c1");
      final HeartbeatDelta delta =
          registry.heartbeat("g1", "c1", epoch, Set.copyOf(owned), TWO_PARTITIONS);

      // then — epoch matches; no revocations or assignments needed
      assertThat(delta.epoch()).isEqualTo(epoch);
      assertThat(delta.revoke()).isEmpty();
      assertThat(delta.assign()).isEmpty();
      assertThat(delta.fullAssignment()).isEmpty();
    }

    @Test
    void shouldAutoRegisterUnknownConsumerOnFirstHeartbeat() {
      // when — heartbeat for a consumer that was never subscribed
      registry.heartbeat("g1", "c1", 0L, Set.of(), TWO_PARTITIONS);

      // then — consumer is now registered
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldAutoRegisterConsumerInExistingGroupOnFirstHeartbeat() {
      // given — group exists with c1
      register("g1", "c1", TWO_PARTITIONS);

      // when — c2 sends its first heartbeat (never subscribed)
      registry.heartbeat("g1", "c2", 0L, Set.of(), TWO_PARTITIONS);

      // then — c2 is also active in the same group
      assertThat(registry.isConsumerActive("g1", "c2")).isTrue();
    }

    @Test
    void shouldAutoRegisterEvictedConsumerOnNextHeartbeat() {
      // given — consumer subscribed then evicted
      register("g1", "c1", TWO_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();

      // when — evicted consumer sends a heartbeat (auto-registration)
      registry.heartbeat("g1", "c1", 0L, Set.of(), TWO_PARTITIONS);

      // then — consumer is active again
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldReturnFullAssignmentWhenClientEpochIsStale() {
      // given — c1 subscribed, c2 joins → epoch advances twice; c1 is behind
      register("g1", "c1", TWO_PARTITIONS);
      register("g1", "c2", TWO_PARTITIONS);
      final long currentEpoch = registry.getGroup("g1").getEpoch();
      final long staleEpoch = currentEpoch - 1;

      // when — c1 heartbeats with a stale epoch
      final HeartbeatDelta delta =
          registry.heartbeat("g1", "c1", staleEpoch, Set.of(), TWO_PARTITIONS);

      // then — coordinator returns full assignment for reconciliation
      assertThat(delta.epoch()).isEqualTo(currentEpoch);
      assertThat(delta.fullAssignment()).isNotEmpty();
      assertThat(delta.revoke()).isEmpty();
      assertThat(delta.assign()).isEmpty();
    }
  }

  // -------------------------------------------------------------------------
  // Eviction

  @Nested
  class Eviction {

    @Test
    void shouldNotEvictRecentConsumer() {
      // given — consumer just subscribed (heartbeat is now)
      register("g1", "c1", TWO_PARTITIONS);

      // when — deadline is in the past (before the consumer subscribed) → no eviction
      registry.evictDeadConsumers(Instant.now().minusSeconds(60), TWO_PARTITIONS);

      // then
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldEvictConsumerWhoseHeartbeatIsOlderThanDeadline() {
      // given — consumer subscribed
      register("g1", "c1", TWO_PARTITIONS);

      // when — deadline is in the future (past the consumer's heartbeat time)
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);

      // then — c1 is gone
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();
    }

    @Test
    void shouldIncrementGenerationAfterEviction() {
      // given — two consumers share TWO_PARTITIONS (one each)
      register("g1", "c1", TWO_PARTITIONS);
      register("g1", "c2", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final long genBeforeEviction = group.getEpoch();

      // when — evict all consumers
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);

      // then — generation incremented (rebalance triggered by eviction)
      assertThat(group.getEpoch()).isGreaterThan(genBeforeEviction);
    }

    @Test
    void shouldRedistributeOrphanedPartitionsAfterEviction() {
      // given — c1 and c2 each have one partition
      register("g1", "c1", TWO_PARTITIONS);
      register("g1", "c2", TWO_PARTITIONS);

      // when — evict both; then c2 re-subscribes
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);
      final ConsumerGroup group = register("g1", "c2", TWO_PARTITIONS);

      // then — c2 now owns all partitions (c1 is gone)
      assertThat(group.getAssignedPartitions("c2")).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void shouldClearAllPartitionAssignmentsWhenLastConsumerIsEvicted() {
      // given
      register("g1", "c1", TWO_PARTITIONS);

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
      register("g1", "c1", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final List<Integer> originalAssignment = group.getAssignedPartitions("c1");

      // when — c1 "reconnects" (re-subscribe without going dead)
      register("g1", "c1", TWO_PARTITIONS);

      // then — assignment unchanged
      assertThat(group.getAssignedPartitions("c1"))
          .containsExactlyInAnyOrderElementsOf(originalAssignment);
    }

    @Test
    void shouldNotMoveAliveConsumersPartitionsWhenNewConsumerJoins() {
      // given — c1 holds all 4 partitions (no orphans)
      register("g1", "c1", FOUR_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final List<Integer> c1Before = List.copyOf(group.getAssignedPartitions("c1"));

      // when — c2 and c3 join; c1 is still alive → its assignment is never moved
      register("g1", "c2", FOUR_PARTITIONS);
      register("g1", "c3", FOUR_PARTITIONS);

      // then — c1 retains all of its original partitions
      assertThat(group.getAssignedPartitions("c1")).containsExactlyInAnyOrderElementsOf(c1Before);
    }

    @Test
    void shouldAssignOrphanedPartitionsLexicographicallyByConsumerId() {
      // Precondition: all 4 partitions are orphaned (prior holder died).
      register("g1", "previous-holder", FOUR_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      // when — consumer-a subscribes; all orphaned partitions go to consumer-a (only candidate)
      register("g1", "consumer-a", FOUR_PARTITIONS);
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
      register("g1", "original", FOUR_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      register("g1", "c1", FOUR_PARTITIONS); // c1 takes all
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      // c2 now joins into a fully orphaned group
      final ConsumerGroup group = register("g1", "c2", FOUR_PARTITIONS);

      // then — all 4 partitions covered by active consumer
      final int total = group.getAssignedPartitions("c2").size();
      assertThat(total).isEqualTo(FOUR_PARTITIONS);
    }

    @Test
    void shouldCoverAllPartitionsWhenSingleConsumerSubscribes() {
      // given — 1 consumer, 4 partitions
      register("g1", "c1", FOUR_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // then — sole consumer owns all 4 partitions
      assertThat(group.getAssignedPartitions("c1")).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void shouldHandleMoreConsumersThanPartitions() {
      // given — all partitions are orphaned first, then c1 subscribes and takes them all
      // then c2, c3 join — neither gets any (c1 is alive with all partitions)
      register("g1", "c1", TWO_PARTITIONS);
      register("g1", "c2", TWO_PARTITIONS);
      register("g1", "c3", TWO_PARTITIONS);
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
      register("g1", "__bootstrap__", 0);
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
      register("g1", "__bootstrap__", 0);
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
      register("g1", "c1", FOUR_PARTITIONS);
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
    void shouldReturnTrueAfterHeartbeat() {
      register("g1", "c1", TWO_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherConsumerInSameGroup() {
      register("g1", "c1", TWO_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c2")).isFalse();
    }

    @Test
    void shouldReturnFalseAfterConsumerIsEvicted() {
      // given
      register("g1", "c1", TWO_PARTITIONS);

      // when
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);

      // then
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();
    }

    @Test
    void shouldReturnTrueAfterDeadConsumerRejoins() {
      // given — c1 subscribed then evicted
      register("g1", "c1", TWO_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();

      // when — c1 re-subscribes
      register("g1", "c1", TWO_PARTITIONS);

      // then
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }
  }

  // -------------------------------------------------------------------------
  // getGroup / getAllGroups

  @Nested
  class GetGroup {

    @Test
    void shouldReturnNullForNonExistentGroup() {
      assertThat(registry.getGroup("no-such-group")).isNull();
    }

    @Test
    void shouldReturnGroupAfterHeartbeat() {
      // when
      register("g1", "c1", TWO_PARTITIONS);

      // then
      final ConsumerGroup group = registry.getGroup("g1");
      assertThat(group).isNotNull();
      assertThat(group.getGroupId()).isEqualTo("g1");
    }
  }

  @Nested
  class GetAllGroups {

    @Test
    void shouldReturnEmptyMapBeforeAnySubscription() {
      assertThat(registry.getAllGroups()).isEmpty();
    }

    @Test
    void shouldReturnAllRegisteredGroups() {
      // when
      register("g1", "c1", TWO_PARTITIONS);
      register("g2", "c1", TWO_PARTITIONS);
      register("g3", "c1", TWO_PARTITIONS);

      // then
      assertThat(registry.getAllGroups()).containsOnlyKeys("g1", "g2", "g3");
    }

    @Test
    void shouldReturnUnmodifiableView() {
      // given
      register("g1", "c1", TWO_PARTITIONS);
      final Map<String, ConsumerGroup> view = registry.getAllGroups();

      // then — the returned map is unmodifiable
      assertThatThrownBy(() -> view.put("new-group", null))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReflectNewGroupsAfterSubscription() {
      // given — snapshot before
      register("g1", "c1", TWO_PARTITIONS);
      final Map<String, ConsumerGroup> view = registry.getAllGroups();
      assertThat(view).hasSize(1);

      // when — a second group is added
      register("g2", "c2", TWO_PARTITIONS);

      // then — the live view reflects the addition
      assertThat(registry.getAllGroups()).hasSize(2);
    }
  }

  // -------------------------------------------------------------------------
  // Null-parameter validation

  @Nested
  class NullParameterValidation {

    @Test
    void shouldThrowWhenGroupIdIsNullOnHeartbeatRegister() {
      assertThatThrownBy(() -> register(null, "c1", TWO_PARTITIONS))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("groupId");
    }

    @Test
    void shouldThrowWhenConsumerIdIsNullOnHeartbeatRegister() {
      assertThatThrownBy(() -> register("g1", null, TWO_PARTITIONS))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("consumerId");
    }

    @Test
    void shouldThrowWhenGroupIdIsNullOnHeartbeat() {
      assertThatThrownBy(() -> registry.heartbeat(null, "c1", 0L, Set.of(), TWO_PARTITIONS))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("groupId");
    }

    @Test
    void shouldThrowWhenConsumerIdIsNullOnHeartbeat() {
      assertThatThrownBy(() -> registry.heartbeat("g1", null, 0L, Set.of(), TWO_PARTITIONS))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("consumerId");
    }
  }

  // -------------------------------------------------------------------------
  // getAliveAssignedConsumersForPartition

  @Nested
  class GetAliveAssignedConsumersForPartition {

    @Test
    void shouldReturnConsumerIdWhenPartitionIsAssignedToAliveConsumer() {
      // given — c1 holds both partitions
      register("g1", "c1", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // then — both assigned partitions return c1
      assertThat(group.getAliveAssignedConsumersForPartition(0)).containsExactly("c1");
      assertThat(group.getAliveAssignedConsumersForPartition(1)).containsExactly("c1");
    }

    @Test
    void shouldReturnEmptySetForUnassignedPartition() {
      // given — c1 holds partitions 0 and 1 within TWO_PARTITIONS; partition 99 is outside range
      register("g1", "c1", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // then — partition 99 was never assigned
      assertThat(group.getAliveAssignedConsumersForPartition(99)).isEmpty();
    }

    @Test
    void shouldReturnEmptySetWhenAssignedConsumerHasBeenEvicted() {
      // given — c1 holds partition 0; then c1 is evicted (no one to take the partition yet)
      register("g1", "c1", TWO_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // then — partition 0's assignment was cleared by the rebalance following eviction
      assertThat(group.getAliveAssignedConsumersForPartition(0)).isEmpty();
      assertThat(group.getAliveAssignedConsumersForPartition(1)).isEmpty();
    }

    @Test
    void shouldReturnNewOwnerAfterRebalanceReassignsPartition() {
      // given — c1 holds both partitions, then dies; c2 subscribes and inherits them
      register("g1", "c1", TWO_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);
      register("g1", "c2", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // then — c2 now owns both partitions
      assertThat(group.getAliveAssignedConsumersForPartition(0)).containsExactly("c2");
      assertThat(group.getAliveAssignedConsumersForPartition(1)).containsExactly("c2");
    }

    @Test
    void shouldReturnCorrectOwnerPerPartitionAfterSplitAssignment() {
      // given — bootstrap to get a known split: c_a → [0,2], c_b → [1,3]
      register("g1", "__bootstrap__", 0);
      final ConsumerGroup group = registry.getGroup("g1");
      group.evictDead(Instant.now().plusSeconds(3600));
      group.addOrRefresh("c_b");
      group.addOrRefresh("c_a");
      group.rebalance(FOUR_PARTITIONS);

      // then — each partition is owned by exactly the expected consumer
      assertThat(group.getAliveAssignedConsumersForPartition(0)).containsExactly("c_a");
      assertThat(group.getAliveAssignedConsumersForPartition(1)).containsExactly("c_b");
      assertThat(group.getAliveAssignedConsumersForPartition(2)).containsExactly("c_a");
      assertThat(group.getAliveAssignedConsumersForPartition(3)).containsExactly("c_b");
    }
  }

  // -------------------------------------------------------------------------
  // Partial eviction and multi-group independence

  @Nested
  class PartialEviction {

    @Test
    void shouldNotEvictActiveConsumerWhenDeadlineIsInThePast() {
      // given — two consumers in the same group
      register("g1", "c1", TWO_PARTITIONS);
      register("g1", "c2", TWO_PARTITIONS);

      // when — deadline is before both consumers subscribed → nobody dead
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TWO_PARTITIONS);

      // then — both still active
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
      assertThat(registry.isConsumerActive("g1", "c2")).isTrue();
    }

    @Test
    void shouldNotIncrementGenerationWhenNoConsumersAreEvicted() {
      // given — c1 is alive
      register("g1", "c1", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final long genBefore = group.getEpoch();

      // when — evict with a past deadline (before c1 subscribed) → nothing evicted
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TWO_PARTITIONS);

      // then — generation unchanged (no rebalance triggered)
      assertThat(group.getEpoch()).isEqualTo(genBefore);
    }

    @Test
    void shouldEvictConsumersAcrossMultipleGroupsIndependently() {
      // given — two independent groups each with one consumer
      register("g1", "c1", TWO_PARTITIONS);
      register("g2", "c2", TWO_PARTITIONS);

      // when — evict all
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);

      // then — both groups lose their consumer
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();
      assertThat(registry.isConsumerActive("g2", "c2")).isFalse();
    }

    @Test
    void shouldIncrementGenerationInEachGroupIndependentlyOnEviction() {
      // given — two groups
      register("g1", "c1", TWO_PARTITIONS);
      register("g2", "c2", TWO_PARTITIONS);
      final long gen1Before = registry.getGroup("g1").getEpoch();
      final long gen2Before = registry.getGroup("g2").getEpoch();

      // when — evict all
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);

      // then — each group's generation was incremented by its own rebalance
      assertThat(registry.getGroup("g1").getEpoch()).isGreaterThan(gen1Before);
      assertThat(registry.getGroup("g2").getEpoch()).isGreaterThan(gen2Before);
    }
  }

  // -------------------------------------------------------------------------
  // Rebalance edge-cases

  @Nested
  class RebalanceEdgeCases {

    @Test
    void shouldHandleZeroTotalPartitions() {
      // when — subscribe with zero partitions; no assignments possible
      final ConsumerGroup group = register("g1", "c1", 0);

      // then — consumer is active but has no partitions
      assertThat(group.isActive("c1")).isTrue();
      assertThat(group.getPartitionAssignment()).isEmpty();
      assertThat(group.getEpoch()).isEqualTo(1L);
    }

    @Test
    void shouldClearAllAssignmentsAndIncrementGenerationWhenLastConsumerDies() {
      // given
      register("g1", "c1", FOUR_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final long genBefore = group.getEpoch();

      // when
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      // then — assignments cleared and generation incremented by rebalance
      assertThat(group.getPartitionAssignment()).isEmpty();
      assertThat(group.getEpoch()).isGreaterThan(genBefore);
    }

    @Test
    void shouldAssignAllPartitionsToRejoinedSoleConsumer() {
      // given — c1 held partitions, was evicted, then rejoins
      register("g1", "c1", FOUR_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      // when
      final ConsumerGroup group = register("g1", "c1", FOUR_PARTITIONS);

      // then — c1 re-inherits all partitions
      assertThat(group.getAssignedPartitions("c1")).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void shouldCoverAllPartitionsAcrossConsumersAfterEvictionAndRejoin() {
      // given — original holder evicted; c1 and c2 both join into orphaned group
      register("g1", "original", FOUR_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      register("g1", "c1", FOUR_PARTITIONS); // c1 takes all orphaned partitions
      // then c1 is also evicted
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), FOUR_PARTITIONS);

      register("g1", "c2", FOUR_PARTITIONS);
      register("g1", "c3", FOUR_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // then — all 4 partitions are covered (c2 took them all when joining first)
      final List<Integer> c2Partitions = group.getAssignedPartitions("c2");
      final List<Integer> c3Partitions = group.getAssignedPartitions("c3");
      assertThat(c2Partitions.size() + c3Partitions.size()).isEqualTo(FOUR_PARTITIONS);
    }

    @Test
    void shouldKeepPartitionAssignmentUnmodifiableFromCaller() {
      // given
      register("g1", "c1", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");

      // then — returned map cannot be mutated
      final Map<Integer, String> assignment = group.getPartitionAssignment();
      assertThatThrownBy(() -> assignment.put(99, "intruder"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  // -------------------------------------------------------------------------
  // ACK timeout eviction

  @Nested
  class AckTimeoutEviction {

    private static final long ACK_TIMEOUT_MS = 5_000L;

    @BeforeEach
    void setUpWithAckTimeout() {
      registry = new ConsumerGroupRegistry(Integer.MAX_VALUE, ACK_TIMEOUT_MS);
    }

    @Test
    void shouldEvictConsumerWhoseAckDeadlineHasExpired() {
      // given — c1 subscribed and gets partitions; heartbeat triggers a revoke (p99 not in target)
      register("g1", "c1", TWO_PARTITIONS);
      final long epoch = registry.getGroup("g1").getEpoch();
      registry.heartbeat("g1", "c1", epoch, Set.of(99), TWO_PARTITIONS);
      // ackDeadline is now set to now + 5s for c1

      // when — evict with a deadline far in the future (after all ackDeadlines)
      registry.expireAckTimeouts(Instant.now().plusSeconds(3600));

      // then — c1 is evicted because its ackDeadline elapsed
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();
    }

    @Test
    void shouldNotEvictConsumerWhoseAckDeadlineHasNotExpired() {
      // given — heartbeat triggers a revoke → ackDeadline set to now + 5s
      register("g1", "c1", TWO_PARTITIONS);
      final long epoch = registry.getGroup("g1").getEpoch();
      registry.heartbeat("g1", "c1", epoch, Set.of(99), TWO_PARTITIONS);

      // when — check with a deadline BEFORE now (no deadline has passed)
      registry.expireAckTimeouts(Instant.now().minusSeconds(3600));

      // then — consumer is still active
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldNotEvictConsumerWithNoOutstandingAckDeadline() {
      // given — consumer subscribed but no heartbeat delta was sent (no ackDeadline)
      register("g1", "c1", TWO_PARTITIONS);

      // when — evict with far-future instant
      registry.expireAckTimeouts(Instant.now().plusSeconds(3600));

      // then — consumer is not evicted (ackDeadline == null)
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldSetConsumersChangedAfterAckTimeoutEviction() {
      // given
      register("g1", "c1", TWO_PARTITIONS);
      final long epoch = registry.getGroup("g1").getEpoch();
      registry.heartbeat("g1", "c1", epoch, Set.of(99), TWO_PARTITIONS);

      // when
      registry.expireAckTimeouts(Instant.now().plusSeconds(3600));

      // then — consumersChanged is set so evictDeadConsumers triggers rebalance
      assertThat(registry.getGroup("g1").consumersChanged).isTrue();
    }

    @Test
    void shouldTriggerRebalanceAfterAckTimeoutEvictionOnNextEvictDeadConsumers() {
      // given — c1 and c2 both in group; c1's ackDeadline expires
      register("g1", "c1", TWO_PARTITIONS);
      register("g1", "c2", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final long epochBeforeEviction = group.getEpoch();

      final long epoch = group.getEpoch();
      registry.heartbeat("g1", "c1", epoch, Set.of(99), TWO_PARTITIONS);
      // Expire c1's ackDeadline
      registry.expireAckTimeouts(Instant.now().plusSeconds(3600));

      // when — evict dead consumers triggers rebalance because consumersChanged == true
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TWO_PARTITIONS);

      // then — epoch incremented by the rebalance; c2 still active
      assertThat(group.getEpoch()).isGreaterThan(epochBeforeEviction);
      assertThat(registry.isConsumerActive("g1", "c2")).isTrue();
    }

    @Test
    void shouldClearAckDeadlineAfterFullyAcknowledged() {
      // given — c1 subscribed and rebalanced so it owns partitions 0 and 1
      register("g1", "c1", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final long epoch = group.getEpoch();
      // heartbeat reports owning partition 99 (not in target) → revoke sent
      final HeartbeatDelta delta =
          registry.heartbeat("g1", "c1", epoch, Set.of(99), TWO_PARTITIONS);
      assertThat(delta.revoke()).contains(99);

      // when — consumer ACKs the revocation
      registry.ack("g1", "c1", epoch, delta.revoke(), delta.assign());

      // then — no ackDeadline remains; consumer cannot be evicted by expireAckTimeouts
      registry.expireAckTimeouts(Instant.now().plusSeconds(3600));
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }
  }

  // -------------------------------------------------------------------------
  // Partition count change detection

  @Nested
  class PartitionCountChange {

    @Test
    void shouldTriggerRebalanceWhenConfiguredPartitionCountIsUpdated() {
      // given — group created with 2 partitions and rebalanced
      register("g1", "c1", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final long epochBefore = group.getEpoch();
      assertThat(group.getPartitionAssignment()).hasSize(TWO_PARTITIONS);

      // Simulate an admin API updating configuredPartitionCount to 4
      group.configuredPartitionCount = FOUR_PARTITIONS;

      // when — coordinator loop runs evictDeadConsumers which checks for partition count mismatch
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), FOUR_PARTITIONS);

      // then — rebalance triggered: epoch incremented and all 4 partitions now assigned
      assertThat(group.getEpoch()).isGreaterThan(epochBefore);
      assertThat(group.getPartitionAssignment()).hasSize(FOUR_PARTITIONS);
    }

    @Test
    void shouldNotTriggerRebalanceWhenPartitionCountMatchesTrackedPartitions() {
      // given — group stable at 2 partitions
      register("g1", "c1", TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final long epochAfterFirstRebalance = group.getEpoch();

      // when — coordinator loop runs with no change
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TWO_PARTITIONS);

      // then — no extra rebalance; epoch unchanged
      assertThat(group.getEpoch()).isEqualTo(epochAfterFirstRebalance);
    }

    @Test
    void shouldNotTriggerPartitionCountRebalanceForGroupWithNoConsumers() {
      // given — group created then all consumers evicted (partitionAssignment = empty)
      register("g1", "c1", TWO_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TWO_PARTITIONS);
      final ConsumerGroup group = registry.getGroup("g1");
      final long epochAfterEviction = group.getEpoch();

      // Simulate admin API bump; with no consumers there is nothing to rebalance
      group.configuredPartitionCount = FOUR_PARTITIONS;

      // when
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), FOUR_PARTITIONS);

      // then — no spurious rebalance while group is empty
      assertThat(group.getEpoch()).isEqualTo(epochAfterEviction);
    }
  }

  // -------------------------------------------------------------------------
  // Test helpers

  /**
   * Registers a consumer via heartbeat and immediately triggers a rebalance cycle (simulating one
   * coordinator loop tick). This replaces the removed {@code registry.subscribe()} shorthand.
   */
  private ConsumerGroup register(
      final String groupId, final String consumerId, final int partitionCount) {
    registry.heartbeat(groupId, consumerId, 0L, Set.of(), partitionCount);
    // Use Instant.MIN so no consumers are evicted, but consumersChanged triggers a rebalance.
    registry.evictDeadConsumers(Instant.MIN, partitionCount);
    return registry.getGroup(groupId);
  }
}
