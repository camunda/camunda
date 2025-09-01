/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static java.lang.Math.max;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.EdgeCasesMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.constraints.IntRange;

class PartitionReassignRequestTransformerTest {

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Property(tries = 10)
  void shouldReassignPartitionsWithReplicationFactor1(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 1, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 1, max = 100) final int newClusterSize) {
    shouldReassignPartitionsRoundRobin(partitionCount, 1, oldClusterSize, newClusterSize);
  }

  @Property(tries = 10)
  void shouldReassignPartitionsWithReplicationFactor2(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 2, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 2, max = 100) final int newClusterSize) {
    shouldReassignPartitionsRoundRobin(partitionCount, 2, oldClusterSize, newClusterSize);
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF, edgeCases = EdgeCasesMode.NONE)
  void shouldReassignPartitionsWithReplicationFactor3(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 3, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 3, max = 100) final int newClusterSize) {
    shouldReassignPartitionsRoundRobin(partitionCount, 3, oldClusterSize, newClusterSize);
  }

  @Property(tries = 10)
  void shouldReassignPartitionsWithReplicationFactor4(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 4, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 4, max = 100) final int newClusterSize) {
    shouldReassignPartitionsRoundRobin(partitionCount, 4, oldClusterSize, newClusterSize);
  }

  @Property(tries = 10)
  void shouldChangeReplicationFactor(
      @ForAll @IntRange(min = 10, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 1, max = 6) final int oldReplicationFactor,
      @ForAll @IntRange(min = 1, max = 6) final int newReplicationFactor,
      @ForAll @IntRange(min = 10, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 10, max = 100) final int newClusterSize) {
    shouldReassignPartitionsRoundRobin(
        partitionCount, oldReplicationFactor, newReplicationFactor, oldClusterSize, newClusterSize);
  }

  @Property(tries = 10)
  void shouldAddNewPartitions(
      @ForAll @IntRange(min = 10, max = 20) final int oldPartitionCount,
      @ForAll @IntRange(min = 21, max = 30) final int newPartitionCount,
      @ForAll @IntRange(min = 1, max = 6) final int oldReplicationFactor,
      @ForAll @IntRange(min = 1, max = 6) final int newReplicationFactor,
      @ForAll @IntRange(min = 10, max = 30) final int oldClusterSize,
      @ForAll @IntRange(min = 10, max = 30) final int newClusterSize) {
    shouldReassignPartitionsRoundRobin(
        oldPartitionCount,
        newPartitionCount,
        oldReplicationFactor,
        newReplicationFactor,
        oldClusterSize,
        newClusterSize);
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

  void shouldFailIfClusterSizeLessThanReplicationFactor(
      final int partitionCount,
      final int replicationFactor,
      final int oldClusterSize,
      final int newClusterSize) {
    // given
    final var oldDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(
                getClusterMembers(oldClusterSize),
                getSortedPartitionIds(partitionCount),
                replicationFactor);
    var oldClusterTopology =
        ConfigurationUtil.getClusterConfigFrom(true, oldDistribution, partitionConfig, "clusterId");
    for (int i = 0; i < max(oldClusterSize, newClusterSize); i++) {
      oldClusterTopology =
          oldClusterTopology.updateMember(
              MemberId.from(Integer.toString(i)),
              currentState ->
                  Objects.requireNonNullElseGet(
                      currentState, () -> MemberState.initializeAsActive(Map.of())));
    }

    //  when
    final var operationsEither =
        new PartitionReassignRequestTransformer(getClusterMembers(newClusterSize))
            .operations(oldClusterTopology);

    // then
    EitherAssert.assertThat(operationsEither)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
  }

  void shouldReassignPartitionsRoundRobin(
      final int partitionCount,
      final int replicationFactor,
      final int oldClusterSize,
      final int newClusterSize) {
    shouldReassignPartitionsRoundRobin(
        partitionCount, replicationFactor, replicationFactor, oldClusterSize, newClusterSize);
  }

  void shouldReassignPartitionsRoundRobin(
      final int partitionCount,
      final int oldReplicationFactor,
      final int newReplicationFactor,
      final int oldClusterSize,
      final int newClusterSize) {
    shouldReassignPartitionsRoundRobin(
        partitionCount,
        partitionCount,
        oldReplicationFactor,
        newReplicationFactor,
        oldClusterSize,
        newClusterSize);
  }

  void shouldReassignPartitionsRoundRobin(
      final int oldPartitionCount,
      final int newPartitionCount,
      final int oldReplicationFactor,
      final int newReplicationFactor,
      final int oldClusterSize,
      final int newClusterSize) {
    // given
    final var expectedNewDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(
                getClusterMembers(newClusterSize),
                getSortedPartitionIds(newPartitionCount),
                newReplicationFactor);

    final var oldDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(
                getClusterMembers(oldClusterSize),
                getSortedPartitionIds(oldPartitionCount),
                oldReplicationFactor);
    var oldClusterTopology =
        ConfigurationUtil.getClusterConfigFrom(true, oldDistribution, partitionConfig, "clusterId");
    for (int i = 0; i < max(oldClusterSize, newClusterSize); i++) {
      oldClusterTopology =
          oldClusterTopology.updateMember(
              MemberId.from(Integer.toString(i)),
              currentState ->
                  Objects.requireNonNullElseGet(
                      currentState, () -> MemberState.initializeAsActive(Map.of())));
    }

    // when
    final var request =
        new PartitionReassignRequestTransformer(
            getClusterMembers(newClusterSize),
            Optional.of(newReplicationFactor),
            Optional.of(newPartitionCount));
    final var operations = request.operations(oldClusterTopology).get();

    // apply operations to generate new topology
    final ClusterConfiguration newTopology =
        TestTopologyChangeSimulator.apply(oldClusterTopology, operations);
    // then
    final var newDistribution = ConfigurationUtil.getPartitionDistributionFrom(newTopology, "temp");
    assertThat(newDistribution).isEqualTo(expectedNewDistribution);
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
