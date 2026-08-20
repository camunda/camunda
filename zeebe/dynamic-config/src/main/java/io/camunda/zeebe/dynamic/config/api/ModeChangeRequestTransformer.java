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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    final var physicalTenantId = request.physicalTenantId();
    if (physicalTenantId.isPresent()
        && !clusterConfiguration.hasPartitionGroup(physicalTenantId.get())) {
      return Either.left(
          new NotFound(
              "Expected to change the mode of physical tenant '%s', but there's no such tenant"
                  .formatted(physicalTenantId.get())));
    }

    final Map<String, OperationGraph> graphsPerGroup = new LinkedHashMap<>();
    clusterConfiguration.partitionGroups().entrySet().stream()
        .filter(
            group -> physicalTenantId.isEmpty() || physicalTenantId.get().equals(group.getKey()))
        .forEach(
            group -> {
              final var members = membersToTransition(group.getValue());
              if (!members.isEmpty()) {
                graphsPerGroup.put(group.getKey(), modeChangeGraph(members));
              }
            });

    if (graphsPerGroup.isEmpty()) {
      return Either.right(List.of());
    }
    return Either.right(List.of(new PartitionGroupPhase(graphsPerGroup)));
  }

  /**
   * The brokers active in the group that are not in the target mode yet. A broker is active in a
   * group iff it holds partitions there; one that holds none has nothing to transition, and
   * awaiting a mode change from it would never complete.
   */
  private List<MemberId> membersToTransition(final PartitionGroupConfiguration partitionGroup) {
    return partitionGroup.members().entrySet().stream()
        .filter(member -> !member.getValue().partitions().isEmpty())
        .filter(member -> member.getValue().mode() != request.mode())
        .map(Entry::getKey)
        .toList();
  }

  /**
   * Every member starts the transition, then every member's completion is verified.
   *
   * <p>The two stages stay ordered against each other exactly as they were when this was a flat
   * list: each verification waits for <em>all</em> the mode changes, not only its own broker's. The
   * transition is a cluster-wide one and nothing here establishes that a broker may be confirmed
   * while a peer has not yet started, so the conservative ordering is kept until someone who knows
   * the recovery semantics says otherwise. Narrowing each verification to its own broker is a
   * one-line change to the dependency below.
   *
   * <p>What does change is that the members no longer take turns: all the mode changes run at once,
   * then all the verifications do. Round trips drop from {@code 2N} to two.
   */
  private OperationGraph modeChangeGraph(final List<MemberId> members) {
    final var builder = OperationGraph.builder();
    final Set<OperationId> modeChanges = new HashSet<>();
    members.forEach(
        memberId ->
            modeChanges.add(builder.add(new ModeChangeOperation(memberId, request.mode()))));
    members.forEach(
        memberId ->
            builder.add(new AwaitModeChangeOperation(memberId, request.mode()), modeChanges));
    return builder.build();
  }
}
