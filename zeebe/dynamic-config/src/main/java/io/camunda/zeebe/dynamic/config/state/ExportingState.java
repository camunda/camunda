/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

/** Controls the overall state of exporting across all exporters. */
public enum ExportingState {
  /** All enabled exporters are actively exporting records. */
  EXPORTING,
  /**
   * All enabled exporters are actively exporting records but progress updates are not persisted.
   */
  SOFT_PAUSED,
  /** Nothing is being exported. */
  PAUSED,
}
