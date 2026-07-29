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
  /**
   * The tenant is in no single well-defined phase: either the replicas report different phases, or
   * at least one of them reports a phase this gateway does not recognise.
   */
  MIXED;

  /**
   * Aggregates the phases reported by the individual replicas: a single phase if they all agree on
   * one this gateway recognises, {@link #MIXED} otherwise.
   *
   * <p>An unrecognised phase yields {@link #MIXED} rather than a dedicated status because {@link
   * #MIXED} already carries the only meaning a caller can safely act on -- "this is not a confirmed
   * pause". Distinguishing it would oblige every caller to handle a second indistinguishable
   * non-answer for no gain in what they can do about it. In practice the branch is unreachable: the
   * phase is read from the replica's persisted partition state, which only ever holds {@code
   * EXPORTING}, {@code PAUSED} or {@code SOFT_PAUSED} -- the broker's fourth {@code ExporterPhase},
   * {@code CLOSED}, is held by the exporter director and never persisted. It is kept so a future
   * phase added on the broker side degrades to a safe answer instead of being mistaken for a pause
   * on an older gateway.
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
