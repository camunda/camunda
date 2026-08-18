/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ForceScaleDownRequestTransformer implements ConfigurationChangeRequest {

  private final Set<MemberId> membersToRetain;

  private final MemberId coordinator;

  public ForceScaleDownRequestTransformer(
      final Set<MemberId> membersToRetain, final MemberId coordinator) {
    this.membersToRetain = membersToRetain;
    this.coordinator = coordinator;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    for (final var member : membersToRetain) {
      if (!clusterConfiguration.hasMember(member)) {
        return Either.left(
            new InvalidRequest(
                String.format(
                    "Expected to force configure while retaining broker '%s', but broker '%s' is not in the current cluster. Current members are '%s'",
                    member, member, clusterConfiguration.members().keySet())));
      }
    }

    final SortedMap<Integer, SortedSet<MemberId>> partitionsWithNewMembers =
        survivingReplicas(
            clusterConfiguration.members().entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Entry::getKey, entry -> entry.getValue().partitions().keySet())));

    final var partitionsWithNoReplicas = orphanedPartitions(partitionsWithNewMembers);

    if (!partitionsWithNoReplicas.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Expected to force configure and retain members '%s', but this will result in partitions '%s' having no replicas",
                  membersToRetain, partitionsWithNoReplicas)));
    }

    // members that are not in membersToRetain
    final var memberToRemove =
        clusterConfiguration.members().keySet().stream()
            .filter(m -> !membersToRetain.contains(m))
            .collect(ImmutableSortedSet.toImmutableSortedSet(Ordering.natural()));

    return generateOperations(partitionsWithNewMembers, memberToRemove);
  }

  @Override
  public boolean isForced() {
    return true;
  }

  private Either<Exception, List<ClusterConfigurationChangeOperation>> generateOperations(
      final SortedMap<Integer, SortedSet<MemberId>> partitionsWithNewMembers,
      final SortedSet<MemberId> memberToRemove) {

    final var partitionForceConfigureOperations = reconfigurePartitions(partitionsWithNewMembers);
    final List<ClusterConfigurationChangeOperation> operations =
        new ArrayList<>(partitionForceConfigureOperations);

    final var memberRemoveOperations = forceRemoveMembers(memberToRemove);
    operations.addAll(memberRemoveOperations);

    return Either.right(operations);
  }

  private List<ClusterConfigurationChangeOperation> reconfigurePartitions(
      final SortedMap<Integer, SortedSet<MemberId>> partitionsWithNewMembers) {
    return partitionsWithNewMembers.entrySet().stream()
        .map(
            partition ->
                new PartitionForceReconfigureOperation(
                    partition.getValue().stream().findFirst().orElseThrow(),
                    partition.getKey(),
                    partition.getValue()))
        .map(ClusterConfigurationChangeOperation.class::cast)
        .toList();
  }

  private List<ClusterConfigurationChangeOperation> forceRemoveMembers(
      final SortedSet<MemberId> membersToRemove) {
    return membersToRemove.stream()
        .map(member -> new MemberRemoveOperation(coordinator, member))
        .map(ClusterConfigurationChangeOperation.class::cast)
        .toList();
  }

  /**
   * The partitions of one physical tenant paired with the replicas that survive the removal: every
   * partition any broker of that tenant holds, mapped to the retained brokers among them.
   *
   * <p>A partition whose every replica is being removed maps to an empty set rather than dropping
   * out of the map — that is the case {@link #orphanedPartitions(SortedMap)} has to find.
   *
   * @param partitionsByMember which partitions each broker holds, for one physical tenant
   */
  private SortedMap<Integer, SortedSet<MemberId>> survivingReplicas(
      final Map<MemberId, ? extends Collection<Integer>> partitionsByMember) {
    final SortedMap<Integer, SortedSet<MemberId>> survivingReplicas = new TreeMap<>();
    partitionsByMember.forEach(
        (member, partitions) ->
            partitions.forEach(
                partitionId -> {
                  final var replicas =
                      survivingReplicas.computeIfAbsent(partitionId, ignored -> new TreeSet<>());
                  if (membersToRetain.contains(member)) {
                    replicas.add(member);
                  }
                }));
    return survivingReplicas;
  }

  /** The partitions that the removal would leave without a single replica. */
  private static List<Integer> orphanedPartitions(
      final SortedMap<Integer, SortedSet<MemberId>> survivingReplicas) {
    return survivingReplicas.entrySet().stream()
        .filter(partition -> partition.getValue().isEmpty())
        .map(Entry::getKey)
        .toList();
  }
}
