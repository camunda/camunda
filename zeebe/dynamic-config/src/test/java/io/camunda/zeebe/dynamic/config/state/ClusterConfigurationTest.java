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

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.PartitionStateAssert;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        new ClusterConfiguration(
            2,
            Map.of(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, emptyPartitionConfig)))),
            Optional.of(
                new CompletedChange(changeId, Status.COMPLETED, Instant.now(), Instant.now())),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    ClusterConfigurationAssert.assertThatClusterTopology(finalTopology).hasSameTopologyAs(expected);
  }

  @Test
  void shouldUpdateExporterState() {
    // given
    final String exporterName = "exporter";
    final var exportersConfig =
        new ExportersConfig(
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
        new ClusterConfiguration(
            0,
            Map.of(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.uninitialized())))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    final DynamicPartitionConfig validConfig =
        new DynamicPartitionConfig(
            new ExportersConfig(Map.of("expA", new ExporterState(1, ENABLED, Optional.empty()))));
    final var topologyWithValidConfig =
        new ClusterConfiguration(
            0,
            Map.of(
                member(1),
                MemberState.initializeAsActive(Map.of(1, PartitionState.active(1, validConfig)))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

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
        new ClusterConfiguration(
            1, Map.of(), Optional.empty(), Optional.empty(), oldRoutingState, Optional.empty());

    final var newRoutingState =
        Optional.of(new RoutingState(2, new AllPartitions(4), new HashMod(4)));
    final var newConfig =
        new ClusterConfiguration(
            1, Map.of(), Optional.empty(), Optional.empty(), newRoutingState, Optional.empty());

    // when
    final var merged = oldConfig.merge(newConfig);

    // then
    assertThat(merged.routingState()).isEqualTo(newRoutingState);
  }

  private MemberId member(final int id) {
    return MemberId.from(Integer.toString(id));
  }
}
