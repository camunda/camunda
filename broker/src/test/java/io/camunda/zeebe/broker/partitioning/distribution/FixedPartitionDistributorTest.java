/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class FixedPartitionDistributorTest {
  private static final String PARTITION_GROUP_NAME = "group";

  @Test
  void shouldFailOnMissingPartition() {
    // given
    final var distributor =
        new FixedPartitionDistributorBuilder(PARTITION_GROUP_NAME).assignMember(2, 0, 1).build();
    final var clusterMembers = Set.of(node(0));
    final var sortedPartitionIds = List.of(partition(1), partition(2));

    // when - then
    assertThatCode(() -> distributor.distributePartitions(clusterMembers, sortedPartitionIds, 1))
        .as("should fail because partition 1 exists, but was not configured")
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Expected to distribute partition 1, but no members configured for it");
  }

  @Test
  void shouldFailOnUnknownMember() {
    // given
    final var distributor =
        new FixedPartitionDistributorBuilder(PARTITION_GROUP_NAME).assignMember(1, 0, 1).build();
    final var clusterMembers = Set.of(node(1));
    final var sortedPartitionIds = List.of(partition(1), partition(2));

    // when - then
    assertThatCode(() -> distributor.distributePartitions(clusterMembers, sortedPartitionIds, 1))
        .as("should fail because node 0 is not part of the cluster members, only node 1 is")
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Expected partition 1 to be replicated across a cluster made of members [1], but the "
                + "following configured members [0] are not part of the cluster");
  }

  @Test
  void shouldFailOnMissingReplica() {
    // given
    final var distributor =
        new FixedPartitionDistributorBuilder(PARTITION_GROUP_NAME).assignMember(1, 0, 1).build();
    final var clusterMembers = Set.of(node(0));
    final var sortedPartitionIds = List.of(partition(1), partition(2));

    // when - then
    assertThatCode(() -> distributor.distributePartitions(clusterMembers, sortedPartitionIds, 2))
        .as("should fail because only one replica, 0, is specified, and 1 is missing")
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Expected each partition to be replicated across exactly 2 members, but partition 1 is "
                + "replicated across members [0]");
  }

  @Test
  void shouldDistributeEvenly() {
    // given
    final var expectedDistribution =
        Set.of(
            new PartitionMetadata(
                partition(1), List.of(node(0), node(1)), Map.of(node(0), 1, node(1), 2), 2),
            new PartitionMetadata(
                partition(2), List.of(node(0), node(1)), Map.of(node(0), 2, node(1), 1), 2));
    final var distributor =
        new FixedPartitionDistributorBuilder(PARTITION_GROUP_NAME)
            .assignMember(1, 0, 1)
            .assignMember(1, 1, 2)
            .assignMember(2, 0, 2)
            .assignMember(2, 1, 1)
            .build();
    final var clusterMembers = Set.of(node(0), node(1));
    final var sortedPartitionIds = List.of(partition(1), partition(2));

    // when
    final var distribution =
        distributor.distributePartitions(clusterMembers, sortedPartitionIds, 2);

    // then
    assertThat(distribution)
        .as("should distribute the partitions as expected")
        .containsExactlyInAnyOrderElementsOf(expectedDistribution);
  }

  @Test
  void shouldDistributeUnevenly() {
    // given
    final var expectedDistribution =
        Set.of(
            new PartitionMetadata(partition(1), List.of(node(0)), Map.of(node(0), 2), 2),
            new PartitionMetadata(partition(2), List.of(node(0)), Map.of(node(0), 2), 2));
    final var distributor =
        new FixedPartitionDistributorBuilder(PARTITION_GROUP_NAME)
            .assignMember(1, 0, 2)
            .assignMember(2, 0, 2)
            .build();
    final var clusterMembers = Set.of(node(0), node(1));
    final var sortedPartitionIds = List.of(partition(1), partition(2));

    // when
    final var distribution =
        distributor.distributePartitions(clusterMembers, sortedPartitionIds, 1);

    // then
    assertThat(distribution)
        .as("should distribute the partitions as expected")
        .containsExactlyInAnyOrderElementsOf(expectedDistribution);
  }

  private PartitionId partition(final int id) {
    return PartitionId.from(PARTITION_GROUP_NAME, id);
  }

  private MemberId node(final int id) {
    return MemberId.from(String.valueOf(id));
  }
}
