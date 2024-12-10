/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class ClusterPatchRequestTransformerTest {

  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final MemberId id2 = MemberId.from("2");
  private final MemberId id3 = MemberId.from("3");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldRejectIfSameMembersAreAddedAndRemoved() {
    // given
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(id0, id2),
            Set.of(id0, id1),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false);

    // when
    final var result =
        new ClusterPatchRequestTransformer(
                patchRequest.membersToAdd(),
                patchRequest.membersToRemove(),
                patchRequest.newPartitionCount(),
                patchRequest.newReplicationFactor(),
                patchRequest.newPartitionsDistribution())
            .operations(ClusterConfiguration.init());

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldRejectWhenScalingDownPartitions() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    // when
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(), Set.of(), Optional.of(1), Optional.empty(), Optional.empty(), false);
    final var result =
        new ClusterPatchRequestTransformer(
                patchRequest.membersToAdd(),
                patchRequest.membersToRemove(),
                patchRequest.newPartitionCount(),
                patchRequest.newReplicationFactor(),
                patchRequest.newPartitionsDistribution())
            .operations(ClusterConfiguration.init());

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldScaleUpBrokersWhenPartitionUnchanged() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)));

    // when
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(id2), Set.of(), Optional.empty(), Optional.empty(), Optional.empty(), false);
    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id1, id2), getSortedPartitionIds(2), 2);

    // then
    applyRequestAndVerifyResultingTopology(
        2, getClusterMembers(3), patchRequest, currentTopology, expectedDistribution);
  }

  @Test
  void shouldScaleDownBrokersWhenPartitionUnchanged() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    // when
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(), Set.of(id1), Optional.empty(), Optional.empty(), Optional.empty(), false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0), getSortedPartitionIds(2), 1);

    // then
    applyRequestAndVerifyResultingTopology(
        2, Set.of(id0), patchRequest, currentTopology, expectedDistribution);
  }

  @Test
  void shouldAddAndRemoveBrokersWhenPartitionsUnchanged() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    // when
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(id2), Set.of(id1), Optional.empty(), Optional.empty(), Optional.empty(), false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id2), getSortedPartitionIds(2), 1);

    // then
    applyRequestAndVerifyResultingTopology(
        2, Set.of(id0, id2), patchRequest, currentTopology, expectedDistribution);
  }

  @Test
  void shouldAddAndRemoveBrokersAndAddPartitions() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    // when
    final int newPartitionCount = 4;
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(id2),
            Set.of(id1),
            Optional.of(newPartitionCount),
            Optional.empty(),
            Optional.empty(),
            false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id2), getSortedPartitionIds(newPartitionCount), 1);

    // then
    applyRequestAndVerifyResultingTopology(
        newPartitionCount, Set.of(id0, id2), patchRequest, currentTopology, expectedDistribution);
  }

  @Test
  void shouldAddAndRemoveBrokersAndAddPartitionsAndChangeReplicationFactor() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    // when
    final int newPartitionCount = 4;
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(id2),
            Set.of(id1),
            Optional.of(newPartitionCount),
            Optional.of(2),
            Optional.empty(),
            false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id2), getSortedPartitionIds(newPartitionCount), 2);

    // then
    applyRequestAndVerifyResultingTopology(
        newPartitionCount, Set.of(id0, id2), patchRequest, currentTopology, expectedDistribution);
  }

  private void applyRequestAndVerifyResultingTopology(
      final int partitionCount,
      final Set<MemberId> expectedMembers,
      final ClusterPatchRequest patchRequest,
      final ClusterConfiguration oldClusterTopology,
      final Set<PartitionMetadata> expectedNewDistribution) {

    // when
    final var result =
        new ClusterPatchRequestTransformer(
                patchRequest.membersToAdd(),
                patchRequest.membersToRemove(),
                patchRequest.newPartitionCount(),
                patchRequest.newReplicationFactor(),
                patchRequest.newPartitionsDistribution())
            .operations(oldClusterTopology);
    assertThat(result).isRight();
    final var operations = result.get();

    // apply operations to generate new topology
    final ClusterConfiguration newTopology =
        TestTopologyChangeSimulator.apply(oldClusterTopology, operations);

    // then
    final var newDistribution = ConfigurationUtil.getPartitionDistributionFrom(newTopology, "temp");
    Assertions.assertThat(newDistribution)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedNewDistribution);
    Assertions.assertThat(newTopology.members().keySet())
        .describedAs("Expected cluster members")
        .containsExactlyInAnyOrderElementsOf(expectedMembers);
    Assertions.assertThat(newTopology.partitionCount()).isEqualTo(partitionCount);
  }

  private List<PartitionId> getSortedPartitionIds(final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount)
        .mapToObj(id -> PartitionId.from("temp", id))
        .collect(Collectors.toList());
  }

  private Set<MemberId> getClusterMembers(final int newClusterSize) {
    return IntStream.range(0, newClusterSize)
        .mapToObj(Integer::toString)
        .map(MemberId::from)
        .collect(Collectors.toSet());
  }
}
