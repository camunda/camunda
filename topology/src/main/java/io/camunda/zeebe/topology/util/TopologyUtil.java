/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.util;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

public final class TopologyUtil {

  public static ClusterTopology getClusterTopologyFrom(
      final Set<PartitionMetadata> partitionDistribution) {
    final var partitionsOwnedByMembers =
        partitionDistribution.stream()
            .flatMap(
                p ->
                    p.members().stream()
                        .map(m -> Map.entry(m, Map.entry(p.id().id(), p.getPriority(m)))))
            .collect(
                Collectors.groupingBy(
                    Entry::getKey,
                    Collectors.toMap(
                        e -> e.getValue().getKey(),
                        e -> PartitionState.active(e.getValue().getValue()))));

    final var memberStates =
        partitionsOwnedByMembers.entrySet().stream()
            .collect(
                Collectors.toMap(Entry::getKey, e -> MemberState.initializeAsActive(e.getValue())));

    return new io.camunda.zeebe.topology.state.ClusterTopology(
        0, memberStates, ClusterChangePlan.empty());
  }

  public static Set<PartitionMetadata> getPartitionDistributionFrom(
      final ClusterTopology clusterTopology, final String groupName) {
    if (clusterTopology.isUninitialized()) {
      throw new IllegalStateException(
          "Cannot generated partition distribution from uninitialized topology");
    }
    final var partitionsToMembersMap =
        clusterTopology.members().entrySet().stream()
            .flatMap(
                memberEntry ->
                    memberEntry.getValue().partitions().entrySet().stream()
                        .map(
                            p ->
                                Map.entry(
                                    p.getKey(),
                                    Map.entry(memberEntry.getKey(), p.getValue().priority()))))
            .collect(
                Collectors.groupingBy(
                    Entry::getKey,
                    Collectors.toMap(e -> e.getValue().getKey(), e -> e.getValue().getValue())));

    return partitionsToMembersMap.entrySet().stream()
        .map(e -> getPartitionMetadata(e, groupName))
        .collect(Collectors.toSet());
  }

  private static PartitionMetadata getPartitionMetadata(
      final Entry<Integer, Map<MemberId, Integer>> e, final String groupName) {
    final Map<MemberId, Integer> memberPriorities = e.getValue();
    final var optionalPrimary = memberPriorities.entrySet().stream().max(Entry.comparingByValue());
    if (optionalPrimary.isEmpty()) {
      throw new IllegalStateException("Found partition with no members");
    }
    return new PartitionMetadata(
        partitionId(e.getKey(), groupName),
        memberPriorities.keySet(),
        memberPriorities,
        optionalPrimary.get().getValue(),
        optionalPrimary.get().getKey());
  }

  private static PartitionId partitionId(final Integer key, final String groupName) {
    return PartitionId.from(groupName, key);
  }
}
