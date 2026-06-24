/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static io.camunda.zeebe.dynamic.config.state.ExporterState.State.ENABLED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.PartitionStateAssert;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClusterConfigurationTest {

  private final DynamicPartitionConfig emptyPartitionConfig = DynamicPartitionConfig.init();

  @Test
  void canInitializeClusterWithPreExistingMembers() {
    // when
    final var topology =
        ClusterConfiguration.init()
            .addMember(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, emptyPartitionConfig))))
            .addMember(
                member(2),
                MemberState.initializeAsActive(
                    Map.of(2, PartitionState.active(1, emptyPartitionConfig))))
            .addMember(
                member(3),
                MemberState.initializeAsActive(
                    Map.of(3, PartitionState.active(1, emptyPartitionConfig))));

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(1, State.ACTIVE)
        .member(1)
        .hasPartitionWithState(1, PartitionState.State.ACTIVE)
        .hasPartitionWithPriority(1, 1);
    ClusterConfigurationAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(2, State.ACTIVE)
        .member(2)
        .hasPartitionWithState(2, PartitionState.State.ACTIVE)
        .hasPartitionWithPriority(2, 1);
    ClusterConfigurationAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(3, State.ACTIVE)
        .member(3)
        .hasPartitionWithState(3, PartitionState.State.ACTIVE)
        .hasPartitionWithPriority(3, 1);
  }

  @Test
  void canDetermineClusterSizePartitionAndReplicationFactor() {
    // when
    final var topology =
        ClusterConfiguration.init()
            .addMember(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, emptyPartitionConfig))))
            .addMember(
                member(2),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, emptyPartitionConfig))))
            .addMember(
                member(3),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(3, emptyPartitionConfig))));

    // then
    assertThat(topology.clusterSize()).isEqualTo(3);
    assertThat(topology.partitionCount()).isEqualTo(1);
    assertThat(topology.minReplicationFactor()).isEqualTo(3);
  }

  @Test
  void shouldMergeConcurrentUpdatesToMembers() {
    // given
    final var topology =
        ClusterConfiguration.init()
            .addMember(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.joining(1, emptyPartitionConfig))))
            .addMember(
                member(2),
                MemberState.initializeAsActive(
                    Map.of(2, PartitionState.joining(1, emptyPartitionConfig))));

    // update topology in one member
    final var topologyInMemberOne =
        topology.updateMember(member(1), m -> m.updatePartition(1, PartitionState::toActive));
    // update topology in the other member concurrently
    final var topologyInMemberTwo =
        topology.updateMember(member(2), m -> m.updatePartition(2, PartitionState::toActive));

    // when
    final var mergedTopologyOne = topologyInMemberOne.merge(topologyInMemberTwo);
    final var mergedTopologyTwo = topologyInMemberTwo.merge(topologyInMemberOne);

    // then

    ClusterConfigurationAssert.assertThatClusterTopology(mergedTopologyOne)
        .hasMemberWithState(1, State.ACTIVE)
        .member(1)
        .hasPartitionWithState(1, PartitionState.State.ACTIVE)
        .hasPartitionWithPriority(1, 1);
    ClusterConfigurationAssert.assertThatClusterTopology(mergedTopologyOne)
        .hasMemberWithState(2, State.ACTIVE)
        .member(2)
        .hasPartitionWithState(2, PartitionState.State.ACTIVE)
        .hasPartitionWithPriority(2, 1);

    assertThat(mergedTopologyTwo).isEqualTo(mergedTopologyOne);
  }

  @Test
  void shouldAddANewMember() {
    // given
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, emptyPartitionConfig))))
            .addMember(
                member(2),
                MemberState.initializeAsActive(
                    Map.of(2, PartitionState.active(1, emptyPartitionConfig))));

    var topologyOnAnotherMember = ClusterConfiguration.init().merge(initialTopology);

    // when
    final var updateTopology =
        initialTopology.addMember(member(3), MemberState.uninitialized().toJoining());

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(updateTopology)
        .describedAs("Update topology must have the new member and the old members")
        .hasMemberWithState(3, State.JOINING)
        .hasMemberWithState(1, State.ACTIVE)
        .hasMemberWithState(2, State.ACTIVE);

    // when
    topologyOnAnotherMember = topologyOnAnotherMember.merge(updateTopology);

    // then
    assertThat(topologyOnAnotherMember).isEqualTo(updateTopology);
  }

  @Test
  void shouldAdvanceClusterTopologyChanges() {
    // given
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(member(1), MemberState.uninitialized())
            .startConfigurationChange(
                List.of(
                    new PartitionLeaveOperation(member(1), 1, 1),
                    new PartitionLeaveOperation(member(2), 2, 1)));

    // when
    final var updatedTopology =
        initialTopology.advanceConfigurationChange(
            t -> t.updateMember(member(1), MemberState::toActive));

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(updatedTopology)
        .hasMemberWithState(1, State.ACTIVE)
        .hasPendingOperationsWithSize(1);
  }

  @Test
  void shouldIncrementVersionWhenChangeIsCompleted() {
    // given
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(member(1), MemberState.initializeAsActive(Map.of()))
            .startConfigurationChange(List.of(new PartitionLeaveOperation(member(1), 1, 1)));

    // when
    final var updatedTopology =
        initialTopology.advanceConfigurationChange(
            t -> t.updateMember(member(1), MemberState::toLeft));

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(updatedTopology)
        .doesNotHaveMember(1)
        .hasPendingOperationsWithSize(0)
        .hasVersion(initialTopology.version() + 1);
  }

  @Test
  void shouldMergeClusterTopologyChanges() {
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(member(1), MemberState.uninitialized())
            .startConfigurationChange(
                List.of(
                    new PartitionLeaveOperation(member(1), 1, 1),
                    new PartitionLeaveOperation(member(2), 2, 1)));

    // when
    final var updatedTopology =
        initialTopology.advanceConfigurationChange(
            t -> t.updateMember(member(1), MemberState::toActive));

    final var mergedTopology = initialTopology.merge(updatedTopology);

    // then
    assertThat(mergedTopology).isEqualTo(updatedTopology);
  }

  @Test
  void shouldAddCompletedClusterTopologyChanges() {
    // given
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(member(1), MemberState.initializeAsActive(Map.of()))
            .startConfigurationChange(List.of(new PartitionJoinOperation(member(1), 1, 1)));
    final var changeId = initialTopology.pendingChanges().orElseThrow().id();

    // when
    final var finalTopology =
        initialTopology.advanceConfigurationChange(
            t ->
                t.updateMember(
                    member(1),
                    m -> m.addPartition(1, PartitionState.active(1, emptyPartitionConfig))));

    // then
    final var expected =
        ClusterConfiguration.builder()
            .version(2)
            .members(
                Map.of(
                    member(1),
                    MemberState.initializeAsActive(
                        Map.of(1, PartitionState.active(1, emptyPartitionConfig)))))
            .lastChange(
                Optional.of(
                    new CompletedChange(changeId, Status.COMPLETED, Instant.now(), Instant.now())))
            .pendingChanges(Optional.empty())
            .routingState(Optional.empty())
            .clusterId(Optional.empty())
            .build();

    ClusterConfigurationAssert.assertThatClusterTopology(finalTopology).hasSameTopologyAs(expected);
  }

  @Test
  void shouldUpdateExporterState() {
    // given
    final String exporterName = "exporter";
    final var exportersConfig =
        new ExportingConfig(
            ExportingState.EXPORTING,
            Map.of(exporterName, new ExporterState(1, ENABLED, Optional.of("other"))));
    final var config = new DynamicPartitionConfig(exportersConfig);

    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, new PartitionState(PartitionState.State.ACTIVE, 1, config))));

    // when
    final var updatedTopology =
        initialTopology.updateMember(
            member(1),
            m ->
                m.updatePartition(
                    1,
                    p ->
                        p.updateConfig(
                            c -> c.updateExporting(e -> e.disableExporter(exporterName)))));

    // then
    assertThat(
            updatedTopology.getMember(member(1)).getPartition(1).config().exporting().exporters())
        .containsEntry(
            exporterName, new ExporterState(1, ExporterState.State.DISABLED, Optional.empty()));
  }

  @Test
  void shouldMergeUninitializedDynamicConfig() {
    // given
    final var topologyWithUninitializedConfig =
        ClusterConfiguration.builder()
            .version(0)
            .members(
                Map.of(
                    member(1),
                    MemberState.initializeAsActive(
                        Map.of(
                            1, PartitionState.active(1, DynamicPartitionConfig.uninitialized())))))
            .lastChange(Optional.empty())
            .pendingChanges(Optional.empty())
            .routingState(Optional.empty())
            .clusterId(Optional.empty())
            .build();

    final DynamicPartitionConfig validConfig =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of("expA", new ExporterState(1, ENABLED, Optional.empty()))));
    final var topologyWithValidConfig =
        ClusterConfiguration.builder()
            .version(0)
            .members(
                Map.of(
                    member(1),
                    MemberState.initializeAsActive(
                        Map.of(1, PartitionState.active(1, validConfig)))))
            .lastChange(Optional.empty())
            .pendingChanges(Optional.empty())
            .routingState(Optional.empty())
            .clusterId(Optional.empty())
            .build();

    // when
    final var mergeValidToUninitialized =
        topologyWithUninitializedConfig.merge(topologyWithValidConfig);
    final var mergeUninitializedToValid =
        topologyWithValidConfig.merge(topologyWithUninitializedConfig);

    // then
    assertThat(mergeValidToUninitialized).isEqualTo(topologyWithValidConfig);
    ClusterConfigurationAssert.assertThatClusterTopology(mergeUninitializedToValid)
        .member(1)
        .hasPartitionSatisfying(1, p -> PartitionStateAssert.assertThat(p).hasConfig(validConfig));
  }

  @Test
  void shouldMergeRoutingState() {
    // given
    final var oldRoutingState =
        Optional.of(new RoutingState(1, new AllPartitions(3), new HashMod(3)));
    final var oldConfig =
        ClusterConfiguration.builder()
            .version(1)
            .members(Map.of())
            .lastChange(Optional.empty())
            .pendingChanges(Optional.empty())
            .routingState(oldRoutingState)
            .clusterId(Optional.empty())
            .build();

    final var newRoutingState =
        Optional.of(new RoutingState(2, new AllPartitions(4), new HashMod(4)));
    final var newConfig =
        ClusterConfiguration.builder()
            .version(1)
            .members(Map.of())
            .lastChange(Optional.empty())
            .pendingChanges(Optional.empty())
            .routingState(newRoutingState)
            .clusterId(Optional.empty())
            .build();

    // when
    final var merged = oldConfig.merge(newConfig);

    // then
    assertThat(merged.routingState()).isEqualTo(newRoutingState);
  }

  @Test
  void shouldMergeIncarnationNumber() {
    // given
    final var oldConfig =
        ClusterConfiguration.builder()
            .version(1)
            .members(Map.of())
            .lastChange(Optional.empty())
            .pendingChanges(Optional.empty())
            .routingState(Optional.empty())
            .clusterId(Optional.empty())
            .incarnationNumber(5)
            .build();

    final var newConfig =
        ClusterConfiguration.builder()
            .version(1)
            .members(Map.of())
            .lastChange(Optional.empty())
            .pendingChanges(Optional.empty())
            .routingState(Optional.empty())
            .clusterId(Optional.empty())
            .incarnationNumber(6)
            .build();

    // when
    final var merged = oldConfig.merge(newConfig);

    // then
    assertThat(merged.incarnationNumber()).isEqualTo(6);
  }

  @Test
  void initialIncarnationNumberIsZero() {
    // given
    final var initialConfig = ClusterConfiguration.init();

    // then
    assertThat(initialConfig.incarnationNumber()).isZero();
  }

  private MemberId member(final int id) {
    return MemberId.from(Integer.toString(id));
  }

  @Nested
  class ZoneAware {
    @Test
    void shouldClassifyZoneAwarenessForUnzoned() {
      // given
      final var configs =
          List.of(
              ClusterConfiguration.init(),
              ClusterConfiguration.init()
                  .addMember(member(1), MemberState.initializeAsActive(Map.of())));

      for (final var config : configs) {
        assertThat(config)
            .returns(true, ClusterConfiguration::isUnzoned)
            .returns(false, ClusterConfiguration::isPartiallyZoneAware)
            .returns(false, ClusterConfiguration::isFullyZoneAware);
      }
    }

    @Test
    void shouldClassifyZoneAwarenessForPartiallyZoned() {
      // given
      final var config =
          ClusterConfiguration.init()
              .addMember(member(1), MemberState.initializeAsActive(Map.of()))
              .addMember(MemberId.from("zoneA", 1), MemberState.initializeAsActive(Map.of()));

      // then
      assertThat(config)
          .returns(false, ClusterConfiguration::isUnzoned)
          .returns(true, ClusterConfiguration::isPartiallyZoneAware)
          .returns(false, ClusterConfiguration::isFullyZoneAware);
    }

    @Test
    void shouldClassifyZoneAwarenessForPartiallyZonedWhenDistributionIsNotConfigured() {
      // given
      final var config =
          ClusterConfiguration.init()
              .addMember(MemberId.from("zoneB", 1), MemberState.initializeAsActive(Map.of()))
              .addMember(MemberId.from("zoneA", 1), MemberState.initializeAsActive(Map.of()));

      // then
      assertThat(config)
          .returns(false, ClusterConfiguration::isUnzoned)
          .returns(true, ClusterConfiguration::isPartiallyZoneAware)
          .returns(false, ClusterConfiguration::isFullyZoneAware);
    }

    @Test
    void shouldClassifyZoneAwarenessForFullyZoned() {
      // given
      final var config =
          ClusterConfiguration.init()
              .addMember(MemberId.from("zoneB", 1), MemberState.initializeAsActive(Map.of()))
              .addMember(MemberId.from("zoneA", 1), MemberState.initializeAsActive(Map.of()))
              .setPartitionDistributorConfig(
                  new PartitionDistributorConfig.ZoneAwareConfig(
                      List.of(new ZoneSpec("zoneA", 1, 100), new ZoneSpec("zoneB", 1, 100))));

      assertThat(config)
          .returns(false, ClusterConfiguration::isUnzoned)
          .returns(false, ClusterConfiguration::isPartiallyZoneAware)
          .returns(true, ClusterConfiguration::isFullyZoneAware);
    }
  }

  @Nested
  class PartitionDistributor {
    @Test
    void shouldMergeIdenticalPartitionDistributorConfigs() {
      // given two members that initialized the same distributor config at the same version
      final var config =
          new PartitionDistributorConfig.ZoneAwareConfig(
              List.of(new PartitionDistributorConfig.ZoneSpec("zone-a", 2, 1000)));
      final var topologyInMemberOne =
          ClusterConfiguration.init()
              .addMember(member(1), MemberState.initializeAsActive(Map.of()))
              .setPartitionDistributorConfig(config);
      final var topologyInMemberTwo =
          ClusterConfiguration.init()
              .addMember(member(2), MemberState.initializeAsActive(Map.of()))
              .setPartitionDistributorConfig(config);

      // when
      final var merged = topologyInMemberOne.merge(topologyInMemberTwo);

      // then
      assertThat(merged.partitionDistributorConfig()).hasValue(config);
    }

    @Test
    void shouldKeepPartitionDistributorConfigWhenOtherHasNone() {
      // given
      final var config = new PartitionDistributorConfig.RoundRobinConfig();
      final var topologyWithConfig =
          ClusterConfiguration.init()
              .addMember(member(1), MemberState.initializeAsActive(Map.of()))
              .setPartitionDistributorConfig(config);
      final var topologyWithoutConfig =
          ClusterConfiguration.init()
              .addMember(member(2), MemberState.initializeAsActive(Map.of()));

      // then
      assertThat(topologyWithConfig.merge(topologyWithoutConfig).partitionDistributorConfig())
          .hasValue(config);
      assertThat(topologyWithoutConfig.merge(topologyWithConfig).partitionDistributorConfig())
          .hasValue(config);
    }

    @Test
    void shouldFailToMergeConflictingPartitionDistributorConfigsAtSameVersion() {
      // given two members that initialized different distributor configs at the same version
      final var topologyInMemberOne =
          ClusterConfiguration.init()
              .addMember(member(1), MemberState.initializeAsActive(Map.of()))
              .setPartitionDistributorConfig(new PartitionDistributorConfig.RoundRobinConfig());
      final var topologyInMemberTwo =
          ClusterConfiguration.init()
              .addMember(member(2), MemberState.initializeAsActive(Map.of()))
              .setPartitionDistributorConfig(
                  new PartitionDistributorConfig.ZoneAwareConfig(
                      List.of(new PartitionDistributorConfig.ZoneSpec("zone-a", 2, 1000))));

      // when / then
      assertThatThrownBy(() -> topologyInMemberOne.merge(topologyInMemberTwo))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot merge two different partition distributor configs");
    }

    @Test
    void shouldUsePartitionDistributorConfigOfHigherVersionWhenMerging() {
      // given a member that updated the config (bumping the version) and a member on the old config
      final var oldConfig = new PartitionDistributorConfig.RoundRobinConfig();
      final var newConfig =
          new PartitionDistributorConfig.ZoneAwareConfig(
              List.of(new PartitionDistributorConfig.ZoneSpec("zone-a", 2, 1000)));
      final var oldTopology =
          ClusterConfiguration.init()
              .addMember(member(1), MemberState.initializeAsActive(Map.of()))
              .setPartitionDistributorConfig(oldConfig);
      final var newTopology =
          oldTopology
              .startConfigurationChange(List.of(new PartitionLeaveOperation(member(1), 1, 1)))
              .setPartitionDistributorConfig(newConfig);

      // when
      final var merged = oldTopology.merge(newTopology);

      // then the higher version wins wholesale
      assertThat(merged.partitionDistributorConfig()).hasValue(newConfig);
    }
  }
}
