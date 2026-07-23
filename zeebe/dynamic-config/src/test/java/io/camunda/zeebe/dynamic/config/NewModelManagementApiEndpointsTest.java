/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDeleteRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdatePartitionDistributorConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateRoutingStateRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestsHandler;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinatorImpl;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that every {@link io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementApi}
 * endpoint generates the expected plan for the <em>default</em> tenant when the new configuration
 * model is enabled, driven through the real request handler + coordinator + manager. Mirrors the
 * scenarios in {@code ClusterConfigurationManagementApiTest} (which runs against a recording
 * coordinator on the legacy model), asserting the same planned changes on the new path.
 */
final class NewModelManagementApiEndpointsTest {

  private static final MemberId ID_0 = MemberId.from("0");
  private static final MemberId ID_1 = MemberId.from("1");
  private static final MemberId ID_2 = MemberId.from("2");
  private static final MemberId ID_3 = MemberId.from("3");
  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @TempDir private Path tmp;

  private ClusterConfigurationManagerImpl manager;
  private ClusterConfigurationManagementRequestsHandler handler;

  /** Wires the real handler → coordinator → manager (new model) seeded from a legacy topology. */
  private void wire(final ClusterConfiguration seed) {
    final var persisted =
        PersistedCurrentClusterConfiguration.ofFile(
            tmp.resolve("config.meta"), new ProtoBufSerializer());
    manager =
        new ClusterConfigurationManagerImpl(
            executor,
            ID_0,
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
    final var coordinator = new ConfigurationChangeCoordinatorImpl(manager, ID_0, executor);
    handler = new ClusterConfigurationManagementRequestsHandler(coordinator, ID_0, executor);
    manager
        .updateMultiConfiguration(ignored -> CurrentClusterConfiguration.fromLegacy(seed))
        .join();
  }

  private ClusterConfiguration singleActiveMember() {
    return ClusterConfiguration.init().addMember(ID_0, MemberState.initializeAsActive(Map.of()));
  }

  @Test
  void shouldPlanAddMembers() {
    // given
    wire(singleActiveMember());

    // when
    final var response = handler.addMembers(new AddMembersRequest(Set.of(ID_1), false)).join();

    // then
    assertThat(response.plannedChanges()).containsExactly(new MemberJoinOperation(ID_1));
  }

  @Test
  void shouldPlanRemoveMembers() {
    // given
    wire(
        singleActiveMember()
            .addMember(ID_1, MemberState.initializeAsActive(Map.of()))
            .addMember(ID_2, MemberState.initializeAsActive(Map.of())));

    // when
    final var response =
        handler.removeMembers(new RemoveMembersRequest(Set.of(ID_1, ID_2), false)).join();

    // then
    assertThat(response.plannedChanges())
        .containsExactlyElementsOf(
            List.of(new MemberLeaveOperation(ID_1), new MemberLeaveOperation(ID_2)));
  }

  @Test
  void shouldPlanJoinPartition() {
    // given — partition 1 already has an active member (ID_0), so ID_1 can join it
    wire(
        ClusterConfiguration.init()
            .addMember(
                ID_0,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))))
            .addMember(ID_1, MemberState.initializeAsActive(Map.of())));

    // when
    final var response = handler.joinPartition(new JoinPartitionRequest(ID_1, 1, 3, false)).join();

    // then
    assertThat(response.plannedChanges()).containsExactly(new PartitionJoinOperation(ID_1, 1, 3));
  }

  @Test
  void shouldPlanLeavePartition() {
    // given — partition 1 has two replicas so ID_1 can leave without dropping below the minimum
    wire(
        ClusterConfiguration.init()
            .addMember(
                ID_0,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, partitionConfig))))
            .addMember(
                ID_1,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var response = handler.leavePartition(new LeavePartitionRequest(ID_1, 1, false)).join();

    // then
    assertThat(response.plannedChanges()).containsExactly(new PartitionLeaveOperation(ID_1, 1, 1));
  }

  @Test
  void shouldPlanReassignPartitions() {
    // given
    wire(
        singleActiveMember()
            .addMember(
                ID_1,
                MemberState.initializeAsActive(
                    Map.of(
                        1,
                        PartitionState.active(1, partitionConfig),
                        2,
                        PartitionState.active(1, partitionConfig))))
            .addMember(ID_2, MemberState.initializeAsActive(Map.of())));

    // when
    final var response =
        handler.reassignPartitions(new ReassignPartitionsRequest(Set.of(ID_1, ID_2), false)).join();

    // then
    assertThat(response.plannedChanges())
        .containsExactly(
            new PartitionJoinOperation(ID_2, 2, 1), new PartitionLeaveOperation(ID_1, 2, 1));
  }

  @Test
  void shouldPlanScaleBrokers() {
    // given
    wire(
        singleActiveMember()
            .updateMember(ID_0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(ID_0, m -> m.addPartition(2, PartitionState.active(1, partitionConfig))));

    // when
    final var response =
        handler.scaleMembers(new BrokerScaleRequest(Set.of(ID_0, ID_1), false)).join();

    // then
    assertThat(response.plannedChanges())
        .containsExactly(
            new PreScalingOperation(ID_0, Set.of(ID_0, ID_1)),
            new MemberJoinOperation(ID_1),
            new PartitionJoinOperation(ID_1, 2, 1),
            new PartitionLeaveOperation(ID_0, 2, 1),
            new PostScalingOperation(ID_0, Set.of(ID_0, ID_1)));
  }

  @Test
  void shouldPlanScaleCluster() {
    // given
    wire(
        singleActiveMember()
            .updateMember(ID_0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(ID_0, m -> m.addPartition(2, PartitionState.active(1, partitionConfig))));

    // when — scale the cluster to two brokers, keeping the current partition count (no scale-up)
    final var response =
        handler
            .scaleCluster(
                new ClusterScaleRequest(
                    Optional.of(2), Optional.of(2), Optional.empty(), Optional.empty(), false))
            .join();

    // then
    assertThat(response.plannedChanges())
        .startsWith(new PreScalingOperation(ID_0, Set.of(ID_0, ID_1)))
        .endsWith(new PostScalingOperation(ID_0, Set.of(ID_0, ID_1)))
        .contains(new MemberJoinOperation(ID_1));
  }

  @Test
  void shouldPlanPatchCluster() {
    // given
    wire(
        singleActiveMember()
            .updateMember(ID_0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(ID_0, m -> m.addPartition(2, PartitionState.active(1, partitionConfig))));

    // when — add a broker, keeping the current partition count (no scale-up)
    final var response =
        handler
            .patchCluster(
                new ClusterPatchRequest(
                    Set.of(ID_1), Set.of(), Optional.of(2), Optional.empty(), false))
            .join();

    // then
    assertThat(response.plannedChanges())
        .startsWith(new PreScalingOperation(ID_0, Set.of(ID_0, ID_1)))
        .endsWith(new PostScalingOperation(ID_0, Set.of(ID_0, ID_1)))
        .contains(new MemberJoinOperation(ID_1));
  }

  @Test
  void shouldPlanForceScaleDown() {
    // given
    wire(fourMemberTwoPartitionCluster());

    // when
    final var response =
        handler.forceScaleDown(new BrokerScaleRequest(Set.of(ID_0, ID_2), false)).join();

    // then
    assertThat(response.plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionForceReconfigureOperation(ID_0, 1, Set.of(ID_0)),
            new PartitionForceReconfigureOperation(ID_2, 2, Set.of(ID_2)),
            new MemberRemoveOperation(ID_0, ID_1),
            new MemberRemoveOperation(ID_0, ID_3));
  }

  @Test
  void shouldPlanForceRemoveBrokers() {
    // given
    wire(fourMemberTwoPartitionCluster());

    // when
    final var response =
        handler.forceRemoveBrokers(new ForceRemoveBrokersRequest(Set.of(ID_1, ID_3), false)).join();

    // then
    assertThat(response.plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionForceReconfigureOperation(ID_0, 1, Set.of(ID_0)),
            new PartitionForceReconfigureOperation(ID_2, 2, Set.of(ID_2)),
            new MemberRemoveOperation(ID_0, ID_1),
            new MemberRemoveOperation(ID_0, ID_3));
  }

  @Test
  void shouldPlanDisableExporter() {
    // given
    final var exporterId = "exporterId";
    final var partitionConfigWithExporter =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of(exporterId, new ExporterState(1, State.ENABLED, Optional.empty()))));
    wire(
        singleActiveMember()
            .updateMember(
                ID_0,
                m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter))));

    // when
    final var response =
        handler.disableExporter(new ExporterDisableRequest(exporterId, false)).join();

    // then
    assertThat(response.plannedChanges())
        .containsExactly(new PartitionDisableExporterOperation(ID_0, 1, exporterId));
  }

  @Test
  void shouldApplyDisableExporterEndToEndForLocalMember() {
    // given — the exporter lives on the local member's partition
    final var exporterId = "exporterId";
    final var partitionConfigWithExporter =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of(exporterId, new ExporterState(1, State.ENABLED, Optional.empty()))));
    wire(
        singleActiveMember()
            .updateMember(
                ID_0,
                m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter))));

    // when
    handler.disableExporter(new ExporterDisableRequest(exporterId, false)).join();

    // then — the plan is applied on the local member and completes
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    final var exporterState =
        config
            .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
            .getMember(ID_0)
            .partitions()
            .get(1)
            .config()
            .exporting()
            .exporters()
            .get(exporterId);
    assertThat(exporterState.state()).isEqualTo(State.DISABLED);
  }

  @Test
  void shouldPlanDeleteExporter() {
    // given — the exporter is in CONFIG_NOT_FOUND, the state a delete requires
    final var exporterId = "exporterId";
    final var configWithExporter =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of(
                    exporterId, new ExporterState(1, State.CONFIG_NOT_FOUND, Optional.empty()))));
    wire(
        singleActiveMember()
            .updateMember(
                ID_0, m -> m.addPartition(1, PartitionState.active(1, configWithExporter))));

    // when
    final var response =
        handler.deleteExporter(new ExporterDeleteRequest(exporterId, false)).join();

    // then
    assertThat(response.plannedChanges())
        .containsExactly(new PartitionDeleteExporterOperation(ID_0, 1, exporterId));
  }

  @Test
  void shouldPlanEnableExporter() {
    // given — the exporter is DISABLED so it can be enabled
    final var exporterId = "exporterId";
    final var configWithExporter =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of(exporterId, new ExporterState(1, State.DISABLED, Optional.empty()))));
    wire(
        singleActiveMember()
            .updateMember(
                ID_0, m -> m.addPartition(1, PartitionState.active(1, configWithExporter))));

    // when
    final var response =
        handler
            .enableExporter(new ExporterEnableRequest(exporterId, Optional.empty(), false))
            .join();

    // then
    assertThat(response.plannedChanges())
        .containsExactly(
            new PartitionEnableExporterOperation(ID_0, 1, exporterId, Optional.empty()));
  }

  @Test
  void shouldPlanPurge() {
    // given — a member with a partition
    wire(
        singleActiveMember()
            .updateMember(ID_0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig))));

    // when
    final var response = handler.purge(new PurgeRequest(false)).join();

    // then — purge produces a plan and preserves the partition assignment of every member
    assertThat(response.plannedChanges()).isNotEmpty();
    assertThat(response.expectedConfiguration())
        .containsOnlyKeys(response.currentConfiguration().keySet().toArray(MemberId[]::new));
  }

  @Test
  void shouldApplyModeChangeEndToEndForLocalMember() {
    // given — the local member is active and processing in the default group
    wire(
        ClusterConfiguration.init()
            .addMember(
                ID_0,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when — switch to recovery mode
    handler.modeChange(new ModeChangeRequest(Mode.RECOVERING, false)).join();

    // then — the plan is applied and the local member is now in recovery mode
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(
            config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).getMember(ID_0).mode())
        .isEqualTo(Mode.RECOVERING);
  }

  @Test
  void shouldPlanUpdateRoutingState() {
    // given
    wire(
        singleActiveMember()
            .updateMember(ID_0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig))));
    final var routingState = RoutingState.initializeWithPartitionCount(1);

    // when
    final var response =
        handler
            .updateRoutingState(new UpdateRoutingStateRequest(Optional.of(routingState), false))
            .join();

    // then
    assertThat(response.plannedChanges())
        .containsExactly(new UpdateRoutingState(ID_0, Optional.of(routingState)));
  }

  @Test
  void shouldRejectUpdatePartitionDistributionForNonZoneAwareConfig() {
    // given — a bare cluster and a non-zone-aware config, which the transformer rejects
    wire(
        singleActiveMember()
            .updateMember(ID_0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig))));

    // when / then — the endpoint is reachable on the new model and rejects the invalid request
    assertThatCode(
            () ->
                handler
                    .updatePartitionDistribution(
                        new UpdatePartitionDistributorConfigRequest(new RoundRobinConfig(), false))
                    .join())
        .hasMessageContaining("ZONE_AWARE");
  }

  @Test
  void shouldValidateRestoreOnlyWhenRecovering() {
    // given — the local member is in recovery mode. It must host a partition: recovery mode lives
    // on BrokerPartitionState, so a partitionless member is not in the default group and its mode
    // would not survive the round-trip through toLegacyDefault.
    wire(
        ClusterConfiguration.init()
            .addMember(
                ID_0,
                MemberState.initializeAsActive(Map.of(1, PartitionState.active(1, partitionConfig)))
                    .toRecovering()));

    // when — a dry-run restore is validated
    final var result =
        handler.restore(
            new RestoreRequest(List.of(100L), null, null, "elasticsearch", false, true));

    // then — accepted, because toLegacyDefault projects the member back to RECOVERING
    assertThatCode(result::join).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectRestoreWhenNotRecovering() {
    // given — the local member is active (not recovering)
    wire(singleActiveMember());

    // when / then — restore is rejected
    assertThatCode(
            () ->
                handler
                    .restore(
                        new RestoreRequest(
                            List.of(100L), null, null, "elasticsearch", false, false))
                    .join())
        .hasMessageContaining("recovery mode");
  }

  // cancelTopologyChange on the new model is implemented separately (issue #58398); not covered
  // here.

  @Test
  void shouldGetTopology() {
    // given
    wire(singleActiveMember().addMember(ID_1, MemberState.initializeAsActive(Map.of())));

    // when
    final var topology = handler.getTopology().join();

    // then — the default-group projection carries all cluster members
    assertThat(topology.members()).containsKeys(ID_0, ID_1);
  }

  private ClusterConfiguration fourMemberTwoPartitionCluster() {
    return ClusterConfiguration.init()
        .addMember(ID_0, MemberState.initializeAsActive(Map.of()))
        .addMember(ID_1, MemberState.initializeAsActive(Map.of()))
        .addMember(ID_2, MemberState.initializeAsActive(Map.of()))
        .addMember(ID_3, MemberState.initializeAsActive(Map.of()))
        .updateMember(ID_0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
        .updateMember(ID_1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
        .updateMember(ID_2, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
        .updateMember(ID_3, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)));
  }
}
