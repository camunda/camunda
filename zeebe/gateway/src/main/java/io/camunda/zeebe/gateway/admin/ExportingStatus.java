/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * The exporting status aggregated over every replica of every partition of a physical tenant.
 *
 * <p>Pause and resume are applied to all replicas, so a status is only meaningful for backup
 * tooling if all replicas agree; when they don't, the tenant is {@link #MIXED} and the operation is
 * still in flight or was only partially applied.
 */
@NullMarked
public enum ExportingStatus {
  /** All replicas are actively exporting and committing their position. */
  EXPORTING,
  /** All replicas are paused, nothing is being exported. */
  PAUSED,
  /** All replicas keep exporting but do not commit their position. */
  SOFT_PAUSED,
  /** Replicas report different phases, so the tenant is in no single well-defined phase. */
  MIXED;

  /**
   * Aggregates the phases reported by the individual replicas: a single phase if they all agree,
   * {@link #MIXED} otherwise.
   */
  public static ExportingStatus aggregate(final Set<String> reportedPhases) {
    if (reportedPhases.size() != 1) {
      return MIXED;
    }

    final var phase = reportedPhases.iterator().next();
    return switch (phase) {
      case "EXPORTING" -> EXPORTING;
      case "PAUSED" -> PAUSED;
      case "SOFT_PAUSED" -> SOFT_PAUSED;
      default -> MIXED;
    };
  }
}
