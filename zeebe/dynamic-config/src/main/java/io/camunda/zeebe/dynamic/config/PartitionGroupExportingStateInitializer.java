/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Seeds the exporting state of a partition — {@link ExportingState}, i.e. whether exporting is
 * paused for the partition as a whole — from the legacy {@code .exporterPaused} marker files into
 * the local member's partitions in every partition group. This only fills in partitions whose
 * exporting state is still {@link ExportingState#UNKNOWN}, so a cluster upgraded from a version
 * that did not track exporting in the dynamic configuration keeps its paused partitions paused.
 *
 * <p>This initializer seeds every configured partition group from the legacy per-partition files.
 * Unlike the legacy configuration initializer, this has no after-restore branch: nothing currently
 * produces a restore change plan on {@link CurrentClusterConfiguration}, so the branch would be
 * unreachable and untested. Revisit once restore is migrated to the new model.
 *
 * <p>Not to be confused with {@link PartitionGroupExporterStateInitializer}, which reconciles the
 * per-exporter {@link io.camunda.zeebe.dynamic.config.state.ExporterState} (enabled/disabled)
 * against the statically configured exporters.
 */
@NullMarked
public class PartitionGroupExportingStateInitializer
    implements ClusterConfigurationModifier<CurrentClusterConfiguration> {

  private final Map<PartitionId, ExportingState> legacyExportingStates;
  private final MemberId localMemberId;

  public PartitionGroupExportingStateInitializer(
      final Map<PartitionId, ExportingState> legacyExportingStates, final MemberId localMemberId) {
    this.legacyExportingStates = legacyExportingStates;
    this.localMemberId = localMemberId;
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> modify(
      final CurrentClusterConfiguration configuration) {
    var updated = configuration;
    for (final var groupId : configuration.partitionGroups().keySet()) {
      final var partitionGroup = updated.partitionGroup(groupId);
      if (partitionGroup != null && partitionGroup.hasMember(localMemberId)) {
        updated =
            updated.updatePartitionGroupConfig(
                groupId,
                group ->
                    group.updateMember(localMemberId, state -> seedExportingState(groupId, state)));
      }
    }
    return CompletableActorFuture.completed(updated);
  }

  private BrokerPartitionState seedExportingState(
      final String groupId, final BrokerPartitionState brokerPartitionState) {
    BrokerPartitionState updated = brokerPartitionState;
    for (final var partitionId : brokerPartitionState.partitions().keySet()) {
      final var partitionState = brokerPartitionState.partitions().get(partitionId);
      if (!partitionState.config().isInitialized()
          || partitionState.config().exporting().state() != ExportingState.UNKNOWN) {
        continue;
      }
      final var legacyState = legacyExportingStates.get(new PartitionId(groupId, partitionId));
      if (legacyState == null || legacyState == ExportingState.UNKNOWN) {
        continue;
      }
      updated =
          updated.updatePartition(
              partitionId,
              ps -> ps.updateConfig(c -> c.updateExporting(e -> e.withState(legacyState))));
    }
    return updated;
  }
}
