/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static dev.hegel.Generators.integers;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hegel.HegelTest;
import dev.hegel.TestCase;
import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RandomizedRoundRobinDistribution2RegionTest {

  @HegelTest(testCases = 10)
  void shouldDistributedPartitionsEquallyIn2Regions(final TestCase tc) {
    final int clusterSizeFactor = tc.draw(integers().min(2).max(100), "clusterSizeFactor");
    final int partitionCount = tc.draw(integers().min(1).max(200), "partitionCount");
    // cluster size should be always multiple of 2 for a 2-region deployment
    final var clusterSize = 2 * clusterSizeFactor;
    allPartitionHaveTwoReplicasInEachRegion(clusterSize, partitionCount);
  }

  void allPartitionHaveTwoReplicasInEachRegion(final int clusterSize, final int partitionCount) {
    final int replicationFactor = 4;

    final var partitionMetadata =
        new RoundRobinPartitionDistributor()
            .distributePartitions(
                getMembers(clusterSize), getSortedPartitionIds(partitionCount), replicationFactor);

    for (final var p : partitionMetadata) {
      final var members = p.members().stream().map(m -> Integer.parseInt(m.id())).toList();
      assertThat(members.stream().filter(i -> i % 2 == 0).count())
          .describedAs("Partition %s has members %s", p.id(), members)
          .isEqualTo(replicationFactor / 2);
      assertThat(members.stream().filter(i -> i % 2 == 1).count())
          .describedAs("Partition %s has members %s", p.id(), members)
          .isEqualTo(replicationFactor / 2);
    }
  }

  private Set<MemberId> getMembers(final int nodeCount) {
    final Set<MemberId> members = new HashSet<>();
    for (int i = 0; i < nodeCount; i++) {
      members.add(MemberId.from(String.valueOf(i)));
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
