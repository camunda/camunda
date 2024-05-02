/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.InvalidRequest;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator.TopologyChangeRequest;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ForceScaleDownRequestTransformer implements TopologyChangeRequest {

  private final Set<MemberId> membersToRetain;

  private final MemberId coordinator;

  public ForceScaleDownRequestTransformer(
      final Set<MemberId> membersToRetain, final MemberId coordinator) {
    this.membersToRetain = membersToRetain;
    this.coordinator = coordinator;
  }

  @Override
  public Either<Exception, List<TopologyChangeOperation>> operations(
      final ClusterTopology currentTopology) {
    for (final var member : membersToRetain) {
      if (!currentTopology.hasMember(member)) {
        return Either.left(
            new InvalidRequest(
                String.format(
                    "Expected to force configure while retaining broker '%s', but broker '%s' is not in the current cluster. Current members are '%s'",
                    member, member, currentTopology.members().keySet())));
      }
    }

    final List<Integer> partitions =
        currentTopology.members().values().stream()
            .map(MemberState::partitions)
            .flatMap(p -> p.keySet().stream())
            .distinct()
            .toList();

    final Map<Integer, ArrayList<MemberId>> partitionsWithNewMembers =
        calculateNewConfiguration(currentTopology, membersToRetain, partitions);

    final var partitionsWithNoReplicas =
        partitions.stream()
            .filter(
                p ->
                    !partitionsWithNewMembers.containsKey(p)
                        || partitionsWithNewMembers.get(p).isEmpty())
            .toList();

    final var hasReplicasForAllPartitions = partitionsWithNoReplicas.isEmpty();

    if (!hasReplicasForAllPartitions) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Expected to force configure and retain members '%s', but this will result in partitions '%s' having no replicas",
                  membersToRetain, partitionsWithNoReplicas)));
    }

    // members that are not in membersToRetain
    final var memberToRemove =
        currentTopology.members().keySet().stream()
            .filter(m -> !membersToRetain.contains(m))
            .toList();

    return generateOperations(partitionsWithNewMembers, memberToRemove);
  }

  @Override
  public boolean isForced() {
    return true;
  }

  private Either<Exception, List<TopologyChangeOperation>> generateOperations(
      final Map<Integer, ArrayList<MemberId>> partitionsWithNewMembers,
      final List<MemberId> memberToRemove) {

    final var partitionForceConfigureOperations = reconfigurePartitions(partitionsWithNewMembers);
    final List<TopologyChangeOperation> operations =
        new ArrayList<>(partitionForceConfigureOperations);

    final var memberRemoveOperations = forceRemoveMembers(memberToRemove);
    operations.addAll(memberRemoveOperations);

    return Either.right(operations);
  }

  private List<TopologyChangeOperation> reconfigurePartitions(
      final Map<Integer, ArrayList<MemberId>> partitionsWithNewMembers) {
    return partitionsWithNewMembers.entrySet().stream()
        .map(
            partition ->
                new PartitionForceReconfigureOperation(
                    partition.getValue().stream().findFirst().orElseThrow(),
                    partition.getKey(),
                    partition.getValue()))
        .map(TopologyChangeOperation.class::cast)
        .toList();
  }

  private List<TopologyChangeOperation> forceRemoveMembers(final List<MemberId> membersToRemove) {
    return membersToRemove.stream()
        .map(member -> new MemberRemoveOperation(coordinator, member))
        .map(TopologyChangeOperation.class::cast)
        .toList();
  }

  private Map<Integer, ArrayList<MemberId>> calculateNewConfiguration(
      final ClusterTopology currentTopology,
      final Set<MemberId> membersToRetain,
      final List<Integer> partitions) {

    final Map<Integer, ArrayList<MemberId>> partitionToMembersMap = new HashMap<>();
    for (final var partitionId : partitions) {
      partitionToMembersMap.put(partitionId, new ArrayList<>());
      for (final var member : membersToRetain) {
        if (currentTopology.getMember(member).hasPartition(partitionId)) {
          partitionToMembersMap.computeIfPresent(
              partitionId,
              (ignore, members) -> {
                members.add(member);
                return members;
              });
        }
      }
    }

    return partitionToMembersMap;
  }
}
