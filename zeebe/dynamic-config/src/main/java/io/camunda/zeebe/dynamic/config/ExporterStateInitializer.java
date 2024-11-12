/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Set;

/**
 * Updates the exporter state of the local member in the configuration. If a broker restarts with a
 * changes of exporters in the static configuration, this modifier updates the dynamic config to
 * reflect that. If new exporter are added to the static configuration, they are added to the
 * dynamic config with state ENABLED. If existing exporters are removed, they are marked as
 * disabled. Note that the exporters are not removed from the dynamic config.
 */
public class ExporterStateInitializer implements ClusterConfigurationModifier {

  private final Set<String> configuredExporters;
  private final MemberId localMemberId;
  private final ConcurrencyControl executor;

  public ExporterStateInitializer(
      final Set<String> configuredExporters,
      final MemberId localMemberId,
      final ConcurrencyControl executor) {
    this.configuredExporters = configuredExporters;
    this.localMemberId = localMemberId;
    this.executor = executor;
  }

  @Override
  public ActorFuture<ClusterConfiguration> modify(final ClusterConfiguration configuration) {
    final ActorFuture<ClusterConfiguration> result = executor.createFuture();
    if (!configuration.hasMember(localMemberId)) {
      result.complete(configuration);
    } else {
      result.complete(configuration.updateMember(localMemberId, this::updateExporterState));
    }
    return result;
  }

  private MemberState updateExporterState(final MemberState memberState) {
    MemberState updatedMemberState = memberState;
    for (final var p : memberState.partitions().keySet()) {
      final PartitionState currentPartitionState = memberState.partitions().get(p);
      final var updatedPartitionState = updateExporterStateInPartition(currentPartitionState);
      // Do not update the member state if the partition state is not changed, otherwise the
      // version will be updated during every restart and this could interfere with other
      // concurrent configuration changes.
      if (!updatedPartitionState.equals(currentPartitionState)) {
        updatedMemberState =
            updatedMemberState.updatePartition(p, partitionState -> updatedPartitionState);
      }
    }
    return updatedMemberState;
  }

  private PartitionState updateExporterStateInPartition(final PartitionState partitionState) {
    final var initializedPartitionState =
        partitionState.config().isInitialized()
            ? partitionState
            : new PartitionState(
                partitionState.state(), partitionState.priority(), DynamicPartitionConfig.init());
    final var exportersInConfig = initializedPartitionState.config().exporting().exporters();

    final var newlyAddedExporters =
        configuredExporters.stream().filter(id -> !exportersInConfig.containsKey(id)).toList();
    final var removedExporters =
        exportersInConfig.keySet().stream()
            .filter(id -> !configuredExporters.contains(id))
            .toList();

    return initializedPartitionState
        .updateConfig(c -> c.updateExporting(e -> e.disableExporters(removedExporters)))
        .updateConfig(c -> c.updateExporting(e -> e.addExporters(newlyAddedExporters)));
  }
}
