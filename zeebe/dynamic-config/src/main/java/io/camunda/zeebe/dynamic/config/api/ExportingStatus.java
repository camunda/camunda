/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.state.ExportingState;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
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
   * Aggregates the states of the individual replicas: a single status if they all agree, {@link
   * #MIXED} otherwise. {@link ExportingState#UNKNOWN} means the replica has never been touched by a
   * state-change operation, which is equivalent to actively exporting.
   */
  public static ExportingStatus aggregate(final Collection<ExportingState> replicaStates) {
    return aggregatePhases(
        replicaStates.stream()
            .map(state -> state == ExportingState.UNKNOWN ? ExportingState.EXPORTING : state)
            .map(Enum::name)
            .collect(Collectors.toSet()));
  }

  /**
   * Throws on a phase this enum does not know rather than folding it into {@link #MIXED}: a phase
   * added to the broker's {@code ExporterPhase} would otherwise be reported as a benign "pause
   * still in flight" forever, and the mismatch would never surface. Failing the request makes it a
   * visible error instead. This enum is not the only thing that would need updating anyway -- a new
   * phase also needs a status to map onto in the REST response.
   */
  private static ExportingStatus aggregatePhases(final Set<String> reportedPhases) {
    if (reportedPhases.size() != 1) {
      return MIXED;
    }

    final var phase = reportedPhases.iterator().next();
    return switch (phase) {
      case "EXPORTING" -> EXPORTING;
      case "PAUSED" -> PAUSED;
      case "SOFT_PAUSED" -> SOFT_PAUSED;
      default ->
          throw new IllegalArgumentException(
              "Expected a broker replica to report a known exporter phase, but got '%s'"
                  .formatted(phase));
    };
  }
}
