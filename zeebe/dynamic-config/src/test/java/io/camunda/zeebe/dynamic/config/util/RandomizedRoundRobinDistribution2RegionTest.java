/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

public class RandomizedRoundRobinDistribution2RegionTest {

  @Property(tries = 10)
  void shouldDistributedPartitionsEquallyIn2Regions(
      @ForAll @IntRange(min = 2, max = 100) final int clusterSizeFactor,
      @ForAll @IntRange(min = 1, max = 200) final int partitionCount) {
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
      partitionIds.add(PartitionId.from("test", i));
    }
    return partitionIds;
  }
}
