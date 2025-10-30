/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableMap;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Represents configuration or state of exporting in a partition that can be updated during runtime.
 *
 * @param exporters the state of each exporter in this partition
 */
public record ExportersConfig(Map<String, ExporterState> exporters) {

  public static ExportersConfig empty() {
    return new ExportersConfig(Map.of());
  }

  private ExportersConfig updateExporter(
      final String exporterName, final ExporterState exporterState) {
    final var newExporters =
        ImmutableMap.<String, ExporterState>builder()
            .putAll(exporters)
            .put(exporterName, exporterState)
            .buildKeepingLast(); // choose last one if there are duplicate keys
    return new ExportersConfig(newExporters);
  }

  public ExportersConfig disableExporter(final String exporterName) {
    return updateExporter(
        exporterName,
        new ExporterState(
            exporters.get(exporterName).metadataVersion(),
            ExporterState.State.DISABLED,
            Optional.empty()));
  }

  public ExportersConfig deleteExporter(final String exporterName) {
    final var newExporters =
        exporters.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(exporterName))
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    return new ExportersConfig(newExporters);
  }

  public ExportersConfig disableExporters(final Collection<String> exporterNames) {
    final var builder = ImmutableMap.<String, ExporterState>builder().putAll(exporters);

    exporterNames.forEach(
        exporterName ->
            builder.put(
                exporterName,
                new ExporterState(
                    exporters.get(exporterName).metadataVersion(),
                    ExporterState.State.DISABLED,
                    Optional.empty())));

    final var newExporters =
        builder.buildKeepingLast(); // choose last one if there are duplicate keys
    return new ExportersConfig(newExporters);
  }

  public ExportersConfig enableExporter(final String exporterName, final long metadataVersion) {
    return enableExporter(exporterName, null, metadataVersion);
  }

  public ExportersConfig enableExporter(
      final String exporterName, final String initializeFrom, final long metadataVersion) {
    return updateExporter(
        exporterName,
        new ExporterState(metadataVersion, State.ENABLED, Optional.ofNullable(initializeFrom)));
  }

  public ExportersConfig addExporters(final Collection<String> exporterNames) {
    exporterNames.forEach(
        exporterName -> {
          if (exporters.containsKey(exporterName)) {
            throw new IllegalArgumentException(
                String.format("Exporter '%s' already exists in the partition", exporterName));
          }
        });

    final var builder = ImmutableMap.<String, ExporterState>builder().putAll(exporters);

    exporterNames.forEach(
        exporterName ->
            builder.put(exporterName, new ExporterState(0, State.ENABLED, Optional.empty())));

    final var newExporters =
        builder.buildKeepingLast(); // choose last one if there are duplicate keys
    return new ExportersConfig(newExporters);
  }

  /**
   * Updates existing exporters to state {@link ExporterState.State#CONFIG_NOT_FOUND}.
   *
   * @param exporterNames the names of exporters for which the state should be updated
   * @return a new {@link ExportersConfig} with the updated state for the specified exporters
   */
  public ExportersConfig withConfigNotFoundFor(final Collection<String> exporterNames) {
    final var builder = ImmutableMap.<String, ExporterState>builder().putAll(exporters);

    exporterNames.forEach(
        exporterName ->
            builder.put(
                exporterName,
                new ExporterState(
                    exporters.get(exporterName).metadataVersion(),
                    ExporterState.State.CONFIG_NOT_FOUND,
                    exporters.get(exporterName).initializedFrom())));

    final var newExporters =
        builder.buildKeepingLast(); // choose last one if there are duplicate keys
    return new ExportersConfig(newExporters);
  }
}
