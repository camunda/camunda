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
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for {@link AdditivePartitionReassigner}, in the style of {@code
 * PartitionReassignRequestTransformerTest}: for randomly generated valid inputs, the resulting
 * distribution must satisfy a set of invariants regardless of the specific numbers involved.
 *
 * <p>Since {@link AdditivePartitionReassigner} only ever adds new partitions on top of an unchanged
 * broker set and replication factor, the "current" distribution fed into each property is itself
 * built by the same reassigner starting from an empty cluster — this guarantees it's already
 * balanced, which is a precondition the implementation relies on (it never touches existing
 * partitions, so it can't fix up an already-skewed starting point).
 */
final class AdditivePartitionReassignerPropertyTest {

  private static final String GROUP = "test";

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final AdditivePartitionReassigner reassigner = new AdditivePartitionReassigner();

  @Property(tries = 50)
  void shouldSatisfyDistributionInvariantsWhenAddingPartitions(
      @ForAll @IntRange(min = 1, max = 4) final int replicationFactor,
      @ForAll @IntRange(min = 0, max = 10) final int extraMembers,
      @ForAll @IntRange(min = 1, max = 30) final int oldPartitionCount,
      @ForAll @IntRange(min = 0, max = 20) final int additionalPartitionCount) {
    final int clusterSize = replicationFactor + extraMembers;
    final Set<MemberId> targetMembers = members(clusterSize);
    final int newPartitionCount = oldPartitionCount + additionalPartitionCount;

    // given — an already-balanced starting distribution, built by the reassigner itself from an
    // empty cluster (the only kind of starting point this implementation can guarantee to keep
    // balanced, since it never moves an existing partition)
    final var oldDistribution =
        reassigner.reassignPartitions(
            configurationWith(targetMembers, Set.of()),
            targetMembers,
            partitionIds(1, oldPartitionCount),
            replicationFactor);
    final var currentConfiguration = configurationWith(targetMembers, oldDistribution);
    final var targetPartitionIds = partitionIds(1, newPartitionCount);

    // when
    final var result =
        reassigner.reassignPartitions(
            currentConfiguration, targetMembers, targetPartitionIds, replicationFactor);

    // then — leader-count balance is checked with a looser tolerance than replica-count balance,
    // since AdditivePartitionReassigner's "never touch an existing partition" guarantee can make a
    // tight gap of 1 mathematically unachievable; see
    // PartitionDistributionInvariants.assertSatisfied
    PartitionDistributionInvariants.assertSatisfied(
        result, targetMembers, targetPartitionIds, replicationFactor, 3);

    assertThat(result)
        .describedAs("No existing partition was moved or removed")
        .containsAll(oldDistribution);
  }

  private CurrentClusterConfiguration configurationWith(
      final Set<MemberId> targetMembers, final Set<PartitionMetadata> distribution) {
    final var tenantConfigs =
        distribution.stream()
            .map(p -> p.id().group())
            .distinct()
            .collect(Collectors.toMap(Function.identity(), p -> partitionConfig));
    return ConfigurationUtil.getCurrentClusterConfigurationFrom(
        targetMembers, distribution, tenantConfigs, "clusterId");
  }

  private Set<MemberId> members(final int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> MemberId.from(String.valueOf(i)))
        .collect(Collectors.toSet());
  }

  private List<PartitionId> partitionIds(final int fromInclusive, final int toInclusive) {
    return IntStream.rangeClosed(fromInclusive, toInclusive)
        .mapToObj(i -> new PartitionId(GROUP, i))
        .toList();
  }
}
