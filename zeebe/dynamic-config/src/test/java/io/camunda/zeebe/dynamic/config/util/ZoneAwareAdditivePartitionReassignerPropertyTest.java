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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for {@link ZoneAwareAdditivePartitionReassigner}, in the style of {@link
 * AdditivePartitionReassignerPropertyTest}: for randomly generated valid zone configurations and
 * inputs, the resulting distribution must satisfy a set of invariants regardless of the specific
 * numbers involved.
 *
 * <p>Since {@link ZoneAwareAdditivePartitionReassigner} only ever adds new partitions on top of an
 * unchanged broker set, replication factor, and zone configuration, the "current" distribution fed
 * into each property is itself built by the same reassigner starting from an empty cluster — this
 * guarantees it already satisfies the zone spec, which is a precondition the implementation relies
 * on (it never touches an existing partition, so it can't fix up an already-invalid starting
 * point).
 *
 * <p>Zone configurations are generated with 2 or 3 zones, each with a per-zone replication factor
 * (number of replicas) between 1 and 4, and some number of extra brokers beyond that minimum.
 * {@code topZonesTied} occasionally makes the two highest-priority zones share the same priority,
 * exercising the primary-spreading behavior for that case as well as the single-top-zone case.
 */
final class ZoneAwareAdditivePartitionReassignerPropertyTest {

  private static final String GROUP = "test";

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Property(tries = 100)
  void shouldSatisfyDistributionInvariantsWhenAddingPartitions(
      @ForAll @IntRange(min = 2, max = 3) final int zoneCount,
      @ForAll @IntRange(min = 1, max = 4) final int zoneAReplicas,
      @ForAll @IntRange(min = 1, max = 4) final int zoneBReplicas,
      @ForAll @IntRange(min = 1, max = 4) final int zoneCReplicas,
      @ForAll @IntRange(min = 0, max = 4) final int zoneAExtraBrokers,
      @ForAll @IntRange(min = 0, max = 4) final int zoneBExtraBrokers,
      @ForAll @IntRange(min = 0, max = 4) final int zoneCExtraBrokers,
      @ForAll final boolean topZonesTied,
      @ForAll @IntRange(min = 1, max = 15) final int oldPartitionCount,
      @ForAll @IntRange(min = 0, max = 15) final int additionalPartitionCount) {
    final List<ZoneSpec> zoneSpecs =
        buildZoneSpecs(zoneCount, zoneAReplicas, zoneBReplicas, zoneCReplicas, topZonesTied);
    final int replicationFactor = zoneSpecs.stream().mapToInt(ZoneSpec::numberOfReplicas).sum();
    final Set<MemberId> targetMembers =
        buildTargetMembers(
            zoneSpecs,
            List.of(
                zoneAReplicas + zoneAExtraBrokers,
                zoneBReplicas + zoneBExtraBrokers,
                zoneCReplicas + zoneCExtraBrokers));
    final int newPartitionCount = oldPartitionCount + additionalPartitionCount;
    final var reassigner = new ZoneAwareAdditivePartitionReassigner(zoneSpecs);

    // given — an already zone-spec-compliant starting distribution, built by the reassigner
    // itself from an empty cluster (the only kind of starting point this implementation can
    // guarantee stays valid, since it never touches an existing partition)
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

    // then — leader-count (primary) balance is checked with a looser tolerance than replica
    // placement, since primary selection for a partition is restricted to whichever members were
    // already chosen as that partition's replicas in the top zone(s); see
    // ZonePartitionDistributionInvariants.assertSatisfied
    final int topPriority = zoneSpecs.stream().mapToInt(ZoneSpec::priority).max().orElseThrow();
    final int topZoneReplicas =
        zoneSpecs.stream()
            .filter(spec -> spec.priority() == topPriority)
            .mapToInt(ZoneSpec::numberOfReplicas)
            .sum();
    ZonePartitionDistributionInvariants.assertSatisfied(
        result,
        targetMembers,
        targetPartitionIds,
        zoneSpecs,
        replicationFactor,
        topZoneReplicas + 3);

    assertThat(result)
        .describedAs("No existing partition was moved or removed")
        .containsAll(oldDistribution);
  }

  private List<ZoneSpec> buildZoneSpecs(
      final int zoneCount,
      final int zoneAReplicas,
      final int zoneBReplicas,
      final int zoneCReplicas,
      final boolean topZonesTied) {
    final int zoneAPriority = 300;
    final int zoneBPriority = topZonesTied ? 300 : 200;
    final var specs = new ArrayList<ZoneSpec>();
    specs.add(new ZoneSpec("zone-a", zoneAReplicas, zoneAPriority));
    specs.add(new ZoneSpec("zone-b", zoneBReplicas, zoneBPriority));
    if (zoneCount == 3) {
      specs.add(new ZoneSpec("zone-c", zoneCReplicas, 100));
    }
    return specs;
  }

  private Set<MemberId> buildTargetMembers(
      final List<ZoneSpec> zoneSpecs, final List<Integer> membersPerZone) {
    final Set<MemberId> members = new HashSet<>();
    for (int z = 0; z < zoneSpecs.size(); z++) {
      final var zoneName = zoneSpecs.get(z).name();
      for (int i = 0; i < membersPerZone.get(z); i++) {
        members.add(MemberId.from(zoneName, i));
      }
    }
    return members;
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

  private List<PartitionId> partitionIds(final int fromInclusive, final int toInclusive) {
    return IntStream.rangeClosed(fromInclusive, toInclusive)
        .mapToObj(i -> new PartitionId(GROUP, i))
        .toList();
  }
}
