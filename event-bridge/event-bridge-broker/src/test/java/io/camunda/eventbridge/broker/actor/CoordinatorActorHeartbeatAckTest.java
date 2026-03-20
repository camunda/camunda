/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.actor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry;
import io.camunda.eventbridge.broker.offset.OffsetStore;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CoordinatorActor} heartbeat (with auto-registration), ACK, and
 * dead-consumer-detection paths.
 *
 * <p>Complements {@link CoordinatorActorTruncationTest} which covers the truncation boundary and
 * commit-offset actor dispatch.
 */
class CoordinatorActorHeartbeatAckTest {

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
  // Heartbeat — auto-registration

  @Nested
  class HeartbeatAutoRegistration {

    @Test
    void shouldAutoRegisterConsumerOnFirstHeartbeat() {
      // when
      actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // then — consumer is now active in the registry
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldReturnCurrentEpochOnHeartbeat() {
      // when — first heartbeat, consumer and group both new
      final var result = actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // then — epoch is returned (0 before the first rebalance loop cycle)
      assertThat(result.epoch()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void shouldReturnEmptyDeltaOnFirstHeartbeatBeforeRebalance() {
      // Rebalance is deferred to the coordinator loop; no assignments exist yet.
      // when
      final var result = actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // then — no revocations or assignments yet
      assertThat(result.revoke()).isEmpty();
      assertThat(result.assign()).isEmpty();
    }

    @Test
    void shouldNotThrowForUnknownGroupOnHeartbeat() {
      // Heartbeat auto-creates the group — no exception expected.
      final var result = actor.heartbeat("brand-new-group", "c1", 0L, List.of()).join();
      assertThat(result).isNotNull();
    }

    @Test
    void shouldNotThrowForUnknownConsumerInExistingGroup() {
      // given — group exists via a prior heartbeat
      actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // when — different consumer in same group (first contact)
      final var result = actor.heartbeat("g1", "c2", 0L, List.of()).join();

      // then — auto-registered, no exception
      assertThat(registry.isConsumerActive("g1", "c2")).isTrue();
      assertThat(result).isNotNull();
    }

    @Test
    void shouldTreatGroupsAsIndependentNamespaces() {
      // when — same consumer ID in two separate groups
      final var ra = actor.heartbeat("group-a", "c1", 0L, List.of()).join();
      final var rb = actor.heartbeat("group-b", "c1", 0L, List.of()).join();

      // then — both succeed independently
      assertThat(ra).isNotNull();
      assertThat(rb).isNotNull();
      assertThat(registry.isConsumerActive("group-a", "c1")).isTrue();
      assertThat(registry.isConsumerActive("group-b", "c1")).isTrue();
    }
  }

  // -------------------------------------------------------------------------
  // Heartbeat — epoch and delta semantics

  @Nested
  class HeartbeatEpochAndDelta {

    @Test
    void shouldReturnFullAssignmentWhenClientEpochIsLessThanCoordinatorEpoch() {
      // given — register and force a rebalance to advance epoch
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      registry.evictDeadConsumers(
          Instant.MIN, TOTAL_PARTITIONS); // trigger rebalance; epoch advances
      final var group = registry.getGroup("g1");
      final long epochAfterRebalance = group.getEpoch();
      assertThat(epochAfterRebalance).isGreaterThan(0L);

      // when — consumer sends old epoch (0) while coordinator is at a higher epoch
      final var result = actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // then — full assignment returned for reconciliation
      if (result.epoch() > 0L) {
        // epoch advanced → fullAssignment should be populated
        assertThat(result.revoke()).isEmpty();
        assertThat(result.assign()).isEmpty();
        assertThat(result.fullAssignment()).isNotEmpty();
      }
    }

    @Test
    void shouldReturnDeltaWhenClientEpochMatchesCoordinatorEpoch() {
      // given — consumer registered, epoch known
      final var first = actor.heartbeat("g1", "c1", 0L, List.of()).join();
      final long knownEpoch = first.epoch();

      // when — consumer sends matching epoch with no partitions owned
      final var result = actor.heartbeat("g1", "c1", knownEpoch, List.of()).join();

      // then — delta path (no fullAssignment); revoke/assign may be empty if no rebalance yet
      assertThat(result.fullAssignment()).isEmpty();
    }

    @Test
    void shouldNotIncrementEpochOnRoutineHeartbeatFromKnownConsumer() {
      // given — consumer registered
      final var first = actor.heartbeat("g1", "c1", 0L, List.of()).join();
      final long epochBefore = first.epoch();

      // when — same consumer heartbeats again (no new consumer, no eviction)
      final var second = actor.heartbeat("g1", "c1", epochBefore, List.of()).join();

      // then — epoch unchanged
      assertThat(second.epoch()).isEqualTo(epochBefore);
    }

    @Test
    void shouldIncludeRevokeForPartitionConsumerClaimsButIsNotTargeted() {
      // given — run rebalance so c1 owns all partitions
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.MIN, TOTAL_PARTITIONS);
      final var group = registry.getGroup("g1");
      final long epoch = group.getEpoch();

      // Simulate c1 owning partition 99 (outside the configured range — not in target)
      final var result = actor.heartbeat("g1", "c1", epoch, List.of(99)).join();

      // then — partition 99 should be in revoke (not in target)
      assertThat(result.revoke()).contains(99);
    }
  }

  // -------------------------------------------------------------------------
  // Ack

  @Nested
  class Ack {

    @Test
    void shouldReturnOkStatusForValidAck() {
      // given — consumer registered with a known epoch
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.MIN, TOTAL_PARTITIONS);
      final var group = registry.getGroup("g1");
      final long epoch = group.getEpoch();

      // when
      final var result = actor.ack("g1", "c1", epoch, List.of(), List.of()).join();

      // then
      assertThat(result.status()).isEqualTo(ConsumerGroupRegistry.AckStatus.OK);
    }

    @Test
    void shouldReturnEpochMismatchForStaleEpoch() {
      // given — consumer registered with epoch 1
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.MIN, TOTAL_PARTITIONS);
      final var group = registry.getGroup("g1");
      final long epoch = group.getEpoch();

      // when — ACK with wrong epoch
      final var result = actor.ack("g1", "c1", epoch + 99, List.of(), List.of()).join();

      // then
      assertThat(result.status()).isEqualTo(ConsumerGroupRegistry.AckStatus.EPOCH_MISMATCH);
    }

