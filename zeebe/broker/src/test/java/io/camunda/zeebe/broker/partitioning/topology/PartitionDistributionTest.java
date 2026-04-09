/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PartitionDistributionTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");

  @Test
  void withGroupNameReturnsEqualDistributionWhenGroupMatches() {
    // given
    final var distribution = distributionInGroup("default");

    // when
    final var copy = distribution.withGroupName("default");

    // then
    assertThat(copy.partitions()).isEqualTo(distribution.partitions());
  }

  @Test
  void withGroupNameRewritesPartitionIdsButPreservesMembersAndPriorities() {
    // given
    final var original = distributionInGroup("default");

    // when
    final var renamed = original.withGroupName("engine-2");

    // then — every partition id uses the new group, numeric id is preserved
    assertThat(renamed.partitions()).hasSameSizeAs(original.partitions());
    for (final PartitionMetadata renamedPartition : renamed.partitions()) {
      assertThat(renamedPartition.id().group()).isEqualTo("engine-2");

      final var originalPartition =
          original.partitions().stream()
              .filter(p -> p.id().id().equals(renamedPartition.id().id()))
              .findFirst()
              .orElseThrow();

      assertThat(renamedPartition.members()).containsExactlyInAnyOrderElementsOf(
          originalPartition.members());
      assertThat(renamedPartition.getTargetPriority())
          .isEqualTo(originalPartition.getTargetPriority());
      assertThat(renamedPartition.getPrimary()).isEqualTo(originalPartition.getPrimary());
      for (final var member : originalPartition.members()) {
        assertThat(renamedPartition.getPriority(member))
            .isEqualTo(originalPartition.getPriority(member));
      }
    }
  }

  @Test
  void withGroupNameDoesNotMutateOriginalDistribution() {
    // given
    final var original = distributionInGroup("default");

    // when
    original.withGroupName("engine-2");

    // then — original partition ids are untouched
    assertThat(original.partitions()).allSatisfy(p ->
        assertThat(p.id().group()).isEqualTo("default"));
  }

  private static PartitionDistribution distributionInGroup(final String group) {
    final var partition1 =
        new PartitionMetadata(
            PartitionId.from(group, 1),
            Set.of(MEMBER_0, MEMBER_1, MEMBER_2),
            Map.of(MEMBER_0, 3, MEMBER_1, 2, MEMBER_2, 1),
            3,
            MEMBER_0);
    final var partition2 =
        new PartitionMetadata(
            PartitionId.from(group, 2),
            Set.of(MEMBER_0, MEMBER_1),
            Map.of(MEMBER_0, 1, MEMBER_1, 2),
            2,
            MEMBER_1);
    return new PartitionDistribution(Set.of(partition1, partition2));
  }
}
