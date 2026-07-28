/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class ConfigurationUtil {

  private ConfigurationUtil() {}

  public static ClusterConfiguration getClusterConfigFrom(
      final Set<PartitionMetadata> partitionDistribution,
      final DynamicPartitionConfig partitionConfig,
      @Nullable final String clusterId) {
    final var partitionStatesByMember = new HashMap<MemberId, Map<Integer, PartitionState>>();
    for (final var partitionMetadata : partitionDistribution) {
      final var partitionId = partitionMetadata.id().number();
      for (final var member : partitionMetadata.members()) {
        final var memberPriority = partitionMetadata.getPriority(member);
        partitionStatesByMember
            .computeIfAbsent(member, k -> new HashMap<>())
            .put(partitionId, PartitionState.active(memberPriority, partitionConfig));
      }
    }
    final var memberStates = new HashMap<MemberId, MemberState>();
    for (final var e : partitionStatesByMember.entrySet()) {
      memberStates.put(e.getKey(), MemberState.initializeAsActive(e.getValue()));
    }

    final var routingState =
        Optional.of(RoutingState.initializeWithPartitionCount(partitionDistribution.size()));

    return ClusterConfiguration.builder()
        .version(ClusterConfiguration.INITIAL_VERSION)
        .members(Map.copyOf(memberStates))
        .routingState(routingState)
        .clusterId(Optional.ofNullable(clusterId))
        .incarnationNumber(ClusterConfiguration.INITIAL_INCARNATION_NUMBER)
        .build();
  }

  /**
   * Generates the multi-partition-group counterpart of {@link #getClusterConfigFrom}, used by the
   * new configuration model. Every member in {@code clusterMembers} appears in the returned {@link
   * GlobalConfiguration} as {@code ACTIVE}, regardless of whether it replicates any partition — the
   * global configuration is the single authority for cluster membership, independent of partition
   * assignment. {@code partitionDistribution} is split into one {@link PartitionGroupConfiguration}
   * per distinct {@link PartitionId#group()} found in it, each with its own {@link RoutingState}
   * initialized from that group's own partition count; only members that replicate at least one
   * partition of a group appear in that group.
   *
   * <p>{@code partitionConfig} (exporter state) is applied uniformly to every partition regardless
   * of group, since {@code StaticConfiguration} has no per-group exporter configuration yet.
   */
  public static CurrentClusterConfiguration getCurrentClusterConfigurationFrom(
      final Set<MemberId> clusterMembers,
      final Set<PartitionMetadata> partitionDistribution,
      final DynamicPartitionConfig partitionConfig,
      @Nullable final String clusterId) {
    final Map<MemberId, BrokerState> brokerStates = new HashMap<>();
    for (final var member : clusterMembers) {
      brokerStates.put(member, BrokerState.initializeAsActive());
    }
    final var globalConfiguration =
        new GlobalConfiguration(
            GlobalConfiguration.INITIAL_VERSION,
            Optional.ofNullable(clusterId),
            brokerStates,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    final var partitionStatesByGroupAndMember =
        new HashMap<String, Map<MemberId, Map<Integer, PartitionState>>>();
    for (final var partitionMetadata : partitionDistribution) {
      final var groupId = partitionMetadata.id().group();
      final var partitionId = partitionMetadata.id().number();
      for (final var member : partitionMetadata.members()) {
        final var memberPriority = partitionMetadata.getPriority(member);
        partitionStatesByGroupAndMember
            .computeIfAbsent(groupId, ignored -> new HashMap<>())
            .computeIfAbsent(member, ignored -> new HashMap<>())
            .put(partitionId, PartitionState.active(memberPriority, partitionConfig));
      }
    }

    final Map<String, PartitionGroupConfiguration> partitionGroups = new HashMap<>();
    for (final var groupEntry : partitionStatesByGroupAndMember.entrySet()) {
      final var partitionsByMember = groupEntry.getValue();
      final Map<MemberId, BrokerPartitionState> brokerPartitionStates = new HashMap<>();
      final var partitionIdsInGroup = new HashSet<Integer>();
      for (final var memberEntry : partitionsByMember.entrySet()) {
        brokerPartitionStates.put(
            memberEntry.getKey(), BrokerPartitionState.initialize(memberEntry.getValue()));
        partitionIdsInGroup.addAll(memberEntry.getValue().keySet());
      }
      final var routingState =
          Optional.of(RoutingState.initializeWithPartitionCount(partitionIdsInGroup.size()));
      partitionGroups.put(
          groupEntry.getKey(),
          new PartitionGroupConfiguration(
              PartitionGroupConfiguration.INITIAL_VERSION,
              PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
              brokerPartitionStates,
              routingState,
              Optional.empty(),
              Optional.empty()));
    }

    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        globalConfiguration,
        partitionGroups,
        PhasedChangeState.empty());
  }

  public static Set<PartitionMetadata> getPartitionDistributionFrom(
      final ClusterConfiguration clusterConfiguration, final String groupName) {
    if (clusterConfiguration.isUninitialized()) {
      throw new IllegalStateException(
          "Cannot generated partition distribution from uninitialized configuration");
    }

    final var memberPriorityByPartition = new HashMap<Integer, Map<MemberId, Integer>>();
    clusterConfiguration
        .members()
        .forEach(
            (memberId, member) -> {
              for (final Entry<Integer, PartitionState> entry : member.partitions().entrySet()) {
                final Integer partitionId = entry.getKey();
                final PartitionState partitionState = entry.getValue();
                if (partitionState.state().equals(State.ACTIVE)
                    || partitionState.state().equals(State.LEAVING)
                    || partitionState.state().equals(State.RECOVERING)) {
                  // only add active, leaving, and recovering partitions because only those has to
                  // be started
                  memberPriorityByPartition
                      .computeIfAbsent(partitionId, k -> new HashMap<>())
                      .put(memberId, partitionState.priority());
                }
              }
            });

    return memberPriorityByPartition.entrySet().stream()
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
        new PartitionId(groupName, e.getKey()),
        memberPriorities.keySet(),
        memberPriorities,
        optionalPrimary.get().getValue(),
        optionalPrimary.get().getKey());
  }
}
