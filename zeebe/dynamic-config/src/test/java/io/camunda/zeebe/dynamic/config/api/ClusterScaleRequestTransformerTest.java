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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;

final class ClusterScaleRequestTransformerTest {

  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final MemberId id2 = MemberId.from("2");
  private final MemberId id3 = MemberId.from("3");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final ZoneAwareConfig initialZoneAwareConfig =
      new ZoneAwareConfig(List.of(new ZoneSpec("zoneA", 2, 2), new ZoneSpec("zoneB", 1, 1)));

  @Property(tries = 10)
  void shouldScaleBrokersWhenPartitionsUnchanged(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 1, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 1, max = 100) final int newClusterSize) {
    shouldScaleBrokersAndPartitionsByCount(
        partitionCount,
        Optional.empty(),
        1,
        Optional.empty(),
        oldClusterSize,
        Optional.of(newClusterSize));
  }

  @Property(tries = 10)
  void shouldScalePartitionsWhenClusterSizeUnchanged(
      @ForAll @IntRange(min = 3, max = 100) final int clusterSize,
      @ForAll @IntRange(min = 1, max = 10) final int oldPartitionCount,
      @ForAll @IntRange(min = 10, max = 20) final int newPartitionCount) {
    shouldScaleBrokersAndPartitionsByCount(
        oldPartitionCount,
        Optional.of(newPartitionCount),
        3,
        Optional.empty(),
        clusterSize,
        Optional.empty());
  }

  @Property(tries = 10)
  void shouldChangeReplicationFactorWhenClusterSizeAndPartitionsUnchanged(
      @ForAll @IntRange(min = 5, max = 10) final int clusterSize,
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 1, max = 5) final int oldReplicationFactor,
      @ForAll @IntRange(min = 1, max = 5) final int newReplicationFactor) {
    shouldScaleBrokersAndPartitionsByCount(
        partitionCount,
        Optional.empty(),
        oldReplicationFactor,
        Optional.of(newReplicationFactor),
        clusterSize,
        Optional.empty());
  }

  @Property(tries = 10)
  void shouldScaleBrokersAndPartitions(
      @ForAll @IntRange(min = 3, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 3, max = 100) final int newClusterSize,
      @ForAll @IntRange(min = 1, max = 10) final int oldPartitionCount,
      @ForAll @IntRange(min = 10, max = 20) final int newPartitionCount) {
    shouldScaleBrokersAndPartitionsByCount(
        oldPartitionCount,
        Optional.of(newPartitionCount),
        3,
        Optional.empty(),
        oldClusterSize,
        Optional.of(newClusterSize));
  }

  @Property(tries = 10)
  void shouldScaleBrokersAndPartitionsAndChangeReplicationFactor(
      @ForAll @IntRange(min = 5, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 5, max = 100) final int newClusterSize,
      @ForAll @IntRange(min = 1, max = 10) final int oldPartitionCount,
      @ForAll @IntRange(min = 10, max = 20) final int newPartitionCount,
      @ForAll @IntRange(min = 1, max = 5) final int oldReplicationFactor,
      @ForAll @IntRange(min = 1, max = 5) final int newReplicationFactor) {
    shouldScaleBrokersAndPartitionsByCount(
        oldPartitionCount,
        Optional.of(newPartitionCount),
        oldReplicationFactor,
        Optional.of(newReplicationFactor),
        oldClusterSize,
        Optional.of(newClusterSize));
  }

  void shouldScaleBrokersAndPartitionsByCount(
      final int oldPartitionCount,
      final Optional<Integer> newPartitionCount,
      final int oldReplicationFactor,
      final Optional<Integer> newReplicationFactor,
      final int oldClusterSize,
      final Optional<Integer> newClusterSize) {
    shouldScaleBrokersAndPartitionsByCount(
        oldPartitionCount,
        newPartitionCount,
        oldReplicationFactor,
        newReplicationFactor,
        oldClusterSize,
        newClusterSize,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  void shouldScaleBrokersAndPartitionsByCount(
      final int oldPartitionCount,
      final Optional<Integer> newPartitionCount,
      final int oldReplicationFactor,
      final Optional<Integer> newReplicationFactor,
      final int oldClusterSize,
      final Optional<Integer> newClusterSize,
      final Optional<PartitionDistributorConfig> distributorConfig,
      final Optional<PartitionDistributorConfig> newDistributorConfig,
      final Optional<String> zone) {
    // given
    final var expectedNewDistribution =
        newDistributorConfig
            .orElse(new RoundRobinConfig())
            .toDistributor()
            .distributePartitions(
                getClusterMembers(newClusterSize.orElse(oldClusterSize)),
                getSortedPartitionIds(newPartitionCount.orElse(oldPartitionCount)),
                newReplicationFactor.orElse(oldReplicationFactor));

    final var oldDistribution =
        distributorConfig
            .orElse(new RoundRobinConfig())
            .toDistributor()
            .distributePartitions(
                getClusterMembers(oldClusterSize),
                getSortedPartitionIds(oldPartitionCount),
                oldReplicationFactor);
    ClusterConfiguration oldClusterTopology =
        ConfigurationUtil.getClusterConfigFrom(oldDistribution, partitionConfig, "clusterId");
    for (final MemberId member : getClusterMembers(oldClusterSize)) {
      if (!oldClusterTopology.hasMember(member)) {
        oldClusterTopology =
            oldClusterTopology.addMember(member, MemberState.initializeAsActive(Map.of()));
      }
    }

    // when
    final var patchRequest =
        new ClusterScaleRequest(
            newClusterSize, newPartitionCount, newReplicationFactor, zone, false);

    applyRequestAndVerifyResultingTopology(
        newPartitionCount.orElse(oldPartitionCount),
        getClusterMembers(newClusterSize.orElse(oldClusterSize)),
        patchRequest,
        oldClusterTopology,
        expectedNewDistribution);
  }

  private void applyRequestAndVerifyResultingTopology(
      final int partitionCount,
      final Set<MemberId> expectedMembers,
      final ClusterScaleRequest patchRequest,
      final ClusterConfiguration oldClusterTopology,
      final Set<PartitionMetadata> expectedNewDistribution) {

    // when
    final var result =
        new ClusterScaleRequestTransformer(
                patchRequest.brokerCount(),
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
