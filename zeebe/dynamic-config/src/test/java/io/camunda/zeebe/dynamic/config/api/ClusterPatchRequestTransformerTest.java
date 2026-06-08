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
import io.camunda.zeebe.dynamic.config.RoutingStateInitializer;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
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
            Set.of(id0, id2), Set.of(id0, id1), Optional.empty(), Optional.empty(), false);

    // when
    final var result =
        new ClusterPatchRequestTransformer(
                patchRequest.membersToAdd(),
                patchRequest.membersToRemove(),
                patchRequest.newPartitionCount(),
                patchRequest.newReplicationFactor())
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
        new ClusterPatchRequest(Set.of(), Set.of(), Optional.of(1), Optional.empty(), false);
    final var result =
        new ClusterPatchRequestTransformer(
                patchRequest.membersToAdd(),
                patchRequest.membersToRemove(),
                patchRequest.newPartitionCount(),
                patchRequest.newReplicationFactor())
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
        new ClusterPatchRequest(Set.of(id2), Set.of(), Optional.empty(), Optional.empty(), false);
    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id1, id2), getSortedPartitionIds(2), 2);

    // then
    applyRequestAndVerifyResultingTopology(
        2, 2, getClusterMembers(3), patchRequest, currentTopology, expectedDistribution);
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
        new ClusterPatchRequest(Set.of(), Set.of(id1), Optional.empty(), Optional.empty(), false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0), getSortedPartitionIds(2), 1);

    // then
    applyRequestAndVerifyResultingTopology(
        2, 2, Set.of(id0), patchRequest, currentTopology, expectedDistribution);
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
            Set.of(id2), Set.of(id1), Optional.empty(), Optional.empty(), false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id2), getSortedPartitionIds(2), 1);

    // then
    applyRequestAndVerifyResultingTopology(
        2, 2, Set.of(id0, id2), patchRequest, currentTopology, expectedDistribution);
  }

  @Test
  void shouldAddAndRemoveBrokersAndAddPartitions() {
    // given
    var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));
    currentTopology = new RoutingStateInitializer(2).modify(currentTopology).join();

    // when
    final int newPartitionCount = 4;
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(id2), Set.of(id1), Optional.of(newPartitionCount), Optional.empty(), false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id2), getSortedPartitionIds(newPartitionCount), 1);

    // then
    applyRequestAndVerifyResultingTopology(
        2,
        newPartitionCount,
        Set.of(id0, id2),
        patchRequest,
        currentTopology,
        expectedDistribution);
  }

  @Test
  void shouldAddAndRemoveBrokersAndAddPartitionsAndChangeReplicationFactor() {
    // given
    var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));
    currentTopology = new RoutingStateInitializer(2).modify(currentTopology).join();

    // when
    final int newPartitionCount = 4;
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(id2), Set.of(id1), Optional.of(newPartitionCount), Optional.of(2), false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id2), getSortedPartitionIds(newPartitionCount), 2);

    // then
    applyRequestAndVerifyResultingTopology(
        2,
        newPartitionCount,
        Set.of(id0, id2),
        patchRequest,
        currentTopology,
        expectedDistribution);
  }

  @Test
  void shouldRejectNewReplicationFactorsOnNonZoneAwareCluster() {
    // given a plain cluster without ZoneAwareConfig
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    // when
    final var result =
        new ClusterPatchRequestTransformer(
                Set.of(), Set.of(), Optional.empty(), Optional.empty(), Map.of("zone-a", 2))
            .operations(currentTopology);

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldRejectUnknownZoneInNewReplicationFactors() {
    // given a zone-aware cluster with zones zone-a and zone-b
    final var currentTopology = zoneAwareTopology();

    // when requesting a replica count for a zone that does not exist
    final var result =
        new ClusterPatchRequestTransformer(
                Set.of(), Set.of(), Optional.empty(), Optional.empty(), Map.of("zone-x", 2))
            .operations(currentTopology);

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
    Assertions.assertThat(result.getLeft()).hasMessageContaining("zone-x");
  }

  @Test
  void shouldRejectPlainReplicationFactorOnZoneAwareCluster() {
    // given a zone-aware cluster
    final var currentTopology = zoneAwareTopology();

    // when
    final var result =
        new ClusterPatchRequestTransformer(
                Set.of(), Set.of(), Optional.empty(), Optional.of(2), Map.of())
            .operations(currentTopology);

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldEmitUpdatePartitionDistributorConfigFirstWhenChangingZoneReplicationFactor() {
    // given a zone-aware cluster: zone-a rf=2, zone-b rf=1
    final var currentTopology = zoneAwareTopology();

    // when increasing zone-a's replication factor to 3
    final var result =
        new ClusterPatchRequestTransformer(
                Set.of(MemberId.from("zone-a", 2)),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Map.of("zone-a", 3))
            .operations(currentTopology);

    // then the first operation gossips the new distributor config
    assertThat(result).isRight();
    final var operations = result.get();
    Assertions.assertThat(operations)
        .isNotEmpty()
        .first()
        .isEqualTo(
            new UpdatePartitionDistributorConfigOperation(
                currentTopology.members().firstKey(),
                new ZoneAwareConfig(
                    List.of(new ZoneSpec("zone-a", 3, 1000), new ZoneSpec("zone-b", 1, 500)))));
  }

  @Test
  void shouldScaleZoneAwareClusterWithPartitionCountAndZoneReplicationFactor() {
    // given a zone-aware cluster: zone-a rf=2, zone-b rf=1 -- 2 partitions
    final var initialConfig =
        new ZoneAwareConfig(
            List.of(new ZoneSpec("zone-a", 2, 1000), new ZoneSpec("zone-b", 1, 500)));
    final var oldMembers =
        Set.of(MemberId.from("zone-a", 0), MemberId.from("zone-a", 1), MemberId.from("zone-b", 0));
    final var oldDistribution =
        initialConfig.toDistributor().distributePartitions(oldMembers, getSortedPartitionIds(2), 3);
    var currentTopology =
        ConfigurationUtil.getClusterConfigFrom(oldDistribution, partitionConfig, "temp")
            .setPartitionDistributorConfig(initialConfig);
    currentTopology = new RoutingStateInitializer(2).modify(currentTopology).join();

    // when adding a zone-a broker, raising zone-a rf=3, and scaling to 4 partitions
    final var newMember = MemberId.from("zone-a", 2);
    final var newConfig =
        new ZoneAwareConfig(
            List.of(new ZoneSpec("zone-a", 3, 1000), new ZoneSpec("zone-b", 1, 500)));
    final int newPartitionCount = 4;
    final var result =
        new ClusterPatchRequestTransformer(
                Set.of(newMember),
                Set.of(),
                Optional.of(newPartitionCount),
                Optional.empty(),
                Map.of("zone-a", 3))
            .operations(currentTopology);

    // then operations apply cleanly and produce the expected zone-aware distribution
    assertThat(result).isRight();
    final var operations = result.get();
    Assertions.assertThat(operations.get(0))
        .isInstanceOf(UpdatePartitionDistributorConfigOperation.class);

    final var newTopology = TestTopologyChangeSimulator.apply(currentTopology, operations);
    final var expectedDistribution =
        newConfig
            .toDistributor()
            .distributePartitions(
                Set.of(
                    MemberId.from("zone-a", 0),
                    MemberId.from("zone-a", 1),
                    newMember,
                    MemberId.from("zone-b", 0)),
                getSortedPartitionIds(newPartitionCount),
                4);
    final var newDistribution = ConfigurationUtil.getPartitionDistributionFrom(newTopology, "temp");
    Assertions.assertThat(newDistribution)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedDistribution);
    Assertions.assertThat(newTopology.partitionDistributorConfig()).contains(newConfig);
    Assertions.assertThat(newTopology.partitionCount()).isEqualTo(newPartitionCount);
  }

  private ClusterConfiguration zoneAwareTopology() {
    final var config =
        new ZoneAwareConfig(
            List.of(new ZoneSpec("zone-a", 2, 1000), new ZoneSpec("zone-b", 1, 500)));
    final var members =
        Set.of(MemberId.from("zone-a", 0), MemberId.from("zone-a", 1), MemberId.from("zone-b", 0));
    final var distribution =
        config.toDistributor().distributePartitions(members, getSortedPartitionIds(2), 3);
    return ConfigurationUtil.getClusterConfigFrom(distribution, partitionConfig, "temp")
        .setPartitionDistributorConfig(config);
  }

  private void applyRequestAndVerifyResultingTopology(
      final int oldPartitionCount,
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
                patchRequest.newReplicationFactor())
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
    if (oldPartitionCount == partitionCount) {
      Assertions.assertThat(operations)
          .allSatisfy(
              op ->
                  Assertions.assertThat(op)
                      .isNotInstanceOfAny(
                          ScaleUpOperation.class, PartitionBootstrapOperation.class));
    } else if (partitionCount > oldPartitionCount) {
      final var scaleUpInstances =
          operations.stream()
              .filter(ScaleUpOperation.class::isInstance)
              .map(Object::getClass)
              .collect(Collectors.toSet());
      Assertions.assertThat(scaleUpInstances)
          .isEqualTo(
              Set.of(
                  ScaleUpOperation.StartPartitionScaleUp.class,
                  ScaleUpOperation.AwaitRedistributionCompletion.class,
                  ScaleUpOperation.AwaitRelocationCompletion.class));
    }
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
