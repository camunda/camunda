/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.NoopConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliers.PartitionGroupOperationApplier;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationSerializer;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClusterConfigurationManagerPartitionGroupTest {

  @TempDir Path tempDir;
  private final ClusterConfigurationSerializer serializer = new ProtoBufSerializer();
  private final MemberId localMemberId = MemberId.from("1");

  private final ClusterConfiguration initialTopology =
      ClusterConfiguration.init()
          .addMember(localMemberId, MemberState.initializeAsActive(Map.of()));

  private final ClusterConfigurationInitializer successInitializer =
      () -> CompletableActorFuture.completed(initialTopology);

  private PersistedClusterConfiguration persistedClusterConfiguration;
  @AutoClose private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TopologyManagerMetrics topologyMetrics = new TopologyManagerMetrics(meterRegistry);

  @BeforeEach
  void init() {
    persistedClusterConfiguration =
        PersistedClusterConfiguration.ofFile(tempDir.resolve("topology.temp"), serializer);
  }

  @Test
  void shouldApplyOperationForNonDefaultGroup() {
    // given
    final var manager = startManager();
    final var groupConfig =
        PartitionGroupConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .startConfigurationChange(List.of(new PartitionLeaveOperation(localMemberId, 1, 1)));
    manager.setPartitionGroupConfig("tenantA", groupConfig);
    manager.registerPartitionGroupAppliers("tenantA", new LeaveApplier());

    // when — ops are applied by the actor synchronously in TestConcurrencyControl
    // then
    Awaitility.await("tenantA config has no pending operations after apply")
        .untilAsserted(
            () -> {
              final var current = manager.getPartitionGroupConfig("tenantA");
              assertThat(current).isNotNull();
              assertThat(current.hasPendingChanges()).isFalse();
            });
  }

  @Test
  void shouldNotApplyOperationWhenNoAppliersRegistered() {
    // given
    final var manager = startManager();
    final var groupConfig =
        PartitionGroupConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .startConfigurationChange(List.of(new PartitionLeaveOperation(localMemberId, 1, 1)));

    // when — set config but do NOT register appliers
    manager.setPartitionGroupConfig("tenantA", groupConfig);

    // then — pending op remains because no applier is registered
    final var current = manager.getPartitionGroupConfig("tenantA");
    assertThat(current).isNotNull();
    assertThat(current.hasPendingChanges()).isTrue();
  }

  @Test
  void shouldApplyOperationsForTwoGroupsConcurrently() {
    // given — both groups have a pending op; appliers that track how many are started
    final var opsStarted = new AtomicInteger(0);
    final var groupAFuture =
        new CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>>();
    final var groupBFuture =
        new CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>>();

    final PartitionGroupConfigurationChangeAppliers appliersA =
        op -> new CapturingApplier(localMemberId, opsStarted, groupAFuture);
    final PartitionGroupConfigurationChangeAppliers appliersB =
        op -> new CapturingApplier(localMemberId, opsStarted, groupBFuture);

    final var manager = startManager();

    final var configA =
        PartitionGroupConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .startConfigurationChange(List.of(new PartitionLeaveOperation(localMemberId, 1, 1)));
    final var configB =
        PartitionGroupConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .startConfigurationChange(List.of(new PartitionLeaveOperation(localMemberId, 2, 1)));

    manager.setPartitionGroupConfig("tenantA", configA);
    manager.setPartitionGroupConfig("tenantB", configB);

    // when — register appliers (triggers apply)
    manager.registerPartitionGroupAppliers("tenantA", appliersA);
    manager.registerPartitionGroupAppliers("tenantB", appliersB);

    // then — both ops are started before either completes (concurrent dispatch)
    Awaitility.await("both group operations are initiated concurrently")
        .untilAsserted(() -> assertThat(opsStarted.get()).isEqualTo(2));

    // complete both
    groupAFuture.complete(c -> c.updateMember(localMemberId, MemberState::toLeft));
    groupBFuture.complete(c -> c.updateMember(localMemberId, MemberState::toLeft));

    Awaitility.await("both group configs have no pending operations")
        .untilAsserted(
            () -> {
              assertThat(manager.getPartitionGroupConfig("tenantA").hasPendingChanges()).isFalse();
              assertThat(manager.getPartitionGroupConfig("tenantB").hasPendingChanges()).isFalse();
            });
  }

  @Test
  void shouldApplyNonDefaultGroupOpIndependentlyFromDefaultGroup() {
    // given — default group has no pending op; tenantA has one
    final var appliersApplied = new AtomicBoolean(false);
    final PartitionGroupConfigurationChangeAppliers groupAppliers =
        op -> new TrackingLeaveApplier(localMemberId, appliersApplied);

    final var manager = startManager();
    final var groupConfig =
        PartitionGroupConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .startConfigurationChange(List.of(new PartitionLeaveOperation(localMemberId, 1, 1)));

    manager.setPartitionGroupConfig("tenantA", groupConfig);
    manager.registerPartitionGroupAppliers("tenantA", groupAppliers);

    // then — tenantA op is applied without touching the default group
    Awaitility.await("tenantA op applied")
        .untilAsserted(() -> assertThat(appliersApplied).isTrue());
    assertThat(manager.getPartitionGroupConfig("tenantA").hasPendingChanges()).isFalse();

    // default group config unchanged
    assertThat(persistedClusterConfiguration.getConfiguration().hasPendingChanges()).isFalse();
  }

  @Test
  void shouldNotApplyNonDefaultGroupOpWhenAppliersRemovedBeforeCompletion() {
    // given
    final var manager = startManager();
    final var groupConfig =
        PartitionGroupConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .startConfigurationChange(List.of(new PartitionLeaveOperation(localMemberId, 1, 1)));
    manager.setPartitionGroupConfig("tenantA", groupConfig);

    // when — remove appliers before registering (ensure no applier is set)
    manager.removePartitionGroupAppliers("tenantA");

    // then — nothing applied
    final var current = manager.getPartitionGroupConfig("tenantA");
    assertThat(current.hasPendingChanges()).isTrue();
  }

  @Test
  void shouldUpdateGroupConfigViaUpdatePartitionGroupConfig() {
    // given
    final var manager = startManager();
    final var initial =
        PartitionGroupConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()));
    manager.setPartitionGroupConfig("tenantA", initial);

    // when
    final var withPendingOp =
        initial.startConfigurationChange(List.of(new PartitionLeaveOperation(localMemberId, 1, 1)));
    manager.registerPartitionGroupAppliers("tenantA", new LeaveApplier());
    manager.updatePartitionGroupConfig("tenantA", c -> withPendingOp).join();

    // then — pending op is applied
    Awaitility.await("tenantA pending op applied")
        .untilAsserted(
            () ->
                assertThat(manager.getPartitionGroupConfig("tenantA").hasPendingChanges())
                    .isFalse());
  }

  // ---- helpers ----

  private ClusterConfigurationManagerImpl startManager() {
    final var manager =
        new ClusterConfigurationManagerImpl(
            new TestConcurrencyControl(),
            localMemberId,
            persistedClusterConfiguration,
            topologyMetrics,
            Duration.ofMillis(100),
            Duration.ofMillis(200));
    manager.setConfigurationGossiper(ignored -> {});

    final ActorFuture<ClusterConfigurationManagerImpl> startFuture = new TestActorFuture<>();
    manager
        .start(successInitializer)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                manager.registerTopologyChangeAppliers(new NoopConfigurationChangeAppliers());
                startFuture.complete(manager);
              } else {
                startFuture.completeExceptionally(error);
              }
            });
    return startFuture.join();
  }

  /** Completes the leave operation synchronously. */
  private static final class LeaveApplier implements PartitionGroupConfigurationChangeAppliers {
    @Override
    public PartitionGroupOperationApplier getApplier(
        final ClusterConfigurationChangeOperation operation) {
      final MemberId memberId = operation.memberId();
      return new PartitionGroupOperationApplier() {
        @Override
        public MemberId memberId() {
          return memberId;
        }

        @Override
        public io.camunda.zeebe.util.Either<Exception, UnaryOperator<PartitionGroupConfiguration>>
            init(final PartitionGroupConfiguration config) {
          return io.camunda.zeebe.util.Either.right(
              c -> c.updateMember(memberId, MemberState::toLeaving));
        }

        @Override
        public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
          return CompletableActorFuture.completed(
              c -> c.updateMember(memberId, MemberState::toLeft));
        }
      };
    }
  }

  /** Tracks that the operation was applied. Implements PartitionGroupOperationApplier directly. */
  private static final class TrackingLeaveApplier implements PartitionGroupOperationApplier {
    private final MemberId memberId;
    private final AtomicBoolean applied;

    TrackingLeaveApplier(final MemberId memberId, final AtomicBoolean applied) {
      this.memberId = memberId;
      this.applied = applied;
    }

    @Override
    public MemberId memberId() {
      return memberId;
    }

    @Override
    public io.camunda.zeebe.util.Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
        final PartitionGroupConfiguration config) {
      return io.camunda.zeebe.util.Either.right(
          c -> c.updateMember(memberId, MemberState::toLeaving));
    }

    @Override
    public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
      applied.set(true);
      return CompletableActorFuture.completed(c -> c.updateMember(memberId, MemberState::toLeft));
    }
  }

  /**
   * Increments a counter when apply() is called, then waits for an externally-completable future.
   * Used to verify that two groups start their operations before either completes (concurrency
   * check).
   */
  private static final class CapturingApplier implements PartitionGroupOperationApplier {
    private final MemberId memberId;
    private final AtomicInteger started;
    private final CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>> resultFuture;

    CapturingApplier(
        final MemberId memberId,
        final AtomicInteger started,
        final CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>> resultFuture) {
      this.memberId = memberId;
      this.started = started;
      this.resultFuture = resultFuture;
    }

    @Override
    public MemberId memberId() {
      return memberId;
    }

    @Override
    public io.camunda.zeebe.util.Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
        final PartitionGroupConfiguration config) {
      return io.camunda.zeebe.util.Either.right(
          c -> c.updateMember(memberId, MemberState::toLeaving));
    }

    @Override
    public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
      started.incrementAndGet();
      return resultFuture;
    }
  }
}
