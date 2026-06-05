/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.dynamic.config.PartitionDistributor;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.dynamic.config.util.ZoneAwarePartitionDistributor;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.EdgeCasesMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.AssertionsForInterfaceTypes;

class ScaleRequestTransformerTest {

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Property(tries = 10)
  void shouldScaleAndReassignWithReplicationFactor1(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 1, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 1, max = 100) final int newClusterSize) {
    shouldScaleAndReassign(partitionCount, 1, oldClusterSize, newClusterSize);
  }

  @Property(tries = 10)
  void shouldScaleAndReassignWithReplicationFactor2(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 2, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 2, max = 100) final int newClusterSize) {
    shouldScaleAndReassign(partitionCount, 2, oldClusterSize, newClusterSize);
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF, edgeCases = EdgeCasesMode.NONE)
  void shouldScaleAndReassignWithReplicationFactor3(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 3, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 3, max = 100) final int newClusterSize) {
    shouldScaleAndReassign(partitionCount, 3, oldClusterSize, newClusterSize);
  }

  @Property(tries = 10)
  void shouldScaleAndReassignWithReplicationFactor4(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 4, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 4, max = 100) final int newClusterSize) {
    shouldScaleAndReassign(partitionCount, 4, oldClusterSize, newClusterSize);
  }

  @Property
  void shouldFailIfClusterSizeLessThanReplicationFactor3(
      @ForAll @IntRange(min = 0, max = 2) final int newClusterSize) {
    shouldFailIfClusterSizeLessThanReplicationFactor(3, 3, 3, newClusterSize);
  }

  @Property
  void shouldFailIfClusterSizeLessThanReplicationFactor4(
      @ForAll @IntRange(min = 0, max = 3) final int newClusterSize) {
    shouldFailIfClusterSizeLessThanReplicationFactor(12, 4, 6, newClusterSize);
  }

  @Property
  void shouldFailIfDesiredPartitionCountIsLessThanNewPartitions(
      @ForAll @IntRange(min = 1, max = 100) final int currentPartitionCount,
      @ForAll @IntRange(min = 1, max = 100) final int desiredPartitionCount) {
    scaleUpWithValidation(currentPartitionCount, desiredPartitionCount, null);
  }

  @Property
  void shouldGenerateScaleUpOperationForAllPartition1Members(
      @ForAll @IntRange(min = 1, max = 100) final int currentPartitionCount,
      @ForAll @IntRange(min = 1, max = 100) final int newPartitionCount) {
    final var desiredPartitionCount = currentPartitionCount + newPartitionCount;
    scaleUpWithValidation(
        currentPartitionCount,
        desiredPartitionCount,
        operations -> {
          final var lowestMemberId = MemberId.from("1");
          final var newPartitions =
              partitionsInRange(currentPartitionCount + 1, desiredPartitionCount + 1);
          AssertionsForInterfaceTypes.assertThat(
                  operations.stream().filter(ScaleUpOperation.class::isInstance))
              .isEqualTo(
                  List.of(
                      new StartPartitionScaleUp(lowestMemberId, desiredPartitionCount),
                      new AwaitRedistributionCompletion(
                          lowestMemberId, desiredPartitionCount, newPartitions),
                      new AwaitRelocationCompletion(
                          lowestMemberId, desiredPartitionCount, newPartitions)));
        });
  }

  void shouldFailIfClusterSizeLessThanReplicationFactor(
      final int partitionCount,
      final int replicationFactor,
      final int oldClusterSize,
      final int newClusterSize) {
    shouldFailIfClusterSizeLessThanReplicationFactor(
        partitionCount,
        replicationFactor,
        RoundRobinPartitionDistributor::new,
        getClusterMembers(oldClusterSize),
        getClusterMembers(newClusterSize));
  }

  void shouldFailIfClusterSizeLessThanReplicationFactor(
      final int partitionCount,
      final int replicationFactor,
      final Supplier<PartitionDistributor> distributor,
      final Set<MemberId> oldMembers,
      final Set<MemberId> newMembers) {
    // given
    final var oldDistribution =
        distributor
            .get()
            .distributePartitions(
                oldMembers, getSortedPartitionIds(partitionCount), replicationFactor);
    final var oldClusterTopology =
        ConfigurationUtil.getClusterConfigFrom(oldDistribution, partitionConfig, "clusterId");

    // when
    final var operationsEither =
        new ScaleRequestTransformer(getClusterMembers(newClusterSize))
            .operations(oldClusterTopology);

    // then
    EitherAssert.assertThat(operationsEither)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
  }

  void shouldScaleAndReassign(
      final int partitionCount,
      final int replicationFactor,
      final int oldClusterSize,
      final int newClusterSize) {
    shouldScaleAndReassign(
        partitionCount,
        replicationFactor,
        RoundRobinPartitionDistributor::new,
        getClusterMembers(oldClusterSize),
        getClusterMembers(newClusterSize));
  }

  void shouldScaleAndReassign(
      final int partitionCount,
      final int replicationFactor,
      final Supplier<PartitionDistributor> distributor,
      final Set<MemberId> oldMembers,
      final Set<MemberId> newMembers) {
    // given
    final var expectedNewDistribution =
        distributor
            .get()
            .distributePartitions(
                newMembers, getSortedPartitionIds(partitionCount), replicationFactor);

    final var oldDistribution =
        distributor
            .get()
            .distributePartitions(
                oldMembers, getSortedPartitionIds(partitionCount), replicationFactor);
    final var oldClusterTopology =
        ConfigurationUtil.getClusterConfigFrom(oldDistribution, partitionConfig, "clusterId");

    // when
    final var operations =
        new ScaleRequestTransformer(getClusterMembers(newClusterSize))
            .operations(oldClusterTopology)
            .get();

    // apply operations to generate new topology
    final ClusterConfiguration newTopology =
        TestTopologyChangeSimulator.apply(oldClusterTopology, operations);

    // then
    final var newDistribution = ConfigurationUtil.getPartitionDistributionFrom(newTopology, "temp");
    assertThat(newDistribution).isEqualTo(expectedNewDistribution);
    assertThat(newTopology.members().keySet()).containsExactlyInAnyOrderElementsOf(newMembers);
  }

  @org.junit.jupiter.api.Test
  void shouldScaleOutZoneAwareCluster() {
    // given: 2 zones, RF=3 (zone-a contributes 2 replicas, zone-b contributes 1)
    final var zoneSpecs = List.of(new ZoneSpec("zone-a", 2, 1000), new ZoneSpec("zone-b", 1, 500));
    final Supplier<PartitionDistributor> distributor =
        () -> new ZoneAwarePartitionDistributor(zoneSpecs);

    // old cluster: 2 zone-a brokers + 1 zone-b broker
    final var oldMembers =
        Set.of(MemberId.from("zone-a", 0), MemberId.from("zone-a", 1), MemberId.from("zone-b", 0));

    // new cluster: 4 zone-a brokers + 2 zone-b brokers
    final var newMembers =
        Set.of(
            MemberId.from("zone-a", 0),
            MemberId.from("zone-a", 1),
            MemberId.from("zone-a", 2),
            MemberId.from("zone-a", 3),
            MemberId.from("zone-b", 0),
            MemberId.from("zone-b", 1));

    shouldScaleAndReassign(3, 3, distributor, oldMembers, newMembers);
  }

  @org.junit.jupiter.api.Test
  void shouldScaleInZoneAwareCluster() {
    // given: 2 zones, RF=3 (zone-a contributes 2 replicas, zone-b contributes 1)
    final var zoneSpecs = List.of(new ZoneSpec("zone-a", 2, 1000), new ZoneSpec("zone-b", 1, 500));
    final Supplier<PartitionDistributor> distributor =
        () -> new ZoneAwarePartitionDistributor(zoneSpecs);

    // old cluster: 4 zone-a brokers + 2 zone-b brokers
    final var oldMembers =
        Set.of(
            MemberId.from("zone-a", 0),
            MemberId.from("zone-a", 1),
            MemberId.from("zone-a", 2),
            MemberId.from("zone-a", 3),
            MemberId.from("zone-b", 0),
            MemberId.from("zone-b", 1));

    // new cluster: 2 zone-a brokers + 1 zone-b broker (minimal)
    final var newMembers =
        Set.of(MemberId.from("zone-a", 0), MemberId.from("zone-a", 1), MemberId.from("zone-b", 0));

    shouldScaleAndReassign(3, 3, distributor, oldMembers, newMembers);
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

  public void scaleUpWithValidation(
      final int currentPartitionCount,
      final int desiredPartitionCount,
      final Consumer<List<ClusterConfigurationChangeOperation>> whenRight) {
    final var clusterSize = 3;
    final var replicationFactor = 3;
    final var distribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(
                IntStream.range(1, clusterSize)
                    .mapToObj(id -> MemberId.from(Integer.toString(id)))
                    .collect(Collectors.toSet()),
                partitionsInRange(1, 1 + currentPartitionCount).stream()
                    .map(i -> PartitionId.from("temp", i))
                    .toList(),
                replicationFactor);
    final var config =
        ConfigurationUtil.getClusterConfigFrom(distribution, partitionConfig, "clusterId");
    final var transformer =
        new ScaleRequestTransformer(
            getClusterMembers(clusterSize), Optional.of(3), Optional.of(desiredPartitionCount));
    final var operations = transformer.operations(config);
    if (desiredPartitionCount < currentPartitionCount) {
      EitherAssert.assertThat(operations).isLeft();
    } else {
      EitherAssert.assertThat(operations).right();
      if (whenRight != null) {
        whenRight.accept(operations.get());
      }
    }
  }

  SortedSet<Integer> partitionsInRange(final int from, final int to) {
    return new TreeSet<>(IntStream.range(from, to).boxed().toList());
  }
}
