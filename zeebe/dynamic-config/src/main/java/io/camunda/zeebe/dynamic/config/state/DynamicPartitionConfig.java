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
    // This is temporary until we have a way to initialize this during bootstrap or first update to
    // the version that added this
    return new DynamicPartitionConfig(null);
  }

  // Only used in tests. May be removed.
  public static DynamicPartitionConfig init() {
    // This is temporary until we have a way to initialize this during bootstrap or first update to
    // the version that added this
    return new DynamicPartitionConfig(ExportingConfig.empty());
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
