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
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinatorImpl;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.BrokerState.State;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests of the new multi-partition-group path through the real {@link
 * ConfigurationChangeCoordinatorImpl} + {@link ClusterConfigurationManagerImpl}, exercising plan
 * generation, phased dispatch and phase advancement for the default tenant.
 */
final class NewModelConfigurationChangeCoordinatorTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @TempDir private Path tmp;

  private ClusterConfigurationManagerImpl manager;
  private ConfigurationChangeCoordinatorImpl coordinator;

  private void wire(final MemberId localMemberId, final CurrentClusterConfiguration seed) {
    final var persisted =
        PersistedCurrentClusterConfiguration.ofFile(
            tmp.resolve("config-" + localMemberId.id() + ".meta"), new ProtoBufSerializer());
    manager =
        new ClusterConfigurationManagerImpl(
            executor,
            localMemberId,
            persisted,
            new TopologyManagerMetrics(new SimpleMeterRegistry()),
            Duration.ofMillis(1),
            Duration.ofMillis(1));
    manager.setCurrentConfigurationGossiper(ignored -> {});
    manager.registerGlobalChangeAppliers(
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor()));
    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopClusterChangeExecutor(),
            new NoopModeChangeExecutor()));
    coordinator = new ConfigurationChangeCoordinatorImpl(manager, localMemberId, executor);
    manager.updateMultiConfiguration(ignored -> seed).join();
  }

  /** Two active members, both replicating partition 1 in the default group. */
  private CurrentClusterConfiguration twoMemberCluster() {
    final var group =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                MEMBER_0,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(2, partitionConfig))),
                MEMBER_1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, partitionConfig)))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(
                MEMBER_0, new BrokerState(0, Instant.EPOCH, State.ACTIVE),
                MEMBER_1, new BrokerState(0, Instant.EPOCH, State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()),
        Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, group),
        PhasedChangeState.empty());
  }

  private CurrentClusterConfiguration configuration() {
    return manager.getMultiConfiguration().join();
  }

  @Test
  void shouldGenerateApplyAndAdvanceAMultiPhasePlan() {
    // given — a two-member cluster; the local member 0 is the coordinator
    wire(MEMBER_0, twoMemberCluster());
    // a request producing a global operation followed by a partition operation, both targeting
    // member 0 → a two-phase plan (global phase, then a default-group partition phase)
    final ConfigurationChangeRequest request =
        current ->
            Either.right(
                List.of(
                    new PreScalingOperation(MEMBER_0, Set.of(MEMBER_0, MEMBER_1)),
                    new PartitionLeaveOperation(MEMBER_0, 1, 1)));

    // when
    final var result = coordinator.applyOperations(request).join();

    // then — the generated operations are returned, the plan drains phase by phase and completes
    assertThat(result.operations())
        .containsExactly(
            new PreScalingOperation(MEMBER_0, Set.of(MEMBER_0, MEMBER_1)),
            new PartitionLeaveOperation(MEMBER_0, 1, 1));

    final var config = configuration();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(config.phasedChangeState().lastChange())
        .hasValueSatisfying(
            last -> assertThat(last.status()).isEqualTo(PhasedChangePlanStatus.COMPLETED));
    assertThat(config.globalConfiguration().hasPendingChanges()).isFalse();
    final var defaultGroup = config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasPendingChanges()).isFalse();
    // member 0 left partition 1; member 1 still hosts it
    assertThat(defaultGroup.hasMember(MEMBER_0)).isFalse();
    assertThat(defaultGroup.hasMember(MEMBER_1)).isTrue();
  }

  @Test
  void shouldRejectWhenNotCoordinator() {
    // given — the local member is 1, but member 0 (lower id) is the coordinator
    wire(MEMBER_1, twoMemberCluster());
    final ConfigurationChangeRequest request =
        current -> Either.right(List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1)));

    // when / then — the non-coordinator refuses to apply the change
    assertThat(coordinator.applyOperations(request)).failsWithin(Duration.ofSeconds(5));
    assertThat(configuration().phasedChangeState().pending()).isEmpty();
  }

  @Test
  void shouldNotStartPlanOnDryRun() {
    // given
    wire(MEMBER_0, twoMemberCluster());
    final ConfigurationChangeRequest request =
        current -> Either.right(List.of(new PartitionLeaveOperation(MEMBER_0, 1, 1)));

    // when
    final var result = coordinator.simulateOperations(request).join();

    // then — the operations are returned but no plan is started
    assertThat(result.operations()).containsExactly(new PartitionLeaveOperation(MEMBER_0, 1, 1));
    assertThat(configuration().phasedChangeState().pending()).isEmpty();
    final var defaultGroup =
        configuration().partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
    assertThat(defaultGroup.hasPendingChanges()).isFalse();
    assertThat(defaultGroup.hasMember(MEMBER_0)).isTrue();
  }

  @Test
  void shouldCompleteWithoutChangesWhenNoOperationsGenerated() {
    // given
    wire(MEMBER_0, twoMemberCluster());
    final ConfigurationChangeRequest request = current -> Either.right(List.of());

    // when
    final var result = coordinator.applyOperations(request).join();

    // then
    assertThat(result.operations()).isEmpty();
    assertThat(configuration().phasedChangeState().pending()).isEmpty();
  }
}
