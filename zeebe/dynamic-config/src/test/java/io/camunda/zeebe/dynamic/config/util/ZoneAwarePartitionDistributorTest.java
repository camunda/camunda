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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ZoneAwarePartitionDistributorTest {

  private static final Logger LOG =
      LoggerFactory.getLogger(ZoneAwarePartitionDistributorTest.class);
  private static final String GROUP = "raft";
  private static final TestConfig TWO_ZONES =
      new TestConfig(
          List.of(new ZoneSpec("us-east1", 2, 1000), new ZoneSpec("us-west1", 1, 500)),
          3,
          union(membersOf("us-east1", 2), membersOf("us-west1", 1)));
  private static final TestConfig THREE_ZONES =
      new TestConfig(
          List.of(
              new ZoneSpec("us-east1", 2, 1000),
              new ZoneSpec("us-west1", 2, 500),
              new ZoneSpec("eu-east1", 1, 10)),
          5,
          union(membersOf("us-east1", 2), membersOf("us-west1", 2), membersOf("eu-east1", 1)));

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static Set<MemberId> membersOf(final String zone, final int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> MemberId.from(zone, i))
        .collect(Collectors.toUnmodifiableSet());
  }

  @SafeVarargs
  private static Set<MemberId> union(final Set<MemberId>... sets) {
    return Arrays.stream(sets).flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
  }

  private static List<PartitionId> partitions(final int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(i -> PartitionId.from(GROUP, i))
        .sorted()
        .toList();
  }

  private static PartitionMetadata partitionById(
      final Set<PartitionMetadata> result, final int id) {
    return result.stream()
        .filter(p -> p.id().id() == id)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Partition " + id + " not found"));
  }

  // -------------------------------------------------------------------------
  // Parameterized configurations: 2-zone (RF=4) and 3-zone (RF=5)
  // -------------------------------------------------------------------------

  private static Stream<Arguments> twoAndThreeZoneConfigs() {
    return Stream.of(Arguments.of(TWO_ZONES), Arguments.of(THREE_ZONES));
  }

  // -------------------------------------------------------------------------
  // Core correctness
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @MethodSource("twoAndThreeZoneConfigs")
  void shouldAssignPartitionsCorrectly(final TestConfig config) {
    // given
    final var distributor = new ZoneAwarePartitionDistributor(config.specs());

    // when
    final var result =
        distributor.distributePartitions(
            config.clusterMembers(), partitions(5), config.replicationFactor());

    // then
    assertThat(result.stream().map(PartitionMetadata::id).map(PartitionId::id))
        .containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    assertThat(result)
        .hasSize(5)
        .allSatisfy(
            p -> {
              assertThat(p.getPrimary()).isPresent();
              assertThat(p.getTargetPriority()).isPositive();
              assertThat(p.members()).allSatisfy(m -> assertThat(p.getPriority(m)).isPositive());
              assertThat(p.members()).doesNotHaveDuplicates().hasSize(config.replicationFactor());
            });
  }

  @ParameterizedTest
  @MethodSource("twoAndThreeZoneConfigs")
  void shouldPlaceLeaderInHighestPriorityRegion(final TestConfig config) {
    // given — us-east1 is highest-priority in both configurations
    final var distributor = new ZoneAwarePartitionDistributor(config.specs());

    // when
    final var result =
        distributor.distributePartitions(
            config.clusterMembers(), partitions(3), config.replicationFactor());

    // then — primary of every partition must be a broker from us-east1
    result.forEach(
        p ->
            assertThat(p.getPrimary())
                .isPresent()
                .get()
                .matches(m -> m.isInZone("us-east1"), "primary should be in us-east1"));
  }

  @ParameterizedTest
  @MethodSource("twoAndThreeZoneConfigs")
  void shouldSetTargetPriorityToReplicationFactor(final TestConfig config) {
    // given
    final var distributor = new ZoneAwarePartitionDistributor(config.specs());

    // when
    final var result =
        distributor.distributePartitions(
            config.clusterMembers(), partitions(3), config.replicationFactor());

    // then
    result.forEach(p -> assertThat(p.getTargetPriority()).isEqualTo(config.replicationFactor()));
  }

  @Test
  void shouldAssignHighestRaftPriorityToHighestPriorityRegion() {
    // given
    final var distributor = new ZoneAwarePartitionDistributor(THREE_ZONES.specs());

    // when
    final var result =
        distributor.distributePartitions(
            THREE_ZONES.clusterMembers(), partitions(1), THREE_ZONES.replicationFactor());

    // then — us-east1 brokers must have the two highest priorities (5 and 4)
    final var p1 = partitionById(result, 1);
    final var eastBrokers = membersOf("us-east1", 2);
    final var eastPriorities = eastBrokers.stream().mapToInt(p1::getPriority).boxed().toList();
    assertThat(eastPriorities)
        .containsExactlyInAnyOrder(
            THREE_ZONES.replicationFactor(), THREE_ZONES.replicationFactor() - 1);

    // us-west1 brokers have the next two (3 and 2)
    final var westBrokers = membersOf("us-west1", 2);
    final var westPriorities = westBrokers.stream().mapToInt(p1::getPriority).boxed().toList();
    assertThat(westPriorities)
        .containsExactlyInAnyOrder(
            THREE_ZONES.replicationFactor() - 2, THREE_ZONES.replicationFactor() - 3);

    // eu-east1 broker has the lowest priority (1)
    assertThat(p1.getPriority(MemberId.from("eu-east1", 0))).isEqualTo(1);
  }

  @Test
  void shouldBeEqualToRoundRobinForSingleRegion() {
    // given — one region, 2 brokers, 1 replica per partition
    final var specs = List.of(new ZoneSpec("us-east1", 3, 1000));
    final var clusterMembers = membersOf("us-east1", 3);
    final var distributor = new ZoneAwarePartitionDistributor(specs);

    // when
    final var result = distributor.distributePartitions(clusterMembers, partitions(3), 3);

    // then — partitions 1,3 → us-east1_0; partitions 2,4 → us-east1_1 (or vice versa)
    final var rrResult =
        new RoundRobinPartitionDistributor().distributePartitions(clusterMembers, partitions(3), 3);

    assertThat(result).isEqualTo(rrResult);
  }

  // -------------------------------------------------------------------------
  // Round-robin within each region
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @MethodSource("twoAndThreeZoneConfigs")
  void shouldShiftStartingBrokerPerPartitionWithinRegion(final TestConfig config) {
    // given — us-east1 has 2 brokers and 2 replicas per partition in both configs
    final var distributor = new ZoneAwarePartitionDistributor(config.specs());

    // when
    final var result =
        distributor.distributePartitions(
            config.clusterMembers(), partitions(2), config.replicationFactor());

    // then — for partition 1 the highest-priority east broker is us-east1_0,
    //        for partition 2 it is us-east1_1 (offset shifts by 1)
    final var p1 = partitionById(result, 1);
    final var p2 = partitionById(result, 2);

    assertThat(p1.getPrimary().orElseThrow()).isEqualTo(MemberId.from("us-east1", 0));
    assertThat(p2.getPrimary().orElseThrow()).isEqualTo(MemberId.from("us-east1", 1));

    // us-west1_0 is always assigned regardless of zone size or round-robin offset
    assertThat(p1.members()).contains(MemberId.from("us-west1", 0));
    assertThat(p2.members()).contains(MemberId.from("us-west1", 0));
  }

  // -------------------------------------------------------------------------
  // Degenerate cases
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowWhenZoneNameIsEmpty() {
    assertThatThrownBy(() -> new ZoneSpec("", 1, 1000))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void shouldThrowWhenZonesAreNotUnique() {
    final var zones = Stream.of("zoneA", "zoneA").map(name -> new ZoneSpec(name, 1, 1000)).toList();
    assertThatThrownBy(() -> new ZoneAwarePartitionDistributor(zones))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("zone names to be unique");
  }

  // -------------------------------------------------------------------------
  // ZoneSpec construction validation
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(ints = {-100, -1, 0})
  void shouldThrowWhenNumberOfReplicasIsNotPositive(final int numberOfReplicas) {
    assertThatThrownBy(() -> new ZoneSpec("us-east1", numberOfReplicas, 1000))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("numberOfReplicas");
  }

  @ParameterizedTest
  @ValueSource(ints = {-100, -1, 0})
  void shouldThrowWhenPriorityIsNotPositive(final int priority) {
    assertThatThrownBy(() -> new ZoneSpec("us-east1", 1, priority))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("priority");
  }

  @Test
  void shouldThrowWhenMemberHasNoZone() {
    // given — bare member id (no zone)
    final var specs = List.of(new ZoneSpec("us-east1", 1, 1000));
    final var distributor = new ZoneAwarePartitionDistributor(specs);
    final Set<MemberId> bareMembers = Set.of(MemberId.from("0"));

    // when / then
    assertThatThrownBy(() -> distributor.distributePartitions(bareMembers, partitions(1), 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no zone");
  }

  // -------------------------------------------------------------------------
  // Guard / validation
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @MethodSource("twoAndThreeZoneConfigs")
  void shouldThrowWhenReplicaSumDoesNotMatchReplicationFactor(final TestConfig config) {
    // given — passing RF != config.replicationFactor() should fail
    final var distributor = new ZoneAwarePartitionDistributor(config.specs());

    // when / then
    assertThatThrownBy(
            () ->
                distributor.distributePartitions(
                    config.clusterMembers(), partitions(1), config.replicationFactor() + 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("replicationFactor");
  }

  @Test
  void shouldThrowWhenZoneHasFewerBrokersThanReplicas() {
    // given — zone declares 3 replicas but clusterMembers only has 2 in that zone
    final var specs = List.of(new ZoneSpec("us-east1", 3, 1000));
    final var distributor = new ZoneAwarePartitionDistributor(specs);
    final var clusterMembers = membersOf("us-east1", 2);

    // when / then
    assertThatThrownBy(() -> distributor.distributePartitions(clusterMembers, partitions(1), 3))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("us-east1");
  }

  @ParameterizedTest
  @MethodSource("twoAndThreeZoneConfigs")
  void shouldMatchExactlyPriorityTable(final TestConfig config) {
    // given
    final var distributor = new ZoneAwarePartitionDistributor(config.specs());

    // when
    final var result =
        distributor.distributePartitions(
            config.clusterMembers(), partitions(5), config.replicationFactor());

    // then — table is printed to the logs; this test acts as a visual smoke-check
    final var table = priorityTable(result, config.specs(), config.clusterMembers());
    final var expected =
        Map.of(
            2,
"""
Partition | us-east1_0 | us-east1_1 | us-west1_0
----------|------------|------------|-----------
        1 |     3      |     2      |     1    \s
        2 |     2      |     3      |     1    \s
        3 |     3      |     2      |     1    \s
        4 |     2      |     3      |     1    \s
        5 |     3      |     2      |     1    \s
""",
            3,
"""
Partition | us-east1_0 | us-east1_1 | us-west1_0 | us-west1_1 | eu-east1_0
----------|------------|------------|------------|------------|-----------
        1 |     5      |     4      |     3      |     2      |     1
        2 |     4      |     5      |     2      |     3      |     1
        3 |     5      |     4      |     3      |     2      |     1
        4 |     4      |     5      |     2      |     3      |     1
        5 |     5      |     4      |     3      |     2      |     1
""");
    assertThat(table).isEqualToIgnoringWhitespace(expected.get(config.specs.size()));
  }

  @Test
  void shouldDistributeEvenlyAcrossZonesWhenPrioritiesAreIdentical() {
    // given - all zones have same priorities
    final var specs = TWO_ZONES.specs.stream().map(z -> z.withPriority(1)).toList();
    final var distributor = new ZoneAwarePartitionDistributor(specs);

    // when
    final var result =
        distributor.distributePartitions(TWO_ZONES.clusterMembers(), partitions(3), 3);

    // then
    final var priorityTable = priorityTable(result, specs, TWO_ZONES.clusterMembers());
    assertThat(priorityTable)
        .isEqualToIgnoringWhitespace(
"""
Partition | us-east1_0 | us-east1_1 | us-west1_0
----------|------------|------------|-----------
        1 |     3      |     1      |     2
        2 |     1      |     2      |     3
        3 |     2      |     3      |     1
""");
  }

  // -------------------------------------------------------------------------
  // Priority table visualization
  // -------------------------------------------------------------------------

  /**
   * Logs a Markdown-style table showing the Raft election priority of every broker for every
   * partition. Columns are ordered by zone (highest priority first, matching spec order) then by
   * local broker index. The broker with priority {@code targetPriority} (== replicationFactor) is
   * the preferred leader for that partition.
   */
  private static String priorityTable(
      final Set<PartitionMetadata> result,
      final List<ZoneSpec> specs,
      final Set<MemberId> clusterMembers) {
    // Columns: zones in spec order (already sorted by priority desc), brokers within each zone
    // sorted by nodeIdx
    final List<MemberId> columns =
        specs.stream()
            .<MemberId>flatMap(
                s ->
                    clusterMembers.stream()
                        .filter(m -> m.isInZone(s.name()))
                        .sorted(Comparator.comparingInt(MemberId::nodeIdx)))
            .toList();

    // Sort partitions by numeric id for stable row order
    final List<PartitionMetadata> rows =
        result.stream().sorted((a, b) -> Integer.compare(a.id().id(), b.id().id())).toList();

    // Compute column widths
    final int partitionColWidth =
        Math.max(
            "Partition".length(),
            rows.stream().mapToInt(p -> String.valueOf(p.id().id()).length()).max().orElse(1));
    final int[] brokerColWidths =
        columns.stream().mapToInt(m -> Math.max(m.id().length(), 3)).toArray();

    // Header row
    final var header = new StringBuilder();
    header.append(padLeft("Partition", partitionColWidth));
    for (int c = 0; c < columns.size(); c++) {
      header.append(" | ").append(center(columns.get(c).id(), brokerColWidths[c]));
    }

    // Separator row
    final var separator = new StringBuilder();
    separator.repeat("-", partitionColWidth);
    for (final int w : brokerColWidths) {
      separator.append("-|-").repeat("-", w);
    }

    // Data rows
    final var dataRows = new StringBuilder();
    for (final PartitionMetadata pm : rows) {
      final var row = new StringBuilder();
      row.append(padLeft(String.valueOf(pm.id().id()), partitionColWidth));
      for (int c = 0; c < columns.size(); c++) {
        final int priority = pm.getPriority(columns.get(c));
        row.append(" | ").append(center(String.valueOf(priority), brokerColWidths[c]));
      }
      dataRows.append('\n').append(row);
    }

    return String.valueOf(header) + '\n' + separator + '\n' + dataRows;
  }

  private static String padLeft(final String s, final int width) {
    return " ".repeat(Math.max(0, width - s.length())) + s;
  }

  private static String center(final String s, final int width) {
    final int padding = Math.max(0, width - s.length());
    final int left = padding / 2;
    final int right = padding - left;
    return " ".repeat(left) + s + " ".repeat(right);
  }

  private record TestConfig(
      List<ZoneSpec> specs, int replicationFactor, Set<MemberId> clusterMembers) {}
}
