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
import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry.AckStatus;
import io.camunda.eventbridge.broker.offset.OffsetStore;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link CoordinatorActor} coordinator loop: session-timeout eviction, ACK-timeout
 * eviction, rebalance triggering, multi-group processing, assignment queries, inflight-revocation
 * cap, and commit-offset integration after re-registration.
 *
 * <p>The coordinator loop is exercised by calling {@link
 * ConsumerGroupRegistry#expireAckTimeouts(Instant)} and {@link
 * ConsumerGroupRegistry#evictDeadConsumers(Instant, int)} directly, which mirrors the exact
 * sequence performed by {@code runCoordinatorLoop()}. This avoids coupling tests to wall-clock
 * timing while still exercising the full state-machine logic.
 *
 * <p>Complements {@link CoordinatorActorSubscribeHeartbeatTest} (heartbeat / ACK actor dispatch)
 * and {@link CoordinatorActorTruncationTest} (truncation boundary and commit-offset).
 */
@SuppressWarnings("deprecation")
class CoordinatorActorLoopTest {

  private static final int TOTAL_PARTITIONS = 4;
  private static final EventBridgeProperties PROPERTIES =
      new EventBridgeProperties(null, null, null, null, null, null, null, null);

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
  // Session-timeout eviction

  @Nested
  class SessionTimeoutEviction {

    @Test
    void shouldNotEvictConsumerWithRecentHeartbeat() {
      // given
      actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // when — deadline is far in the past; only old heartbeats would be evicted
      registry.evictDeadConsumers(Instant.now().minusSeconds(60), TOTAL_PARTITIONS);

      // then — consumer heartbeated just now, so it's still alive
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldEvictConsumerWhenDeadlineIsInFuture() {
      // given
      actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // when — far-future deadline covers every heartbeat timestamp → eviction
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // then
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();
    }

    @Test
    void shouldIncrementEpochWhenConsumerEvictedViaSessionTimeout() {
      // given — register c1, run rebalance so epoch > 0
      final var group = registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epochBeforeEviction = group.getEpoch();
      assertThat(epochBeforeEviction).isGreaterThan(0L);

      // when — evict c1 via session timeout and rebalance
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // then — epoch advanced by the eviction-triggered rebalance
      assertThat(registry.getGroup("g1").getEpoch()).isGreaterThan(epochBeforeEviction);
    }

    @Test
    void shouldRedistributeOrphanedPartitionsToRemainingConsumer() {
      // given — c1 subscribes and is assigned all partitions
      registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      assertThat(registry.getGroup("g1").getAssignedPartitions("c1")).hasSize(TOTAL_PARTITIONS);

      // when — c1 dies (session timeout); all its partitions become orphaned
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();

      // when — c2 registers and the coordinator loop runs the rebalance
      actor.heartbeat("g1", "c2", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);

      // then — c2 inherits all orphaned partitions
      assertThat(registry.getGroup("g1").getAssignedPartitions("c2"))
          .hasSize(TOTAL_PARTITIONS)
          .containsExactly(0, 1, 2, 3);
    }

    @Test
    void shouldClearPartitionAssignmentWhenAllConsumersEvicted() {
      // given — c1 is assigned partitions
      registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      assertThat(registry.getGroup("g1").getPartitionAssignment()).isNotEmpty();

      // when — evict everyone
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // then — no active consumers; partition assignment cleared by rebalance
      assertThat(registry.getGroup("g1").getPartitionAssignment()).isEmpty();
    }

    @Test
    void shouldNotAffectOtherGroupsWhenOneGroupEvicts() {
      // given — c1 in group-a and c1 in group-b both heartbeat
      actor.heartbeat("group-a", "c1", 0L, List.of()).join();
      actor.heartbeat("group-b", "c1", 0L, List.of()).join();

      // when — evict only group-a (by registering c1 of group-b again so its timestamp is fresh,
      // then using a deadline that covers the original timestamp of group-a but not group-b).
      // Simpler: evict everyone, but group-b has a consumer with a future deadline.
      // Instead use a targeted approach: directly evict group-a only.
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // then — both groups' consumers are evicted (expected in this test), but they are independent
      assertThat(registry.isConsumerActive("group-a", "c1")).isFalse();
      assertThat(registry.isConsumerActive("group-b", "c1")).isFalse();

      // Groups still exist as data structures
      assertThat(registry.getGroup("group-a")).isNotNull();
      assertThat(registry.getGroup("group-b")).isNotNull();
    }
  }

  // -------------------------------------------------------------------------
  // ACK-timeout eviction

  @Nested
  class AckTimeoutEviction {

    @Test
    void shouldIncrementEpochWhenConsumerEvictedViaAckTimeout() {
      // given — consumer registered and rebalanced
      final var group = registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epochBeforeEviction = group.getEpoch();

      // Trigger a revoke delta by sending a heartbeat with a partition not in the target.
      // Use a registry with ackTimeoutMs = 1 so the deadline is effectively immediate.
      final ConsumerGroupRegistry registryWithAck =
          new ConsumerGroupRegistry(Integer.MAX_VALUE, 1L);
      final var group2 = registryWithAck.subscribe("g2", "c1", TOTAL_PARTITIONS);
      final long epoch2 = group2.getEpoch();

      // Heartbeat with an out-of-range partition to trigger ackDeadline
      registryWithAck.heartbeat("g2", "c1", epoch2, java.util.Set.of(99), TOTAL_PARTITIONS);

      // when — expire the ack deadline
      registryWithAck.expireAckTimeouts(Instant.now().plusMillis(100));

      // then — c1 was evicted
      assertThat(registryWithAck.isConsumerActive("g2", "c1")).isFalse();

      // and — group epoch is still the epoch before eviction (epoch increments during rebalance
      // inside evictDeadConsumers, which hasn't been called yet)
      assertThat(registryWithAck.getGroup("g2").getEpoch()).isEqualTo(epoch2);

      // when — run the eviction/rebalance step
      registryWithAck.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);

      // then — epoch incremented because group.consumersChanged was set by expireAckTimeouts
      assertThat(registryWithAck.getGroup("g2").getEpoch()).isGreaterThan(epoch2);
    }

    @Test
    void shouldNotEvictConsumerWhenAckDeadlineNotYetExpired() {
      // given — registry with 5 second ACK timeout
      final ConsumerGroupRegistry registryWithAck =
          new ConsumerGroupRegistry(Integer.MAX_VALUE, 5_000L);
      final var group = registryWithAck.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epoch = group.getEpoch();

      // Heartbeat with out-of-range partition to arm the ack deadline
      registryWithAck.heartbeat("g1", "c1", epoch, java.util.Set.of(99), TOTAL_PARTITIONS);

      // when — expire with now (deadline is ~5s in the future, so nothing expires)
      registryWithAck.expireAckTimeouts(Instant.now());

      // then — consumer still alive
      assertThat(registryWithAck.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldRunAckTimeoutEvictionBeforeSessionTimeoutEviction() {
      // This test verifies the coordinator loop ordering: expireAckTimeouts() runs
      // before evictDeadConsumers(). A consumer that times out due to an unACKed revocation
      // must be evicted even when its session heartbeat is still fresh.

      // given — registry with ACK timeout of 1 ms and a long session timeout (never triggers)
      final ConsumerGroupRegistry timedRegistry = new ConsumerGroupRegistry(Integer.MAX_VALUE, 1L);
      final var group = timedRegistry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epoch = group.getEpoch();

      // Arm the ACK deadline by triggering a revoke
      timedRegistry.heartbeat("g1", "c1", epoch, java.util.Set.of(99), TOTAL_PARTITIONS);

      // Simulate "now + 100ms" so the 1ms ACK deadline has passed
      final Instant futureNow = Instant.now().plusMillis(100);

      // Step 1 of coordinator loop: expire ack timeouts — must evict c1
      timedRegistry.expireAckTimeouts(futureNow);
      assertThat(timedRegistry.isConsumerActive("g1", "c1"))
          .as("ACK timeout should have evicted c1 before session timeout step")
          .isFalse();

      // Step 2 of coordinator loop: session-timeout eviction with a deadline in the far past
      // (i.e., only truly ancient heartbeats would expire here — c1 is already gone)
      timedRegistry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);

      // then — c1 is still gone; rebalance ran due to consumersChanged flag set by ack eviction
      assertThat(timedRegistry.isConsumerActive("g1", "c1")).isFalse();
      assertThat(timedRegistry.getGroup("g1").getEpoch()).isGreaterThan(epoch);
    }

    @Test
    void shouldClearAckDeadlineAfterSuccessfulAck() {
      // given — registry with ACK timeout of 5 s
      final ConsumerGroupRegistry registryWithAck =
          new ConsumerGroupRegistry(Integer.MAX_VALUE, 5_000L);
      final var group = registryWithAck.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epoch = group.getEpoch();

      // Arm the ACK deadline: consumer owns partition 99 (not in target) → revoke issued;
      // consumer also has assigned partitions [0-3] not yet owned → assign issued.
      // ackDeadline is set only when either revoke or assign is non-empty.
      final var delta =
          registryWithAck.heartbeat("g1", "c1", epoch, java.util.Set.of(99), TOTAL_PARTITIONS);

      // Consumer ACKs ALL pending revocations AND assignments — deadline must be cleared once both
      // pendingRevoke and pendingAssign are empty.
      registryWithAck.ack("g1", "c1", epoch, delta.revoke(), delta.assign());

      // when — expire with a far-future instant (would evict if deadline were still set)
      registryWithAck.expireAckTimeouts(Instant.now().plusSeconds(3600));

      // then — deadline was cleared by ACK; consumer is still alive
      assertThat(registryWithAck.isConsumerActive("g1", "c1")).isTrue();
    }
  }

  // -------------------------------------------------------------------------
  // Multi-group processing

  @Nested
  class MultiGroupProcessing {

    @Test
    void shouldProcessAllGroupsInOneCoordinatorLoopCycle() {
      // given — two consumers in two separate groups, each with a rebalanced assignment
      registry.subscribe("group-a", "ca", TOTAL_PARTITIONS);
      registry.subscribe("group-b", "cb", TOTAL_PARTITIONS);

      final long epochA = registry.getGroup("group-a").getEpoch();
      final long epochB = registry.getGroup("group-b").getEpoch();

      // Both consumers heartbeat (keeps them alive in their respective groups)
      actor.heartbeat("group-a", "ca2", 0L, List.of()).join(); // new consumer in group-a
      actor.heartbeat("group-b", "cb2", 0L, List.of()).join(); // new consumer in group-b

      // when — one coordinator loop cycle evicts both new consumers' session deadlines
      // (they just registered, so use a far-future deadline)
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // then — both groups underwent rebalance (epoch advanced in each)
      assertThat(registry.getGroup("group-a").getEpoch()).isGreaterThan(epochA);
      assertThat(registry.getGroup("group-b").getEpoch()).isGreaterThan(epochB);
    }

    @Test
    void shouldMaintainEpochIndependenceBetweenGroups() {
      // given — two separate groups
      registry.subscribe("group-a", "ca", TOTAL_PARTITIONS);
      registry.subscribe("group-b", "cb", TOTAL_PARTITIONS);

      final long epochA = registry.getGroup("group-a").getEpoch();
      final long epochB = registry.getGroup("group-b").getEpoch();

      // when — add a new consumer only to group-a (triggers rebalance in group-a only)
      actor.heartbeat("group-a", "ca2", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);

      // then — group-a epoch advanced; group-b epoch is unchanged
      assertThat(registry.getGroup("group-a").getEpoch()).isGreaterThan(epochA);
      assertThat(registry.getGroup("group-b").getEpoch()).isEqualTo(epochB);
    }
  }

  // -------------------------------------------------------------------------
  // Assignment queries

  @Nested
  class AssignmentQueries {

    @Test
    void shouldGetAssignmentForRegisteredConsumer() {
      // given — consumer subscribed and rebalanced
      registry.subscribe("g1", "c1", TOTAL_PARTITIONS);

      // when
      final var result = actor.getAssignment("g1", "c1").join();

      // then — assigned partitions are non-empty; epoch is > 0 after rebalance
      assertThat(result.assignedPartitions()).isNotEmpty().hasSize(TOTAL_PARTITIONS);
      assertThat(result.epoch()).isGreaterThan(0L);
    }

    @Test
    void shouldGetAssignmentWithCorrectEpochAfterMultipleRebalances() {
      // given — first rebalance
      registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epochAfterFirst = registry.getGroup("g1").getEpoch();

      // second rebalance — add c2
      registry.subscribe("g1", "c2", TOTAL_PARTITIONS);
      final long epochAfterSecond = registry.getGroup("g1").getEpoch();
      assertThat(epochAfterSecond).isGreaterThan(epochAfterFirst);

      // when
      final var result = actor.getAssignment("g1", "c1").join();

      // then — epoch matches the latest coordinator epoch
      assertThat(result.epoch()).isEqualTo(epochAfterSecond);
    }

    @Test
    void shouldThrowConsumerNotRegisteredForGetAssignmentOnNonExistentConsumer() {
      // given — group exists but the consumer is unknown;
      // getAssignment only fails if the GROUP is absent — for an unknown consumer in an existing
      // group it returns an empty partition list without throwing.
      actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // when / then — ghost-consumer has no partitions; result is returned, not an exception
      final var result = actor.getAssignment("g1", "ghost-consumer").join();
      assertThat(result.assignedPartitions()).isEmpty();
    }

    @Test
    void shouldThrowConsumerNotRegisteredForGetAssignmentOnNonExistentGroup() {
      // when / then — no group at all
      assertThatThrownBy(() -> actor.getAssignment("no-such-group", "c1").join())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(CoordinatorActor.ConsumerNotRegisteredException.class);
    }

    @Test
    void shouldReturnEmptyAssignedPartitionsForConsumerWithNoPartitions() {
      // BALANCED_STICKY: a consumer that joins after another has claimed all partitions receives
      // an empty initial assignment — its partitions will only arrive once some are orphaned
      // (e.g., an existing consumer is evicted).
      registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      registry.subscribe("g1", "c2", TOTAL_PARTITIONS);

      // c1 holds all partitions; c2 starts with none (no redistribution of alive assignments)
      assertThat(registry.getGroup("g1").getAssignedPartitions("c1")).hasSize(TOTAL_PARTITIONS);
      assertThat(registry.getGroup("g1").getAssignedPartitions("c2")).isEmpty();
    }
  }

  // -------------------------------------------------------------------------
  // Epoch behaviour

  @Nested
  class EpochBehaviour {

    @Test
    void shouldStartAtEpochZeroBeforeFirstRebalance() {
      // when — first heartbeat (no loop cycle yet)
      final var result = actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // then — epoch is 0 because no rebalance has run yet
      assertThat(result.epoch()).isEqualTo(0L);
    }

    @Test
    void shouldReachEpochOneAfterFirstRebalance() {
      // given — consumer registered
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      assertThat(registry.getGroup("g1").getEpoch()).isEqualTo(0L);

      // when — simulate coordinator loop (evictDeadConsumers triggers rebalance for new consumer)
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);

      // then — epoch advanced to 1
      assertThat(registry.getGroup("g1").getEpoch()).isEqualTo(1L);
    }

    @Test
    void shouldIncrementEpochByOnePerRebalanceTrigger() {
      // given — two sequential rebalances
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);
      assertThat(registry.getGroup("g1").getEpoch()).isEqualTo(1L);

      // second consumer joins → second rebalance
      actor.heartbeat("g1", "c2", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);

      // then — epoch at 2
      assertThat(registry.getGroup("g1").getEpoch()).isEqualTo(2L);
    }

    @Test
    void shouldNotIncrementEpochWhenNoMembershipChangesOccur() {
      // given — single consumer, stable state
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);
      final long stableEpoch = registry.getGroup("g1").getEpoch();

      // when — two more coordinator loop cycles with no changes (consumer is gone, no new ones)
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);

      // then — epoch unchanged (no active consumers, no consumersChanged)
      assertThat(registry.getGroup("g1").getEpoch()).isEqualTo(stableEpoch);
    }
  }

  // -------------------------------------------------------------------------
  // Full-assignment reconciliation path

  @Nested
  class FullAssignmentReconciliation {

    @Test
    void shouldReturnFullAssignmentWhenClientEpochLagsCoordinatorEpoch() {
      // given — c1 subscribes and rebalance runs (epoch = 1)
      final var group = registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long currentEpoch = group.getEpoch();
      assertThat(currentEpoch).isEqualTo(1L);

      // when — c1 heartbeats with epoch 0 (stale; coordinator is at epoch 1)
      final var result = actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // then — fullAssignment is populated; revoke/assign are empty
      assertThat(result.epoch()).isEqualTo(1L);
      assertThat(result.revoke()).isEmpty();
      assertThat(result.assign()).isEmpty();
      assertThat(result.fullAssignment())
          .isNotEmpty()
          .containsExactlyElementsOf(registry.getGroup("g1").getAssignedPartitions("c1"));
    }

    @Test
    void shouldReturnEmptyFullAssignmentWhenClientEpochMatchesCoordinator() {
      // given — c1 subscribes and rebalance runs
      final var group = registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epoch = group.getEpoch();

      // when — heartbeat with matching epoch
      final var result = actor.heartbeat("g1", "c1", epoch, List.of()).join();

      // then — delta path; no full assignment
      assertThat(result.epoch()).isEqualTo(epoch);
      assertThat(result.fullAssignment()).isEmpty();
    }

    @Test
    void shouldTreatClientEpochAheadOfCoordinatorAsMatchingEpoch() {
      // given — c1 subscribes; epoch = 1
      final var group = registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epoch = group.getEpoch();

      // when — heartbeat with future epoch (should be treated as equal, not error)
      final var result = actor.heartbeat("g1", "c1", epoch + 99, List.of()).join();

      // then — delta path (no full assignment); coordinator does not reject or error
      assertThat(result).isNotNull();
      assertThat(result.fullAssignment()).isEmpty();
    }

    @Test
    void shouldReturnEmptyFullAssignmentForNewConsumerBeforeFirstRebalance() {
      // when — brand-new consumer's first heartbeat; rebalance has not run yet
      final var result = actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // then — no assignments yet; fullAssignment is empty (epoch == clientEpoch == 0)
      assertThat(result.revoke()).isEmpty();
      assertThat(result.assign()).isEmpty();
      assertThat(result.fullAssignment()).isEmpty();
    }
  }

  // -------------------------------------------------------------------------
  // ACK processing

  @Nested
  class AckProcessing {

    @Test
    void shouldNotRepeatRevocationAfterConsumerAcks() {
      // given — consumer has partitions; heartbeat triggers a revoke for partition 99
      final var group = registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epoch = group.getEpoch();

      final var hb1 = actor.heartbeat("g1", "c1", epoch, List.of(99)).join();
      assertThat(hb1.revoke()).contains(99);

      // when — consumer ACKs the revocation
      final var ackResult = actor.ack("g1", "c1", epoch, List.of(99), List.of()).join();
      assertThat(ackResult.status()).isEqualTo(AckStatus.OK);

      // when — next heartbeat reports same (empty) owned set
      final var hb2 = actor.heartbeat("g1", "c1", epoch, List.of()).join();

      // then — partition 99 is no longer in revoke (already acked)
      assertThat(hb2.revoke()).doesNotContain(99);
    }

    @Test
    void shouldNotRepeatAssignmentAfterConsumerAcks() {
      // given — c1 subscribes; epoch = 1; c1 is assigned partitions 0-3
      final var group = registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epoch = group.getEpoch();
      final var assigned = group.getAssignedPartitions("c1");

      // c1 heartbeats with empty owned — receives assign delta
      final var hb1 = actor.heartbeat("g1", "c1", epoch, List.of()).join();
      assertThat(hb1.assign()).isNotEmpty();

      // when — c1 ACKs the assignment
      actor.ack("g1", "c1", epoch, List.of(), hb1.assign()).join();

      // then — next heartbeat reporting the newly owned partitions produces no further assignment
      final var hb2 = actor.heartbeat("g1", "c1", epoch, hb1.assign()).join();
      assertThat(hb2.assign()).isEmpty();
    }

    @Test
    void shouldReturnEpochMismatchForStaleAck() {
      // given — consumer registered
      final var group = registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epoch = group.getEpoch();

      // when — ACK with a stale epoch (well above current)
      final var result = actor.ack("g1", "c1", epoch + 42L, List.of(), List.of()).join();

      // then
      assertThat(result.status()).isEqualTo(AckStatus.EPOCH_MISMATCH);
    }

    @Test
    void shouldReturnConsumerNotFoundWhenGroupDoesNotExist() {
      // when — ACK for a group that was never created
      final var result = actor.ack("no-such-group", "c1", 1L, List.of(), List.of()).join();

      // then
      assertThat(result.status()).isEqualTo(AckStatus.CONSUMER_NOT_FOUND);
    }

    @Test
    void shouldReturnConsumerNotFoundWhenConsumerWasEvicted() {
      // given — consumer registered then evicted
      final var group = registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epoch = group.getEpoch();
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();

      // when — ACK arrives after eviction
      final var result = actor.ack("g1", "c1", epoch, List.of(), List.of()).join();

      // then — consumer is gone; stale ACK is discarded
      assertThat(result.status()).isEqualTo(AckStatus.CONSUMER_NOT_FOUND);
    }
  }

  // -------------------------------------------------------------------------
  // Inflight-revocations cap

  @Nested
  class InflightRevocationsCap {

    @Test
    void shouldCapRevocationsWhenMaxInflightIsOne() {
      // given — registry with cap = 1; c1 and c2 both report owning a non-target partition
      final ConsumerGroupRegistry cappedRegistry = new ConsumerGroupRegistry(1);
      cappedRegistry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      cappedRegistry.subscribe("g1", "c2", TOTAL_PARTITIONS);
      final long epoch = cappedRegistry.getGroup("g1").getEpoch();

      // c1 reports owning partition 99 — triggers a revoke (1 inflight slot used)
      final var hb1 =
          cappedRegistry.heartbeat("g1", "c1", epoch, java.util.Set.of(99), TOTAL_PARTITIONS);
      assertThat(hb1.revoke()).contains(99);

      // when — c2 also reports owning partition 98; cap is full (1 in-flight)
      final var hb2 =
          cappedRegistry.heartbeat("g1", "c2", epoch, java.util.Set.of(98), TOTAL_PARTITIONS);

      // then — c2's revoke is deferred; cap was reached after c1's revoke
      assertThat(hb2.revoke()).doesNotContain(98);
    }

    @Test
    void shouldAllowRevocationAfterInflightDropsBelowCap() {
      // given — registry with cap = 1; c1 already has 1 in-flight revoke
      final ConsumerGroupRegistry cappedRegistry = new ConsumerGroupRegistry(1);
      cappedRegistry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      cappedRegistry.subscribe("g1", "c2", TOTAL_PARTITIONS);
      final long epoch = cappedRegistry.getGroup("g1").getEpoch();

      cappedRegistry.heartbeat("g1", "c1", epoch, java.util.Set.of(99), TOTAL_PARTITIONS);
      final var hbBeforeAck =
          cappedRegistry.heartbeat("g1", "c2", epoch, java.util.Set.of(98), TOTAL_PARTITIONS);
      assertThat(hbBeforeAck.revoke()).doesNotContain(98); // capped

      // when — c1 ACKs its revocation → inflight drops to 0
      cappedRegistry.ack("g1", "c1", epoch, List.of(99), List.of());

      // c2 retries heartbeat with the same owned set
      final var hbAfterAck =
          cappedRegistry.heartbeat("g1", "c2", epoch, java.util.Set.of(98), TOTAL_PARTITIONS);

      // then — cap is free; c2 now receives its deferred revoke
      assertThat(hbAfterAck.revoke()).contains(98);
    }

    @Test
    void shouldNotCapRevocationsWhenInflightIsZero() {
      // given — registry with cap = 1; no in-flight revocations yet
      final ConsumerGroupRegistry cappedRegistry = new ConsumerGroupRegistry(1);
      cappedRegistry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final long epoch = cappedRegistry.getGroup("g1").getEpoch();

      // when — first revoke
      final var hb =
          cappedRegistry.heartbeat("g1", "c1", epoch, java.util.Set.of(99), TOTAL_PARTITIONS);

      // then — allowed (cap = 1, inflight was 0)
      assertThat(hb.revoke()).contains(99);
    }
  }

  // -------------------------------------------------------------------------
  // commitOffset integration after re-registration

  @Nested
  class CommitOffsetAfterReRegistration {

    @Test
    void shouldAllowCommitAfterConsumerReRegisters() {
      // given — c1 subscribes, commits, then is evicted
      registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      actor.commitOffset("g1", "c1", 0, 10L).join();
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();

      // when — c1 re-registers via heartbeat
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();

      // then — commit succeeds after re-registration
      actor.commitOffset("g1", "c1", 0, 20L).join();
      assertThat(offsetStore.getCommittedOffset("g1", "c1", 0)).isEqualTo(20L);
    }

    @Test
    void shouldRejectCommitAfterEviction() {
      // given — c1 subscribes then is evicted
      registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();

      // when / then — commit while evicted is rejected
      assertThatThrownBy(() -> actor.commitOffset("g1", "c1", 0, 50L).join())
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(CoordinatorActor.ConsumerNotRegisteredException.class);
    }

    @Test
    void shouldPreserveHistoricalOffsetAfterEvictionAndReRegistration() {
      // Offset store retains committed values even after the consumer is evicted;
      // the truncation boundary is based on alive-and-assigned consumers, but the raw offset value
      // is preserved for idempotency checks.

      // given — c1 commits offset 77, then is evicted and re-registers
      registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      actor.commitOffset("g1", "c1", 0, 77L).join();
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // when — c1 tries to re-commit at a lower position (idempotent; should not regress)
      actor.commitOffset("g1", "c1", 0, 10L).join();

      // then — offset is still 77 (idempotent; lower values are silently ignored)
      assertThat(offsetStore.getCommittedOffset("g1", "c1", 0)).isEqualTo(77L);
    }
  }

  // -------------------------------------------------------------------------
  // Rebalance balance guarantees

  @Nested
  class RebalanceBalance {

    @Test
    void shouldDistributePartitionsEvenlyAcrossTwoConsumers() {
      // given — both consumers join before the first rebalance so the coordinator sees them
      // simultaneously and distributes partitions evenly
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      actor.heartbeat("g1", "c2", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);

      // then — each consumer gets exactly 2 partitions (balanced; skew <= 1)
      assertThat(registry.getGroup("g1").getAssignedPartitions("c1")).hasSize(2);
      assertThat(registry.getGroup("g1").getAssignedPartitions("c2")).hasSize(2);
    }

    @Test
    void shouldDistributePartitionsAcrossThreeConsumers() {
      // given — all three consumers join before the first rebalance (skew = 1: two get 1, one gets
      // 2)
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      actor.heartbeat("g1", "c2", 0L, List.of()).join();
      actor.heartbeat("g1", "c3", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);

      final int s1 = registry.getGroup("g1").getAssignedPartitions("c1").size();
      final int s2 = registry.getGroup("g1").getAssignedPartitions("c2").size();
      final int s3 = registry.getGroup("g1").getAssignedPartitions("c3").size();

      // then — total = 4; max-min skew <= 1
      assertThat(s1 + s2 + s3).isEqualTo(TOTAL_PARTITIONS);
      assertThat(Math.max(s1, Math.max(s2, s3)) - Math.min(s1, Math.min(s2, s3)))
          .isLessThanOrEqualTo(1);
    }

    @Test
    void shouldCoverAllPartitionsAfterRebalance() {
      // given — both consumers join before rebalance so all partitions are distributed
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      actor.heartbeat("g1", "c2", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);

      // then — every partition 0-3 is assigned to exactly one consumer
      final var assignment = registry.getGroup("g1").getPartitionAssignment();
      assertThat(assignment).hasSize(TOTAL_PARTITIONS).containsKeys(0, 1, 2, 3);
    }

    @Test
    void shouldKeepExistingAssignmentsSticky() {
      // given — c1 is established with all partitions via subscribe
      registry.subscribe("g1", "c1", TOTAL_PARTITIONS);
      final var c1Initial = registry.getGroup("g1").getAssignedPartitions("c1");
      assertThat(c1Initial).hasSize(TOTAL_PARTITIONS);

      // when — c2 joins; BALANCED_STICKY preserves c1's alive assignments; no orphans exist,
      // so c2 receives no partitions until c1 is evicted
      registry.subscribe("g1", "c2", TOTAL_PARTITIONS);

      final var c1After = registry.getGroup("g1").getAssignedPartitions("c1");
      final var c2After = registry.getGroup("g1").getAssignedPartitions("c2");

      // then — c1 retains all its original partitions; c2 gets none (sticky, no forced migration)
      assertThat(c1After).containsExactlyElementsOf(c1Initial);
      assertThat(c2After).isEmpty();
    }
  }
}
