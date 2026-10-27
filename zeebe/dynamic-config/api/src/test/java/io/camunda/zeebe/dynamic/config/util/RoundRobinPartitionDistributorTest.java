/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class RoundRobinPartitionDistributorTest {

  @ParameterizedTest(name = "[{index}] {0} {1} {2}")
  @MethodSource("clusterConfig")
  void shouldAssignPrioritiesCorrectly(
      final int clusterSize,
      final int partitionCount,
      final int replicationFactor,
      final int[][] expected) {
    // when
    final var partitionMetadata =
        new RoundRobinPartitionDistributor()
            .distributePartitions(
                getMembers(clusterSize), getSortedPartitionIds(partitionCount), replicationFactor);

    // then
    assertThat(partitionMetadata).isNotEmpty();
    partitionMetadata.forEach(
        metadata -> {
          final int partitionId = metadata.id().number();
          metadata
              .members()
              .forEach(
                  member -> {
                    final int nodeId = Integer.parseInt(member.id());
                    assertThat(metadata.getPriority(member))
                        .describedAs(
                            "Priority calculated wrong for nodeId %d, partition %d. Observed metadata is %s",
                            nodeId, partitionId, metadata)
                        .isEqualTo(expected[nodeId][partitionId - 1]);
                  });
        });
  }

  static Stream<Arguments> clusterConfig() {
    return Stream.of(
        Arguments.of(
            3, // clusterSize
            3, // partitionCount
            3, // replicationFactor
            // Expected partitionDistribution matrix row = nodes, column = partitions
            new int[][] {
              {3, 1, 2},
              {2, 3, 1},
              {1, 2, 3},
            }),
        Arguments.of(
            3,
            6,
            3,
            new int[][] {
              {3, 1, 2, 3, 2, 1},
              {2, 3, 1, 1, 3, 2},
              {1, 2, 3, 2, 1, 3},
            }),
        Arguments.of(
            6,
            3,
            3,
            new int[][] {
              {3, 0, 0},
              {2, 3, 0},
              {1, 2, 3},
              {0, 1, 2},
              {0, 0, 1},
              {0, 0, 0},
            }),
        Arguments.of(
            4,
            12,
            3,
            new int[][] {
              {3, 0, 1, 2, 3, 0, 2, 1, 3, 0, 1, 2},
              {2, 3, 0, 1, 1, 3, 0, 2, 2, 3, 0, 1},
              {1, 2, 3, 0, 2, 1, 3, 0, 1, 2, 3, 0},
              {0, 1, 2, 3, 0, 2, 1, 3, 0, 1, 2, 3},
            }));
  }

  @Test
  void shouldDistributePartitionsAcrossZoneAwareMembers() {
    // given
    final var members = new HashSet<>(ZONE_A_NODES);

    // when
    final var distribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(members, getSortedPartitionIds(3), 3);

    // then — distribution completes without NumberFormatException and covers all partitions
    assertThat(distribution).hasSize(3);
    distribution.forEach(
        metadata -> {
          assertThat(metadata.members()).hasSize(3);
          metadata
              .members()
              .forEach(
                  member -> {
                    final int nodeId = member.nodeIdx();
                    assertThat(metadata.getPriority(member))
                        .describedAs(
                            "Priority should be set for nodeId %d in partition %d",
                            nodeId, metadata.id().number())
                        .isPositive();
                  });
        });
  }

  @Test
  void shouldPreserveRoundRobinSlotsForMixedBareAndZonedMembersWhenZoneOrderIsConfigured() {
    // given
    final var originalMembers = bareNodes(4);
    final var mixedMembers = Set.of(BARE_0, BARE_2, ZONE_B_0, ZONE_B_1);
    final var expectedMapping =
        Map.of(
            BARE_0, BARE_0, // no change
            BARE_1, ZONE_B_0, // to zoned
            BARE_2, BARE_2, // no change
            BARE_3, ZONE_B_1 // to zoned
            );

    final var originalDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(originalMembers, getSortedPartitionIds(4), 4);
    final var mixedDistribution =
        new RoundRobinPartitionDistributor(List.of(ZONE_A, ZONE_B))
            .distributePartitions(mixedMembers, getSortedPartitionIds(4), 4);

    // then
    assertThat(partitionMembers(mixedDistribution))
        .isEqualTo(remapMembers(originalDistribution, expectedMapping));
  }

  @Test
  void shouldPreserveRoundRobinSlotsForFullyZonedMembersWhenZoneOrderIsConfigured() {
    // given
    final var originalMembers =
        Set.of(MemberId.from(0), MemberId.from(1), MemberId.from(2), MemberId.from(3));
    final var zonedMembers = Set.of(ZONE_A_0, ZONE_B_0, ZONE_A_1, ZONE_B_1);
    final var expectedMapping =
        Map.of(
            BARE_0, ZONE_A_0, // to primary
            BARE_1, ZONE_B_0, // to secondary
            BARE_2, ZONE_A_1, // to primary
            BARE_3, ZONE_B_1 // to secondary
            );

    // when
    final var originalDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(originalMembers, getSortedPartitionIds(4), 4);
    final var zonedDistribution =
        new RoundRobinPartitionDistributor(List.of(ZONE_A, ZONE_B))
            .distributePartitions(zonedMembers, getSortedPartitionIds(4), 4);

    // then
    assertThat(partitionMembers(zonedDistribution))
        .isEqualTo(remapMembers(originalDistribution, expectedMapping));
  }

  private Map<Integer, Set<MemberId>> partitionMembers(final Set<PartitionMetadata> distribution) {
    return distribution.stream()
        .collect(
            Collectors.toMap(
                metadata -> metadata.id().number(), metadata -> Set.copyOf(metadata.members())));
  }

  private Map<Integer, Set<MemberId>> remapMembers(
      final Set<PartitionMetadata> distribution, final Map<MemberId, MemberId> mapping) {
    final var remapped = new HashMap<Integer, Set<MemberId>>();
    distribution.forEach(
        metadata ->
            remapped.put(
                metadata.id().number(),
                metadata.members().stream().map(mapping::get).collect(Collectors.toSet())));
    return remapped;
  }

  private Set<MemberId> getMembers(final int nodeCount) {
    final Set<MemberId> members = new HashSet<>();
    for (int i = 0; i < nodeCount; i++) {
      members.add(MemberId.from(i));
    }
    return members;
  }

  private List<PartitionId> getSortedPartitionIds(final int partitionCount) {
    final List<PartitionId> partitionIds = new ArrayList<>(partitionCount);
    for (int i = 1; i <= partitionCount; i++) {
      partitionIds.add(new PartitionId("test", i));
    }
    return partitionIds;
  }
}
