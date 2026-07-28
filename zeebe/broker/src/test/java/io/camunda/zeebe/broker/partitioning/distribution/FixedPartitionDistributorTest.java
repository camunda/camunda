/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
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
        new FixedPartitionDistributorBuilder().assignMember(partition(2), 0, 1).build();
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
        new FixedPartitionDistributorBuilder().assignMember(partition(1), 0, 1).build();
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
        new FixedPartitionDistributorBuilder().assignMember(partition(1), 0, 1).build();
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
                partition(1), Set.of(node(0), node(1)), Map.of(node(0), 1, node(1), 2), 2, node(1)),
            new PartitionMetadata(
                partition(2),
                Set.of(node(0), node(1)),
                Map.of(node(0), 2, node(1), 1),
                2,
                node(0)));
    final var distributor =
        new FixedPartitionDistributorBuilder()
            .assignMember(partition(1), 0, 1)
            .assignMember(partition(1), 1, 2)
            .assignMember(partition(2), 0, 2)
            .assignMember(partition(2), 1, 1)
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
            new PartitionMetadata(partition(1), Set.of(node(0)), Map.of(node(0), 2), 2, node(0)),
            new PartitionMetadata(partition(2), Set.of(node(0)), Map.of(node(0), 2), 2, node(0)));
    final var distributor =
        new FixedPartitionDistributorBuilder()
            .assignMember(partition(1), 0, 2)
            .assignMember(partition(2), 0, 2)
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

  @Test
  void shouldNotAssignPrimaryIfMoreThanOnePotentialPrimary() {
    // given
    final var expectedDistribution =
        Set.of(
            // expect a partition without assigned primary
            new PartitionMetadata(
                partition(1), Set.of(node(0), node(1)), Map.of(node(0), 2, node(1), 2), 2, null));
    final var distributor =
        new FixedPartitionDistributorBuilder()
            // two members with the same priority
            .assignMember(partition(1), 0, 2)
            .assignMember(partition(1), 1, 2)
            .build();
    final var clusterMembers = Set.of(node(0), node(1));
    final var sortedPartitionIds = List.of(partition(1));

    // when
    final var distribution =
        distributor.distributePartitions(clusterMembers, sortedPartitionIds, 2);

    // then
    assertThat(distribution)
        .as("should distribute the partitions as expected")
        .containsExactlyInAnyOrderElementsOf(expectedDistribution);
  }

  @Test
  void shouldDistributePartitionsIndependentlyAcrossMultipleTenants() {
    // given -- two tenants, each with a partition numbered 1, assigned to different members
    final var expectedDistribution =
        Set.of(
            new PartitionMetadata(
                partition("tenanta", 1), Set.of(node(0)), Map.of(node(0), 1), 1, node(0)),
            new PartitionMetadata(
                partition("tenantb", 1), Set.of(node(1)), Map.of(node(1), 1), 1, node(1)));
    final var distributor =
        new FixedPartitionDistributorBuilder()
            .assignMember(partition("tenanta", 1), 0, 1)
            .assignMember(partition("tenantb", 1), 1, 1)
            .build();
    final var clusterMembers = Set.of(node(0), node(1));
    final var sortedPartitionIds = List.of(partition("tenanta", 1), partition("tenantb", 1));

    // when
    final var distribution =
        distributor.distributePartitions(clusterMembers, sortedPartitionIds, 1);

    // then -- each tenant's partition is distributed to its own configured members only
    assertThat(distribution)
        .as("should distribute each tenant's partitions independently")
        .containsExactlyInAnyOrderElementsOf(expectedDistribution);
  }

  @Test
  void shouldDistributeSamePartitionNumberDifferentlyPerTenant() {
    // given -- tenant A and tenant B both configure partition 1 and 2, but with different
    // members and priorities per tenant
    final var expectedDistribution =
        Set.of(
            new PartitionMetadata(
                partition("tenantA", 1),
                Set.of(node(0), node(1)),
                Map.of(node(0), 1, node(1), 2),
                2,
                node(1)),
            new PartitionMetadata(
                partition("tenantA", 2),
                Set.of(node(0), node(1)),
                Map.of(node(0), 2, node(1), 1),
                2,
                node(0)),
            new PartitionMetadata(
                partition("tenantB", 1),
                Set.of(node(2), node(3)),
                Map.of(node(2), 2, node(3), 1),
                2,
                node(2)),
            new PartitionMetadata(
                partition("tenantB", 2),
                Set.of(node(2), node(3)),
                Map.of(node(2), 1, node(3), 2),
                2,
                node(3)));
    final var distributor =
        new FixedPartitionDistributorBuilder()
            .assignMember(partition("tenantA", 1), 0, 1)
            .assignMember(partition("tenantA", 1), 1, 2)
            .assignMember(partition("tenantA", 2), 0, 2)
            .assignMember(partition("tenantA", 2), 1, 1)
            .assignMember(partition("tenantB", 1), 2, 2)
            .assignMember(partition("tenantB", 1), 3, 1)
            .assignMember(partition("tenantB", 2), 2, 1)
            .assignMember(partition("tenantB", 2), 3, 2)
            .build();
    final var clusterMembers = Set.of(node(0), node(1), node(2), node(3));
    final var sortedPartitionIds =
        List.of(
            partition("tenantA", 1),
            partition("tenantA", 2),
            partition("tenantB", 1),
            partition("tenantB", 2));

    // when
    final var distribution =
        distributor.distributePartitions(clusterMembers, sortedPartitionIds, 2);

    // then
    assertThat(distribution)
        .as("should distribute the partitions of each tenant according to its own configuration")
        .containsExactlyInAnyOrderElementsOf(expectedDistribution);
  }

  @Test
  void shouldFailOnMissingPartitionWhenOnlyConfiguredForAnotherTenant() {
    // given -- partition 1 is only configured for tenantA, but tenantB also requests partition 1
    final var distributor =
        new FixedPartitionDistributorBuilder().assignMember(partition("tenantA", 1), 0, 1).build();
    final var clusterMembers = Set.of(node(0));
    final var sortedPartitionIds = List.of(partition("tenantA", 1), partition("tenantB", 1));

    // when - then
    assertThatCode(() -> distributor.distributePartitions(clusterMembers, sortedPartitionIds, 1))
        .as("should fail because tenantB's partition 1 was not configured")
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Expected to distribute partition 1, but no members configured for it");
  }

  private PartitionId partition(final int id) {
    return new PartitionId(PARTITION_GROUP_NAME, id);
  }

  private PartitionId partition(final String tenant, final int id) {
    return new PartitionId(tenant, id);
  }

  private MemberId node(final int id) {
    return MemberId.from(String.valueOf(id));
  }
}
