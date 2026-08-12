/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Map;
import java.util.Set;

/**
 * New-model counterpart of {@link ExporterStateInitializer}, applying the same exporter-state
 * reconciliation to partitions in <em>every</em> partition group, instead of once for the single
 * default group. The per-partition reconciliation logic is shared with {@link
 * ExporterStateInitializer} via its package-visible static helpers.
 *
 * <p>Mirrors {@link ExporterStateInitializer}'s post-restore handling: if the migrated
 * configuration is {@link CurrentClusterConfiguration#isAfterRestore()}, only the coordinator
 * updates the exporter state, and it does so for every member of every group (not just the local
 * member) — the coordinator is the only member guaranteed to run this initializer again once the
 * post-restore {@code UpdateRoutingState} operation is applied, so it must reconcile on behalf of
 * everyone. Non-coordinators skip initialization entirely in that case.
 */
public class PartitionGroupExporterStateInitializer
    implements ClusterConfigurationModifier<CurrentClusterConfiguration> {

  private final Map<String, Set<String>> configuredExporters;
  private final MemberId localMemberId;
  private final boolean isCoordinator;

  public PartitionGroupExporterStateInitializer(
      final Map<String, Set<String>> configuredExporters,
      final MemberId localMemberId,
      final boolean isCoordinator) {
    this.configuredExporters = configuredExporters;
    this.localMemberId = localMemberId;
    this.isCoordinator = isCoordinator;
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> modify(
      final CurrentClusterConfiguration configuration) {
    if (configuration.isAfterRestore()) {
      if (isCoordinator) {
        return CompletableActorFuture.completed(updateExporterStateForAllMembers(configuration));
      }
      return CompletableActorFuture.completed(configuration);
    }
    return CompletableActorFuture.completed(updateLocalMemberExporterState(configuration));
  }

  private CurrentClusterConfiguration updateLocalMemberExporterState(
      final CurrentClusterConfiguration configuration) {
    var updated = configuration;
    for (final var groupId : configuration.partitionGroups().keySet()) {
      if (updated.partitionGroup(groupId).hasMember(localMemberId)) {
        updated =
            updated.updatePartitionGroupConfig(
                groupId,
                group ->
                    group.updateMember(
                        localMemberId,
                        brokerPartitionState ->
                            updateExporterState(groupId, brokerPartitionState)));
      }
    }
    return updated;
  }

  private CurrentClusterConfiguration updateExporterStateForAllMembers(
      final CurrentClusterConfiguration configuration) {
    var updated = configuration;
    for (final var groupId : configuration.partitionGroups().keySet()) {
      for (final var memberId : updated.partitionGroup(groupId).members().keySet()) {
        updated =
            updated.updatePartitionGroupConfig(
                groupId,
                group ->
                    group.updateMember(
                        memberId,
                        brokerPartitionState ->
                            updateExporterState(groupId, brokerPartitionState)));
      }
    }
    return updated;
  }

  private BrokerPartitionState updateExporterState(
      final String groupId, final BrokerPartitionState brokerPartitionState) {
    BrokerPartitionState updated = brokerPartitionState;
    if (!configuredExporters.containsKey(groupId)) {
      // All partition groups should have a configuration. When we support disabling physical
      // tenants, those groups must be filtered out before this call.
      throw new IllegalStateException(
          "No configuration found for partition group '%s'".formatted(groupId));
    }

    final Set<String> configuredExportersForGroup = configuredExporters.get(groupId);
    for (final var p : brokerPartitionState.partitions().keySet()) {
      final PartitionState currentPartitionState = brokerPartitionState.partitions().get(p);
      final var updatedPartitionState =
          ExporterStateInitializer.updateExporterStateInPartition(
              currentPartitionState, configuredExportersForGroup);
      // Do not update the partition state if it is unchanged, otherwise the version would be
      // bumped during every restart and could interfere with other concurrent configuration
      // changes.
      if (!updatedPartitionState.equals(currentPartitionState)) {
        updated = updated.updatePartition(p, partitionState -> updatedPartitionState);
      }
    }
    return updated;
  }
}
