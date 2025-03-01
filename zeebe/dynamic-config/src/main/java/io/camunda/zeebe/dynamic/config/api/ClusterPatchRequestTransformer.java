/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.protocol.Topology.PartitionsDistribution;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ClusterPatchRequestTransformer implements ConfigurationChangeRequest {

  private final Set<MemberId> membersToAdd;
  private final Set<MemberId> membersToRemove;
  private final Optional<Integer> newPartitionCount;
  private final Optional<Integer> newReplicationFactor;
  private final Optional<PartitionsDistribution> newPartitionsDistribution;

  public ClusterPatchRequestTransformer(
      final Set<MemberId> membersToAdd,
      final Set<MemberId> membersToRemove,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor,
      final Optional<PartitionsDistribution> newPartitionsDistribution) {
    this.membersToAdd = membersToAdd;
    this.membersToRemove = membersToRemove;
    this.newPartitionCount = newPartitionCount;
    this.newReplicationFactor = newReplicationFactor;
    this.newPartitionsDistribution = newPartitionsDistribution;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    // if membersToAdd and membersToRemove have common items, reject the request
    if (membersToAdd.stream().anyMatch(membersToRemove::contains)) {
      return Either.left(
          new ClusterConfigurationRequestFailedException.InvalidRequest(
              new IllegalArgumentException(
                  "Cannot add and remove the same member in the same request")));
    }

    if (newPartitionsDistribution.isPresent()) {
      final Set<PartitionMetadata> newPartitionsMetadata = new HashSet<>();
      newPartitionsDistribution.get().getPartitionsList().stream()
          .forEach(
              partitionDistribution -> {
                final Map<MemberId, Integer> newPriorities =
                    partitionDistribution.getMembersList().stream()
                        .collect(
                            Collectors.toMap(
                                member -> MemberId.from(member.getMemberId()),
                                member -> member.getPriority()));

                final Set<MemberId> newMembers = newPriorities.keySet();

                newPartitionsMetadata.add(
                    new PartitionMetadata(
                        new PartitionId("mygroup", partitionDistribution.getPartitionId()),
                        newMembers,
                        newPriorities,
                        3,
                        newMembers.stream().findFirst().get()));
              });
      return new PartitionReassignRequestTransformer(
              clusterConfiguration.members().keySet(),
              Optional.of(clusterConfiguration.minReplicationFactor()),
              Optional.of(clusterConfiguration.partitionCount()))
          .newOperations(clusterConfiguration, newPartitionsMetadata);
    }

    final var newSetOfMembers = new HashSet<>(clusterConfiguration.members().keySet());
    newSetOfMembers.addAll(membersToAdd);
    newSetOfMembers.removeAll(membersToRemove);

    return new ScaleRequestTransformer(newSetOfMembers, newReplicationFactor, newPartitionCount)
        .operations(clusterConfiguration);
  }
}
