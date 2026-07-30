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
import java.util.Set;

/**
 * New-model counterpart of {@link ExporterStateInitializer}, applying the same exporter-state
 * reconciliation to the local member's partitions in <em>every</em> partition group, instead of
 * once for the single default group. The per-partition reconciliation logic is shared with {@link
 * ExporterStateInitializer} via its package-visible static helpers.
 *
 * <p>Unlike {@link ExporterStateInitializer}, this does not have a post-restore branch that updates
 * every member's exporter state: nothing currently produces a restore change plan on {@link
 * CurrentClusterConfiguration}, so that branch would be unreachable and untested. Revisit once
 * restore is migrated to the new model.
 */
public class PartitionGroupExporterStateInitializer
    implements ClusterConfigurationModifier<CurrentClusterConfiguration> {

  private final Set<String> configuredExporters;
  private final MemberId localMemberId;

  public PartitionGroupExporterStateInitializer(
      final Set<String> configuredExporters, final MemberId localMemberId) {
    this.configuredExporters = configuredExporters;
    this.localMemberId = localMemberId;
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> modify(
      final CurrentClusterConfiguration configuration) {
    var updated = configuration;
    for (final var groupId : configuration.partitionGroups().keySet()) {
      if (updated.partitionGroup(groupId).hasMember(localMemberId)) {
        updated =
            updated.updatePartitionGroupConfig(
                groupId, group -> group.updateMember(localMemberId, this::updateExporterState));
      }
    }
    return CompletableActorFuture.completed(updated);
  }

  private BrokerPartitionState updateExporterState(
      final BrokerPartitionState brokerPartitionState) {
    BrokerPartitionState updated = brokerPartitionState;
    for (final var p : brokerPartitionState.partitions().keySet()) {
      final PartitionState currentPartitionState = brokerPartitionState.partitions().get(p);
      final var updatedPartitionState =
          ExporterStateInitializer.updateExporterStateInPartition(
              currentPartitionState, configuredExporters);
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
