/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PartitionAssignmentRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transforms a SetClusterConfigurationRequest into a list of operations to transition from the
 * current configuration to the desired one.
 */
public final class SetClusterConfigurationRequestTransformer implements ConfigurationChangeRequest {

  private final Map<MemberId, List<PartitionAssignmentRequest>> desiredBrokerAssignments;

  public SetClusterConfigurationRequestTransformer(
      final Map<MemberId, List<PartitionAssignmentRequest>> desiredBrokerAssignments) {
    this.desiredBrokerAssignments = desiredBrokerAssignments;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    final var validationResult = validateRequest(clusterConfiguration);
    if (validationResult.isLeft()) {
      return validationResult;
    }

    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();

    final var currentMembers = clusterConfiguration.members().keySet();
    final var desiredMembers = desiredBrokerAssignments.keySet();

    // Add new members first
    final var membersToAdd =
        desiredMembers.stream()
            .filter(m -> !currentMembers.contains(m))
            .collect(Collectors.toSet());
    for (final var member : membersToAdd) {
      operations.add(new MemberJoinOperation(member));
    }

    // Compute partition changes for existing and new members
    operations.addAll(computePartitionChanges(clusterConfiguration));

    // Remove members that are not in the desired configuration
    final var membersToRemove =
        currentMembers.stream()
            .filter(m -> !desiredMembers.contains(m))
            .collect(Collectors.toSet());
    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.of(() -> clusterConfiguration)
            .getDefaultCoordinator();
    for (final var member : membersToRemove) {
      operations.add(new MemberRemoveOperation(coordinator, member));
    }

    return Either.right(operations);
  }

  private Either<Exception, List<ClusterConfigurationChangeOperation>> validateRequest(
      final ClusterConfiguration clusterConfiguration) {

    if (desiredBrokerAssignments.isEmpty()) {
      return Either.left(
          new InvalidRequest("At least one broker must be specified in the desired configuration"));
    }

    // Check for duplicate partition assignments within the same broker
    for (final var entry : desiredBrokerAssignments.entrySet()) {
      final var brokerId = entry.getKey();
      final var partitions = entry.getValue();
      final var partitionIds =
          partitions.stream().map(PartitionAssignmentRequest::partitionId).toList();
      final var uniquePartitionIds = new HashSet<>(partitionIds);
      if (partitionIds.size() != uniquePartitionIds.size()) {
        return Either.left(
            new InvalidRequest(
                String.format("Broker %s has duplicate partition assignments", brokerId.id())));
      }
    }

    // Check that all partitions have at least one replica
    final var allPartitionIds = getAllPartitionIds();
    if (allPartitionIds.isEmpty()) {
      return Either.left(new InvalidRequest("At least one partition must be assigned"));
    }

    // Check for valid partition IDs (must be positive)
    for (final var partitionId : allPartitionIds) {
      if (partitionId <= 0) {
        return Either.left(
            new InvalidRequest(
                String.format(
                    "Invalid partition ID: %d. Partition IDs must be positive", partitionId)));
      }
    }

    // Check that partition IDs are contiguous starting from 1
    final var maxPartitionId = allPartitionIds.stream().max(Integer::compareTo).orElse(0);
    for (int i = 1; i <= maxPartitionId; i++) {
      if (!allPartitionIds.contains(i)) {
        return Either.left(
            new InvalidRequest(
                String.format(
                    "Missing partition %d. Partition IDs must be contiguous starting from 1", i)));
      }
    }

    // Check that priorities are valid (positive)
    for (final var entry : desiredBrokerAssignments.entrySet()) {
      for (final var assignment : entry.getValue()) {
        if (assignment.priority() <= 0) {
          return Either.left(
              new InvalidRequest(
                  String.format(
                      "Invalid priority %d for partition %d on broker %s. Priority must be positive",
                      assignment.priority(), assignment.partitionId(), entry.getKey().id())));
        }
      }
    }

    return Either.right(List.of());
  }

  private Set<Integer> getAllPartitionIds() {
    return desiredBrokerAssignments.values().stream()
        .flatMap(List::stream)
        .map(PartitionAssignmentRequest::partitionId)
        .collect(Collectors.toSet());
  }

  private List<ClusterConfigurationChangeOperation> computePartitionChanges(
      final ClusterConfiguration clusterConfiguration) {
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();

    // Build a map of current partition assignments: partitionId -> Map<memberId, priority>
    final Map<Integer, Map<MemberId, Integer>> currentAssignments = new HashMap<>();
    for (final var entry : clusterConfiguration.members().entrySet()) {
      final var memberId = entry.getKey();
      final var memberState = entry.getValue();
      for (final var partitionEntry : memberState.partitions().entrySet()) {
        final var partitionId = partitionEntry.getKey();
        final var partitionState = partitionEntry.getValue();
        currentAssignments
            .computeIfAbsent(partitionId, k -> new HashMap<>())
            .put(memberId, partitionState.priority());
      }
    }

    // Build a map of desired partition assignments: partitionId -> Map<memberId, priority>
    final Map<Integer, Map<MemberId, Integer>> desiredAssignments = new HashMap<>();
    for (final var entry : desiredBrokerAssignments.entrySet()) {
      final var memberId = entry.getKey();
      for (final var assignment : entry.getValue()) {
        desiredAssignments
            .computeIfAbsent(assignment.partitionId(), k -> new HashMap<>())
            .put(memberId, assignment.priority());
      }
    }

    // Get all partition IDs (both current and desired)
    final var allPartitionIds = new HashSet<Integer>();
    allPartitionIds.addAll(currentAssignments.keySet());
    allPartitionIds.addAll(desiredAssignments.keySet());

    for (final var partitionId : allPartitionIds) {
      final var currentPartitionAssignments =
          currentAssignments.getOrDefault(partitionId, Map.of());
      final var desiredPartitionAssignments =
          desiredAssignments.getOrDefault(partitionId, Map.of());

      // Check if this is a new partition (doesn't exist in current configuration)
      boolean isNewPartition = currentPartitionAssignments.isEmpty();

      // Members to add to this partition
      for (final var entry : desiredPartitionAssignments.entrySet()) {
        final var memberId = entry.getKey();
        final var desiredPriority = entry.getValue();

        if (!currentPartitionAssignments.containsKey(memberId)) {
          if (isNewPartition) {
            operations.add(
                new PartitionBootstrapOperation(memberId, partitionId, desiredPriority, true));
            isNewPartition = false;
          } else {
            // New member for this partition - join
            operations.add(new PartitionJoinOperation(memberId, partitionId, desiredPriority));
          }
        } else {
          // Member exists - check if priority needs to change
          final var currentPriority = currentPartitionAssignments.get(memberId);
          if (!currentPriority.equals(desiredPriority)) {
            operations.add(
                new PartitionReconfigurePriorityOperation(memberId, partitionId, desiredPriority));
          }
        }
      }

      // Members to remove from this partition
      for (final var memberId : currentPartitionAssignments.keySet()) {
        if (!desiredPartitionAssignments.containsKey(memberId)) {
          // Member should leave this partition
          final var currentPriority = currentPartitionAssignments.get(memberId);
          operations.add(new PartitionLeaveOperation(memberId, partitionId, 1));
        }
      }
    }

    return operations;
  }
}
