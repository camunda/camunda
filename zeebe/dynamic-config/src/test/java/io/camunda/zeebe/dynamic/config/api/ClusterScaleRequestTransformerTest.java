/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import java.util.HashSet;
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
import org.junit.jupiter.api.Test;

final class ClusterScaleRequestTransformerTest {

  private static final String ZONE_A = "zoneA";
  private static final String ZONE_B = "zoneB";
  private static final int ZONE_B_BROKERS = 1;
  private static final int ZONE_B_REPLICAS = 1;
  private static final int ZONE_A_PRIORITY = 1000;
  private static final int ZONE_B_PRIORITY = 500;
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Property(tries = 10)
  void shouldScaleBrokersWhenPartitionsUnchanged(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 2, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 2, max = 100) final int newClusterSize) {
    shouldScaleBrokersAndPartitionsByCount(
        partitionCount,
        Optional.empty(),
        2,
        Optional.empty(),
        oldClusterSize,
        Optional.of(newClusterSize),
        Optional.empty());
  }

  @Property(tries = 10)
  void shouldScaleBrokersWhenPartitionsUnchangedWhenZoned(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 2, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 2, max = 100) final int newClusterSize) {
    shouldScaleBrokersAndPartitionsByCount(
        partitionCount,
        Optional.empty(),
        2,
        Optional.empty(),
        oldClusterSize,
        Optional.of(newClusterSize),
        Optional.of(ZONE_A));
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
        Optional.empty(),
        Optional.empty());
  }

  @Property(tries = 10)
  void shouldScalePartitionsWhenClusterSizeUnchangedWhenZoned(
      @ForAll @IntRange(min = 3, max = 100) final int clusterSize,
      @ForAll @IntRange(min = 1, max = 10) final int oldPartitionCount,
      @ForAll @IntRange(min = 10, max = 20) final int newPartitionCount) {
    shouldScaleBrokersAndPartitionsByCount(
        oldPartitionCount,
        Optional.of(newPartitionCount),
        3,
        Optional.empty(),
        clusterSize,
        Optional.empty(),
        Optional.of(ZONE_A));
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
        Optional.empty(),
        // replication factor cannot be changed for a zoned cluster
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
        Optional.of(newClusterSize),
        Optional.of(ZONE_A));
  }

  @Property(tries = 10)
  void shouldScaleBrokersAndPartitionsWhenZoned(
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
        Optional.of(newClusterSize),
        Optional.of(ZONE_A));
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
        Optional.of(newClusterSize),
        // replication factor cannot be changed for a zoned cluster
        Optional.empty());
  }

  void shouldScaleBrokersAndPartitionsByCount(
      final int oldPartitionCount,
      final Optional<Integer> newPartitionCount,
      final int replicationFactor,
      final Optional<Integer> newReplicationFactor,
      final int oldClusterSize,
      final Optional<Integer> newClusterSize,
      final Optional<String> zone) {
    // given
    final var effectiveConfig =
        zone.isPresent() ? zoneAwareConfig(replicationFactor) : new RoundRobinConfig();
    final var effectiveNewConfig =
        zone.isPresent()
            ? zoneAwareConfig(newReplicationFactor.orElse(replicationFactor))
            : new RoundRobinConfig();
    final var oldMembers =
        zone.isPresent()
            ? scaledMembers(oldClusterSize)
            : membersInZone(Optional.empty(), oldClusterSize);
    final var newMembers =
        zone.isPresent()
            ? scaledMembers(newClusterSize.orElse(oldClusterSize))
            : membersInZone(Optional.empty(), newClusterSize.orElse(oldClusterSize));

    final var expectedNewDistribution =
        effectiveNewConfig
            .toDistributor()
            .distributePartitions(
                newMembers,
                getSortedPartitionIds(newPartitionCount.orElse(oldPartitionCount)),
                zone.isPresent()
                    ? ((ZoneAwareConfig) effectiveNewConfig).replicationFactor()
                    : newReplicationFactor.orElse(replicationFactor));

    final var oldDistribution =
        effectiveConfig
            .toDistributor()
            .distributePartitions(
                oldMembers,
                getSortedPartitionIds(oldPartitionCount),
                // note that it's not really used for zone-aware
                zone.isPresent()
                    ? ((ZoneAwareConfig) effectiveConfig).replicationFactor()
                    : replicationFactor);
    ClusterConfiguration oldClusterTopology =
        ConfigurationUtil.getClusterConfigFrom(oldDistribution, partitionConfig, "clusterId");
    if (zone.isPresent()) {
      oldClusterTopology = oldClusterTopology.setPartitionDistributorConfig(effectiveConfig);
    }
    for (final MemberId member : oldMembers) {
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
        newMembers,
        patchRequest,
        oldClusterTopology,
        expectedNewDistribution,
        zone);
  }

  private void applyRequestAndVerifyResultingTopology(
      final int partitionCount,
      final Set<MemberId> expectedMembers,
      final ClusterScaleRequest patchRequest,
      final ClusterConfiguration oldClusterTopology,
      final Set<PartitionMetadata> expectedNewDistribution,
      final Optional<String> zone) {

    // when
    final var result =
        new ClusterScaleRequestTransformer(
                patchRequest.brokerCount(),
                patchRequest.newPartitionCount(),
                patchRequest.newReplicationFactor(),
                zone)
            .operations(oldClusterTopology);
    assertThat(result).isRight();
    final var operations = result.get();

    // apply operations to generate new topology
    final ClusterConfiguration newTopology =
        TestTopologyChangeSimulator.apply(oldClusterTopology, operations);

    // then
    final var newDistribution = ConfigurationUtil.getPartitionDistributionFrom(newTopology, "temp");
    assertThat(newDistribution)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedNewDistribution);
    assertThat(newTopology.members().keySet())
        .describedAs("Expected cluster members")
        .containsExactlyInAnyOrderElementsOf(expectedMembers);
    assertThat(newTopology.partitionCount()).isEqualTo(partitionCount);
  }

  private List<PartitionId> getSortedPartitionIds(final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount)
        .mapToObj(id -> new PartitionId("temp", id))
        .collect(Collectors.toList());
  }

  private Set<MemberId> membersInZone(final Optional<String> zone, final int newClusterSize) {
    return IntStream.range(0, newClusterSize)
        .mapToObj(idx -> MemberId.from(zone.orElse(null), idx))
        .collect(Collectors.toSet());
  }

  @Test
  void shouldRejectZoneWithReplicationFactor() {
    // given
    final var topology = zoneAwareTopology(2, 2, 3);

    // when
    final var result =
        new ClusterScaleRequestTransformer(
                Optional.of(3), Optional.empty(), Optional.of(3), Optional.of(ZONE_A))
            .operations(topology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
  }

  @Test
  void shouldRejectUnknownZone() {
    // given
    final var topology = zoneAwareTopology(2, 2, 3);

    // when
    final var result =
        new ClusterScaleRequestTransformer(
                Optional.of(3), Optional.empty(), Optional.empty(), Optional.of("zoneX"))
            .operations(topology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .satisfies(e -> Assertions.assertThat(e).hasMessageContaining("Unknown zone 'zoneX'"));
  }

  @Test
  void shouldRejectNonZoneAwareCluster() {
    // given
    final var distribution =
        new RoundRobinConfig()
            .toDistributor()
            .distributePartitions(
                membersInZone(Optional.of(ZONE_A), 3), getSortedPartitionIds(3), 1);
    final var topology =
        ConfigurationUtil.getClusterConfigFrom(distribution, partitionConfig, "clusterId");

    // when
    final var result =
        new ClusterScaleRequestTransformer(
                Optional.of(4), Optional.empty(), Optional.empty(), Optional.of(ZONE_A))
            .operations(topology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
  }

  private ClusterConfiguration zoneAwareTopology(
      final int zoneABrokers, final int zoneAReplicas, final int partitionCount) {
    final var config = zoneAwareConfig(zoneAReplicas);
    final var members = scaledMembers(zoneABrokers);
    final var distribution =
        config
            .toDistributor()
            .distributePartitions(
                members, getSortedPartitionIds(partitionCount), config.replicationFactor());
    var topology =
        ConfigurationUtil.getClusterConfigFrom(distribution, partitionConfig, "temp")
            .setPartitionDistributorConfig(config);
    for (final MemberId member : members) {
      if (!topology.hasMember(member)) {
        topology = topology.addMember(member, MemberState.initializeAsActive(Map.of()));
      }
    }
    return topology;
  }

  private ZoneAwareConfig zoneAwareConfig(final int zoneAReplicas) {
    return new ZoneAwareConfig(
        List.of(
            new ZoneSpec(ZONE_A, zoneAReplicas, ZONE_A_PRIORITY),
            new ZoneSpec(ZONE_B, ZONE_B_REPLICAS, ZONE_B_PRIORITY)));
  }

  private Set<MemberId> scaledMembers(final int zoneABrokers) {
    final var members = new HashSet<>(membersInZone(Optional.of(ZONE_A), zoneABrokers));
    members.addAll(membersInZone(Optional.of(ZONE_B), ZONE_B_BROKERS));
    return members;
  }
}
