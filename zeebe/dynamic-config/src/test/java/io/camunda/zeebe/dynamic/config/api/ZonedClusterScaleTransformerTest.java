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
import io.camunda.zeebe.dynamic.config.RoutingStateInitializer;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdatePartitionDistributorConfigOperation;
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
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Property-based tests for scaling a single zone of a zone-aware cluster. The cluster size is the
 * target broker count within the named zone; other zones are preserved. A new replication factor is
 * the named zone's replica count.
 */
final class ZonedClusterScaleTransformerTest {

  private static final String ZONE_A = "zoneA";
  private static final String ZONE_B = "zoneB";
  private static final int ZONE_A_PRIORITY = 1000;
  private static final int ZONE_B_PRIORITY = 500;
  // zoneB is held fixed at one broker with one replica throughout.
  private static final int ZONE_B_BROKERS = 1;
  private static final int ZONE_B_REPLICAS = 1;

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Property(tries = 10)
  void shouldScaleBrokersInZoneWhenPartitionsUnchanged(
      @ForAll @IntRange(min = 2, max = 6) final int oldZoneABrokers,
      @ForAll @IntRange(min = 2, max = 6) final int newZoneABrokers) {
    scaleZoneAndVerify(
        oldZoneABrokers, Optional.of(newZoneABrokers), 2, Optional.empty(), 3, Optional.empty());
  }

  @Property(tries = 10)
  void shouldScalePartitionsInZoneWhenClusterSizeUnchanged(
      @ForAll @IntRange(min = 2, max = 6) final int zoneABrokers,
      @ForAll @IntRange(min = 1, max = 4) final int oldPartitionCount,
      @ForAll @IntRange(min = 4, max = 8) final int newPartitionCount) {
    scaleZoneAndVerify(
        zoneABrokers,
        Optional.empty(),
        2,
        Optional.empty(),
        oldPartitionCount,
        Optional.of(newPartitionCount));
  }

  @Property(tries = 10)
  void shouldScaleBrokersAndPartitionsInZone(
      @ForAll @IntRange(min = 2, max = 6) final int oldZoneABrokers,
      @ForAll @IntRange(min = 2, max = 6) final int newZoneABrokers,
      @ForAll @IntRange(min = 1, max = 4) final int oldPartitionCount,
      @ForAll @IntRange(min = 4, max = 8) final int newPartitionCount) {
    scaleZoneAndVerify(
        oldZoneABrokers,
        Optional.of(newZoneABrokers),
        2,
        Optional.empty(),
        oldPartitionCount,
        Optional.of(newPartitionCount));
  }

  @Property(tries = 10)
  void shouldScaleZoneAndChangeZoneReplicationFactor(
      @ForAll @IntRange(min = 2, max = 6) final int newZoneABrokers,
      @ForAll @IntRange(min = 1, max = 6) final int newZoneAReplicas) {
    // the new replication factor cannot exceed the number of brokers in the zone
    Assume.that(newZoneAReplicas <= newZoneABrokers);
    scaleZoneAndVerify(
        2, Optional.of(newZoneABrokers), 2, Optional.of(newZoneAReplicas), 3, Optional.empty());
  }

  @Test
  void shouldRejectUnknownZone() {
    // given a zone-aware cluster with zoneA and zoneB
    final var topology = zoneAwareTopology(2, 2, 3);

    // when scaling a zone that does not exist
    final var result =
        new ZonedClusterScaleTransformer(
                Optional.of(3), Optional.empty(), Optional.empty(), "zoneX")
            .operations(topology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(InvalidRequest.class)
        .satisfies(e -> assertThat(e).hasMessageContaining("Unknown zone 'zoneX'"));
  }

  @Test
  void shouldRejectNonZoneAwareCluster() {
    // given a plain (non-zone-aware) cluster
    final var members = getZonedMembers(ZONE_A, 3);
    final var distribution =
        new RoundRobinConfig()
            .toDistributor()
            .distributePartitions(members, getSortedPartitionIds(3), 1);
    final var topology =
        ConfigurationUtil.getClusterConfigFrom(distribution, partitionConfig, "temp");

    // when scaling with a zone
    final var result =
        new ZonedClusterScaleTransformer(Optional.of(4), Optional.empty(), Optional.empty(), ZONE_A)
            .operations(topology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(InvalidRequest.class)
        .satisfies(e -> assertThat(e).hasMessageContaining("zone-aware"));
  }

  private void scaleZoneAndVerify(
      final int oldZoneABrokers,
      final Optional<Integer> newZoneABrokers,
      final int zoneAReplicas,
      final Optional<Integer> newZoneAReplicas,
      final int oldPartitionCount,
      final Optional<Integer> newPartitionCount) {
    // given a zone-aware cluster: zoneA (oldZoneABrokers brokers, zoneAReplicas) + zoneB (1, 1)
    var topology = zoneAwareTopology(oldZoneABrokers, zoneAReplicas, oldPartitionCount);
    topology = new RoutingStateInitializer(oldPartitionCount).modify(topology).join();

    final var expectedConfig =
        newZoneAReplicas.map(rf -> zoneAwareConfig(rf)).orElse(zoneAwareConfig(zoneAReplicas));
    final var expectedMembers = scaledMembers(newZoneABrokers.orElse(oldZoneABrokers));
    final var expectedDistribution =
        expectedConfig
            .toDistributor()
            .distributePartitions(
                expectedMembers,
                getSortedPartitionIds(newPartitionCount.orElse(oldPartitionCount)),
                expectedConfig.replicationFactor());

    // when scaling zoneA
    final var result =
        new ZonedClusterScaleTransformer(
                newZoneABrokers, newPartitionCount, newZoneAReplicas, ZONE_A)
            .operations(topology);

    // then
    assertThat(result).isRight();
    final var operations = result.get();
    if (newZoneAReplicas.isPresent()) {
      Assertions.assertThat(operations.get(0))
          .isInstanceOf(UpdatePartitionDistributorConfigOperation.class);
    }

    final var newTopology = TestTopologyChangeSimulator.apply(topology, operations);
    final var newDistribution = ConfigurationUtil.getPartitionDistributionFrom(newTopology, "temp");
    Assertions.assertThat(newDistribution)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedDistribution);
    Assertions.assertThat(newTopology.members().keySet())
        .describedAs("Expected cluster members")
        .containsExactlyInAnyOrderElementsOf(expectedMembers);
    Assertions.assertThat(newTopology.partitionCount())
        .isEqualTo(newPartitionCount.orElse(oldPartitionCount));
    Assertions.assertThat(newTopology.partitionDistributorConfig()).contains(expectedConfig);
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
    // getClusterConfigFrom only records members that hold a partition; ensure every configured
    // broker is a member so the zone's broker count is accurate.
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

  /** zoneA scaled to {@code zoneABrokers}, zoneB held fixed at one broker. */
  private Set<MemberId> scaledMembers(final int zoneABrokers) {
    final var members = new HashSet<>(getZonedMembers(ZONE_A, zoneABrokers));
    members.addAll(getZonedMembers(ZONE_B, ZONE_B_BROKERS));
    return members;
  }

  private Set<MemberId> getZonedMembers(final String zone, final int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> MemberId.from(zone, i))
        .collect(Collectors.toSet());
  }

  private List<PartitionId> getSortedPartitionIds(final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount)
        .mapToObj(id -> PartitionId.from("temp", id))
        .collect(Collectors.toList());
  }
}
