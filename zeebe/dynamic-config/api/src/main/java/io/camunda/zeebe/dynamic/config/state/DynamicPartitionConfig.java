/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.util.function.UnaryOperator;

/** Represents configuration of a partition that can be updated during runtime. */
public record DynamicPartitionConfig(ExportingConfig exporting) {

  public static DynamicPartitionConfig uninitialized() {
    return new DynamicPartitionConfig(null);
  }

  public static DynamicPartitionConfig init() {
    return new DynamicPartitionConfig(ExportingConfig.init());
  }

  public boolean isInitialized() {
    return exporting != null;
  }

  public DynamicPartitionConfig updateExporting(final ExportingConfig exporting) {
    return new DynamicPartitionConfig(exporting);
  }

  public DynamicPartitionConfig updateExporting(
      final UnaryOperator<ExportingConfig> exportingUpdater) {
    return new DynamicPartitionConfig(exportingUpdater.apply(exporting));
  }
}
