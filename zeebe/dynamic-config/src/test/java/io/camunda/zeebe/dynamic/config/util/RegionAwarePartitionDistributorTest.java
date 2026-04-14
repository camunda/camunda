/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.dynamic.config.util.RegionAwarePartitionDistributor.RegionSpec;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class RegionAwarePartitionDistributorTest {

  private static final String GROUP = "raft";

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static List<MemberId> brokers(final String region, final int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> MemberId.from(region + "-" + i))
        .toList();
  }

  private static List<PartitionId> partitions(final int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(i -> PartitionId.from(GROUP, i))
        .sorted()
        .toList();
  }

  private static Set<MemberId> allBrokers(final List<RegionSpec> specs) {
    return specs.stream()
        .flatMap(s -> s.brokers().stream())
        .collect(java.util.stream.Collectors.toSet());
  }

  private static PartitionMetadata partitionById(
      final Set<PartitionMetadata> result, final int id) {
    return result.stream()
        .filter(p -> p.id().id() == id)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Partition " + id + " not found"));
  }

  // -------------------------------------------------------------------------
  // Core correctness
  // -------------------------------------------------------------------------

  @Test
  void shouldAssignExactlyRFMembersPerPartition() {
    // given
    final var specs =
        List.of(
            new RegionSpec("us-east1", 2, 1000, brokers("us-east1", 2)),
            new RegionSpec("us-west1", 2, 500, brokers("us-west1", 2)),
            new RegionSpec("euro-east1", 1, 10, brokers("euro-east1", 1)));
    final int rf = 5;
    final var distributor = new RegionAwarePartitionDistributor(specs);

    // when
    final var result = distributor.distributePartitions(allBrokers(specs), partitions(5), rf);

    // then
    assertThat(result).hasSize(5);
    result.forEach(p -> assertThat(p.members()).hasSize(rf));
  }

  @Test
  void shouldAssignAllPartitions() {
    // given
    final var specs =
        List.of(
            new RegionSpec("us-east1", 2, 1000, brokers("us-east1", 2)),
            new RegionSpec("us-west1", 1, 500, brokers("us-west1", 1)));
    final var distributor = new RegionAwarePartitionDistributor(specs);
    final var partitionIds = partitions(4);

    // when
    final var result = distributor.distributePartitions(allBrokers(specs), partitionIds, 3);

    // then — every partition ID appears exactly once
    final var assignedIds = result.stream().map(p -> p.id().id()).sorted().toList();
    assertThat(assignedIds).containsExactly(1, 2, 3, 4);
  }

  @Test
  void shouldNotAssignDuplicateBrokersToSamePartition() {
    // given
    final var specs =
        List.of(
            new RegionSpec("us-east1", 2, 1000, brokers("us-east1", 2)),
            new RegionSpec("us-west1", 2, 500, brokers("us-west1", 2)));
    final var distributor = new RegionAwarePartitionDistributor(specs);

    // when
    final var result = distributor.distributePartitions(allBrokers(specs), partitions(5), 4);

    // then — Set<MemberId>.size() == members().size() iff no duplicates
    result.forEach(
        p ->
            assertThat(p.members())
                .as("partition %d has no duplicate members", p.id().id())
                .doesNotHaveDuplicates());
  }

  // -------------------------------------------------------------------------
  // Leader placement: highest-priority region should own the leaders
  // -------------------------------------------------------------------------

  @Test
  void shouldPlaceLeaderInHighestPriorityRegion() {
    // given — regions given in low→high order to verify the sort inside the constructor
    final var specs =
        List.of(
            new RegionSpec("euro-east1", 1, 10, brokers("euro-east1", 1)),
            new RegionSpec("us-west1", 1, 500, brokers("us-west1", 1)),
            new RegionSpec("us-east1", 1, 1000, brokers("us-east1", 1)));
    final var distributor = new RegionAwarePartitionDistributor(specs);

    // when
    final var result = distributor.distributePartitions(allBrokers(specs), partitions(3), 3);

    // then — primary of every partition must be a broker from us-east1
    final var primaryRegion = "us-east1";
    result.forEach(
        p ->
            assertThat(p.getPrimary())
                .isPresent()
                .get()
                .matches(
                    m -> m.id().startsWith(primaryRegion),
                    "primary should be in region " + primaryRegion));
  }

  @Test
  void shouldAssignHighestRaftPriorityToHighestPriorityRegion() {
    // given
    final int rf = 5;
    final var specs =
        List.of(
            new RegionSpec("us-east1", 2, 1000, brokers("us-east1", 2)),
            new RegionSpec("us-west1", 2, 500, brokers("us-west1", 2)),
            new RegionSpec("euro-east1", 1, 10, brokers("euro-east1", 1)));
    final var distributor = new RegionAwarePartitionDistributor(specs);

    // when
    final var result = distributor.distributePartitions(allBrokers(specs), partitions(1), rf);

    // then — us-east1 brokers must have the two highest priorities (5 and 4)
    final var p1 = partitionById(result, 1);
    final var eastPriorities =
        specs.get(0).brokers().stream().mapToInt(p1::getPriority).boxed().toList();
    assertThat(eastPriorities).containsExactlyInAnyOrder(rf, rf - 1);

    // us-west1 brokers have the next two (3 and 2)
    final var westPriorities =
        specs.get(1).brokers().stream().mapToInt(p1::getPriority).boxed().toList();
    assertThat(westPriorities).containsExactlyInAnyOrder(rf - 2, rf - 3);

    // euro-east1 broker has the lowest priority (1)
    final var euroPriority = p1.getPriority(specs.get(2).brokers().getFirst());
    assertThat(euroPriority).isEqualTo(1);
  }

  @Test
  void shouldSetTargetPriorityToReplicationFactor() {
    // given
    final int rf = 3;
    final var specs =
        List.of(
            new RegionSpec("us-east1", 2, 1000, brokers("us-east1", 2)),
            new RegionSpec("us-west1", 1, 500, brokers("us-west1", 1)));
    final var distributor = new RegionAwarePartitionDistributor(specs);

    // when
    final var result = distributor.distributePartitions(allBrokers(specs), partitions(3), rf);

    // then
    result.forEach(p -> assertThat(p.getTargetPriority()).isEqualTo(rf));
  }

  // -------------------------------------------------------------------------
  // Round-robin within each region
  // -------------------------------------------------------------------------

  @Test
  void shouldRoundRobinBrokersWithinRegionAcrossPartitions() {
    // given — one region, 2 brokers, 1 replica per partition
    final var specs =
        List.of(new RegionSpec("us-east1", 1, 1000, brokers("us-east1", 2)));
    final var distributor = new RegionAwarePartitionDistributor(specs);

    // when
    final var result = distributor.distributePartitions(allBrokers(specs), partitions(4), 1);

    // then — partitions 1,3 → us-east1-0; partitions 2,4 → us-east1-1 (or vice versa)
    final var p1Primary = partitionById(result, 1).getPrimary().orElseThrow();
    final var p2Primary = partitionById(result, 2).getPrimary().orElseThrow();
    final var p3Primary = partitionById(result, 3).getPrimary().orElseThrow();
    final var p4Primary = partitionById(result, 4).getPrimary().orElseThrow();

    assertThat(p1Primary).isEqualTo(p3Primary); // same broker every 2 partitions
    assertThat(p2Primary).isEqualTo(p4Primary);
    assertThat(p1Primary).isNotEqualTo(p2Primary); // alternates between brokers
  }

  @Test
  void shouldShiftStartingBrokerPerPartitionWithinRegion() {
    // given — us-east1 has 2 brokers and 2 replicas per partition
    final var specs =
        List.of(
            new RegionSpec("us-east1", 2, 1000, brokers("us-east1", 2)),
            new RegionSpec("us-west1", 1, 500, brokers("us-west1", 1)));
    final var distributor = new RegionAwarePartitionDistributor(specs);

    // when
    final var result = distributor.distributePartitions(allBrokers(specs), partitions(2), 3);

    // then — for partition 1 the highest-priority east broker is us-east1-0,
    //        for partition 2 it is us-east1-1 (offset shifts by 1)
    final var p1 = partitionById(result, 1);
    final var p2 = partitionById(result, 2);

    assertThat(p1.getPrimary().orElseThrow()).isEqualTo(MemberId.from("us-east1-0"));
    assertThat(p2.getPrimary().orElseThrow()).isEqualTo(MemberId.from("us-east1-1"));
  }

  // -------------------------------------------------------------------------
  // Degenerate cases
  // -------------------------------------------------------------------------

  @Test
  void shouldBehaveCorrectlyWithSingleRegion() {
    // given
    final var specs =
        List.of(new RegionSpec("us-east1", 3, 1000, brokers("us-east1", 3)));
    final var distributor = new RegionAwarePartitionDistributor(specs);

    // when
    final var result = distributor.distributePartitions(allBrokers(specs), partitions(3), 3);

    // then — every partition has 3 members, all from us-east1
    result.forEach(
        p -> {
          assertThat(p.members()).hasSize(3);
          p.members().forEach(m -> assertThat(m.id()).startsWith("us-east1-"));
        });
  }

  // -------------------------------------------------------------------------
  // Guard / validation
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowWhenReplicaSumDoesNotMatchReplicationFactor() {
    // given — 2 + 1 = 3 replicas but RF = 4
    final var specs =
        List.of(
            new RegionSpec("us-east1", 2, 1000, brokers("us-east1", 2)),
            new RegionSpec("us-west1", 1, 500, brokers("us-west1", 1)));
    final var distributor = new RegionAwarePartitionDistributor(specs);

    // when / then
    assertThatThrownBy(
            () -> distributor.distributePartitions(allBrokers(specs), partitions(1), 4))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("replicationFactor");
  }

  @Test
  void shouldThrowWhenBrokerNotInClusterMembers() {
    // given — spec declares 2 brokers but we only pass 1 to distributePartitions
    final var specs =
        List.of(new RegionSpec("us-east1", 2, 1000, brokers("us-east1", 2)));
    final var distributor = new RegionAwarePartitionDistributor(specs);
    final Set<MemberId> onlyOneBroker = Set.of(MemberId.from("us-east1-0"));

    // when / then
    assertThatThrownBy(
            () -> distributor.distributePartitions(onlyOneBroker, partitions(1), 2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("us-east1-1");
  }
}