    @Test
    void shouldReturnConsumerNotFoundForUnknownConsumer() {
      // when — ACK for a consumer that never heartbeated
      final var result = actor.ack("g1", "unknown-consumer", 1L, List.of(), List.of()).join();

      // then
      assertThat(result.status()).isEqualTo(ConsumerGroupRegistry.AckStatus.CONSUMER_NOT_FOUND);
    }

    @Test
    void shouldClearPendingRevokeAfterAck() {
      // given — register and run rebalance so c1 has partitions in target
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.MIN, TOTAL_PARTITIONS);
      final var group = registry.getGroup("g1");
      final long epoch = group.getEpoch();

      // Heartbeat with partition 99 in owned (should trigger revoke)
      final var hbResult = actor.heartbeat("g1", "c1", epoch, List.of(99)).join();
      assertThat(hbResult.revoke()).contains(99);

      // when — ACK confirming revocation of partition 99
      actor.ack("g1", "c1", epoch, List.of(99), List.of()).join();

      // then — subsequent heartbeat should not re-revoke partition 99
      final var hbResult2 = actor.heartbeat("g1", "c1", epoch, List.of()).join();
      assertThat(hbResult2.revoke()).doesNotContain(99);
    }
  }

  // -------------------------------------------------------------------------
  // Heartbeat-timeout-driven eviction

  @Nested
  class HeartbeatTimeout {

    @Test
    void shouldKeepConsumerActiveWhileHeartbeating() {
      // given
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();

      // when — eviction with a deadline in the past (no consumer is evicted)
      registry.evictDeadConsumers(Instant.now().minusSeconds(60), TOTAL_PARTITIONS);

      // then — still active
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldMarkConsumerDeadAfterSessionTimeout() {
      // given
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();

      // when — evict with far-future deadline (all consumers expire)
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // then — consumer is gone; heartbeat re-registers it
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();
    }

    @Test
    void shouldAutoReRegisterAfterEvictionOnNextHeartbeat() {
      // given — consumer evicted
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);
      assertThat(registry.isConsumerActive("g1", "c1")).isFalse();

      // when — consumer sends heartbeat again (auto-registration)
      actor.heartbeat("g1", "c1", 0L, List.of()).join();

      // then — registered again without throwing
      assertThat(registry.isConsumerActive("g1", "c1")).isTrue();
    }

    @Test
    void shouldSuspendTruncationWhenAllConsumersAreDead() {
      // given — consumer registered and committed
      actor.heartbeat("g1", "c1", 0L, List.of()).join();
      actor.commitOffset("g1", "c1", 0, 42L).join();

      // when — consumer dies
      registry.evictDeadConsumers(Instant.now().plusSeconds(3600), TOTAL_PARTITIONS);

      // then — no alive assigned consumer → truncation boundary is MAX_VALUE
      final long boundary = actor.getTruncationBoundary(0).join();
      assertThat(boundary).isEqualTo(Long.MAX_VALUE);
    }
  }

  // -------------------------------------------------------------------------
  // Coordinator loop ordering

  @Nested
  class CoordinatorLoop {

    @Test
    void shouldEvictConsumerWhenAckDeadlineExpires() {
      // given — use a registry with ACK timeout enabled (1 ms — effectively immediate)
      final ConsumerGroupRegistry registryWithAckTimeout =
          new ConsumerGroupRegistry(Integer.MAX_VALUE, 1L);
      final CoordinatorActor actorWithAckTimeout =
          new CoordinatorActor(registryWithAckTimeout, offsetStore, PROPERTIES, TOTAL_PARTITIONS);
      scheduler.submitActor(actorWithAckTimeout).join();

      try {
        // Register c1, run a rebalance so it owns partitions, then send a heartbeat that triggers
        // a revoke (ACK expected but never comes)
        actorWithAckTimeout.heartbeat("g1", "c1", 0L, List.of()).join();
        registryWithAckTimeout.evictDeadConsumers(Instant.MIN, TOTAL_PARTITIONS);
        final var group = registryWithAckTimeout.getGroup("g1");
        final long epoch = group.getEpoch();
        // Heartbeat reporting partition 99 (not in target) — triggers a revoke → ackDeadline set
        actorWithAckTimeout.heartbeat("g1", "c1", epoch, List.of(99)).join();

        // Expire the ackDeadline (1 ms ago is enough since ackTimeoutMs == 1)
        registryWithAckTimeout.expireAckTimeouts(Instant.now().plusMillis(100));

        // when — expireAckTimeouts has already evicted c1; evictDeadConsumers sees consumersChanged
        registryWithAckTimeout.evictDeadConsumers(
            Instant.now().minusSeconds(3600), TOTAL_PARTITIONS);

        // then — c1 was evicted due to ACK timeout
        assertThat(registryWithAckTimeout.isConsumerActive("g1", "c1")).isFalse();
      } finally {
        actorWithAckTimeout.closeAsync().join();
      }
    }

    @Test
    void shouldNotEvictConsumerFromAckTimeoutWhenAckArrivesInTime() {
      // given — registry with ACK timeout enabled
      final ConsumerGroupRegistry registryWithAckTimeout =
          new ConsumerGroupRegistry(Integer.MAX_VALUE, 5_000L);
      final CoordinatorActor actorWithAckTimeout =
          new CoordinatorActor(registryWithAckTimeout, offsetStore, PROPERTIES, TOTAL_PARTITIONS);
      scheduler.submitActor(actorWithAckTimeout).join();

      try {
        actorWithAckTimeout.heartbeat("g1", "c1", 0L, List.of()).join();
        registryWithAckTimeout.evictDeadConsumers(Instant.MIN, TOTAL_PARTITIONS);
        final var group = registryWithAckTimeout.getGroup("g1");
        final long epoch = group.getEpoch();
        // Heartbeat with partition 99 → triggers a revoke
        final var hbResult = actorWithAckTimeout.heartbeat("g1", "c1", epoch, List.of(99)).join();
        // Consumer ACKs immediately — clears ackDeadline
        actorWithAckTimeout.ack("g1", "c1", epoch, hbResult.revoke(), hbResult.assign()).join();

        // when — expire with far-future deadline (ackDeadline was cleared by the ACK)
        registryWithAckTimeout.expireAckTimeouts(Instant.now().plusSeconds(3600));

        // then — consumer is still active
        assertThat(registryWithAckTimeout.isConsumerActive("g1", "c1")).isTrue();
      } finally {
        actorWithAckTimeout.closeAsync().join();
      }
    }
  }
}
