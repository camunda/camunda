/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.slf4j.LoggerFactory.*;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Updates the exporter state of the local member in the configuration. If a broker restarts with a
 * changes of exporters in the static configuration, this modifier updates the dynamic config to
 * reflect that. If new exporter are added to the static configuration, they are added to the
 * dynamic config with state ENABLED. If existing exporters are removed, they are marked as
 * CONFIG_NOT_FOUND. Note that the exporters are not removed from the dynamic config.
 */
public class ExporterStateInitializer implements ClusterConfigurationModifier {

  private static final Logger LOGGER = getLogger(ExporterStateInitializer.class);
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
    final var configRemovedExporters =
        exportersInConfig.entrySet().stream()
            // Only mark exporters as CONFIG_NOT_FOUND if they are currently enabled.
            .filter(entry -> State.ENABLED.equals(entry.getValue().state()))
            .filter(entry -> !configuredExporters.contains(entry.getKey()))
            .map(Entry::getKey)
            .toList();

    if (!configRemovedExporters.isEmpty()) {
      LOGGER.warn(
          "Previously configured exporters [{}] are not found in the application properties. "
              + "They will be paused. Please add the configuration back or remove the exporter using the management api.",
          configRemovedExporters);
    }
    // Re-enable exporters whose configuration is added back to the application properties.
    final var configReaddedExporters =
        exportersInConfig.entrySet().stream()
            .filter(entry -> exportersInConfig.containsKey(entry.getKey()))
            .filter(entry -> entry.getValue().state().equals(State.CONFIG_NOT_FOUND))
            .toList();

    return initializedPartitionState
        .updateConfig(c -> c.updateExporting(e -> e.withConfigNotFoundFor(configRemovedExporters)))
        .updateConfig(c -> c.updateExporting(e -> reEnableExporters(e, configReaddedExporters)))
        .updateConfig(c -> c.updateExporting(e -> e.addExporters(newlyAddedExporters)));
  }

  private ExportersConfig reEnableExporters(
      final ExportersConfig exportersConfig,
      final List<Entry<String, ExporterState>> configReaddedExporters) {

    ExportersConfig updating = exportersConfig;
    for (final var entry : configReaddedExporters) {
      final var exporterName = entry.getKey();
      final var exporterState = entry.getValue();
      // reuse the metadata version and initializedFrom from the existing exporter state
      updating =
          updating.enableExporter(
              exporterName,
              exporterState.initializedFrom().orElse(null),
              exporterState.metadataVersion());
    }
    return updating;
  }
}
