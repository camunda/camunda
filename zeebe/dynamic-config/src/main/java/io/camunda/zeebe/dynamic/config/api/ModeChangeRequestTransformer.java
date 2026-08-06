/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Transitions partitions between {@link Mode#PROCESSING} and {@link Mode#RECOVERING}, either for
 * one physical tenant or for every physical tenant of the cluster.
 */
public final class ModeChangeRequestTransformer implements ConfigurationChangeRequest {

  private final ModeChangeRequest request;

  public ModeChangeRequestTransformer(final ModeChangeRequest request) {
    this.request = request;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    final State sourceState = request.mode() == Mode.RECOVERING ? State.ACTIVE : State.RECOVERING;
    final var members =
        clusterConfiguration.members().entrySet().stream()
            .filter(e -> e.getValue().state() == sourceState)
            .map(Entry::getKey)
            .toList();

    // All members first start the change operation and complete it async
    // Following up with a verification step that the operation completed successfully
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    members.forEach(memberId -> operations.add(new ModeChangeOperation(memberId, request.mode())));
    members.forEach(
        memberId -> operations.add(new AwaitModeChangeOperation(memberId, request.mode())));

    return Either.right(operations);
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    final var physicalTenantId = request.physicalTenantId();
    if (physicalTenantId.isPresent()
        && !clusterConfiguration.hasPartitionGroup(physicalTenantId.get())) {
      return Either.left(
          new InvalidRequest(
              "Expected to change the mode of physical tenant '%s', but it has no partition group in this cluster"
                  .formatted(physicalTenantId.get())));
    }

    final Map<String, List<PartitionGroupOperation>> operationsPerGroup = new LinkedHashMap<>();
    clusterConfiguration.partitionGroups().entrySet().stream()
        .filter(
            group -> physicalTenantId.isEmpty() || physicalTenantId.get().equals(group.getKey()))
        .forEach(
            group -> {
              final var operations = modeChangeOperations(membersToTransition(group.getValue()));
              if (!operations.isEmpty()) {
                operationsPerGroup.put(group.getKey(), operations);
              }
            });

    if (operationsPerGroup.isEmpty()) {
      return Either.right(List.of());
    }
    return Either.right(List.of(new PartitionGroupParallelPhase(operationsPerGroup)));
  }

  /** The brokers of the group that are not in the target mode yet. */
  private List<MemberId> membersToTransition(final PartitionGroupConfiguration partitionGroup) {
    return partitionGroup.members().entrySet().stream()
        .filter(member -> member.getValue().mode() != request.mode())
        .map(Entry::getKey)
        .toList();
  }

  /**
   * All members first start the change operation and complete it async. Following up with a
   * verification step that the operation completed successfully.
   */
  private List<PartitionGroupOperation> modeChangeOperations(final List<MemberId> members) {
    final List<PartitionGroupOperation> operations = new ArrayList<>();
    members.forEach(memberId -> operations.add(new ModeChangeOperation(memberId, request.mode())));
    members.forEach(
        memberId -> operations.add(new AwaitModeChangeOperation(memberId, request.mode())));
    return operations;
  }
}
