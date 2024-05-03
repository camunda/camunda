/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

public record ExportersConfig(Map<String, ExporterState> exporters) {

  public static ExportersConfig empty() {
    return new ExportersConfig(Map.of());
  }

  public ExportersConfig updateExporter(
      final String exporterName, final ExporterState exporterState) {
    final var newExporters =
        ImmutableMap.<String, ExporterState>builder()
            .putAll(exporters)
            .put(exporterName, exporterState)
            .buildKeepingLast(); // choose last one if there are duplicate keys
    return new ExportersConfig(newExporters);
  }
}
