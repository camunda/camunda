/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.dynamic.config.state.ExportingState;

// The PAUSED phase is when the exporter is paused, and the exporter is not exporting records.
// The SOFT_PAUSED phase is when we keep exporting the records without updating the exporter state.
public enum ExporterPhase {
  EXPORTING,
  PAUSED,
  SOFT_PAUSED,
  CLOSED;

  public ExportingState toExportingState() {
    return switch (this) {
      case PAUSED -> ExportingState.PAUSED;
      case SOFT_PAUSED -> ExportingState.SOFT_PAUSED;
      // CLOSED is never persisted; it only exists while an exporter director shuts down.
      case EXPORTING, CLOSED -> ExportingState.EXPORTING;
    };
  }

  /**
   * The phase an exporter director should (re)open with for a given dynamic-config exporting state.
   * {@code UNKNOWN} maps to {@code EXPORTING}, its default: a partition whose config has not been
   * initialized yet, or one still on a wire format that predates this field, is exporting unless
   * something explicitly paused it.
   */
  public static ExporterPhase from(final ExportingState state) {
    return switch (state) {
      case PAUSED -> PAUSED;
      case SOFT_PAUSED -> SOFT_PAUSED;
      case EXPORTING, UNKNOWN -> EXPORTING;
    };
  }
}
