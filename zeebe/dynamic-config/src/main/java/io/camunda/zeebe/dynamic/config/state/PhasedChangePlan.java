/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A coordinator-generated plan that drives a multi-partition-group reconfiguration in sequential
 * phases. Each phase is either a {@link ClusterMembershipPhase} (broker joins/leaves) or a {@link
 * PartitionGroupParallelPhase} (partition operations across one or more groups that run
 * concurrently with each other).
 */
public record PhasedChangePlan(
    long id,
    Instant startedAt,
    int currentPhaseIndex,
    List<Phase> phases,
    CompletedChange lastChange) { // nullable — null means no completed change yet

  public PhasedChangePlan {
    phases = List.copyOf(phases);
  }

  public static PhasedChangePlan init(final long id, final List<Phase> phases) {
    return new PhasedChangePlan(id, Instant.now(), 0, phases, null);
  }

  public PhasedChangePlan withNextPhase() {
    return new PhasedChangePlan(id, startedAt, currentPhaseIndex + 1, phases, lastChange);
  }

  public boolean hasNextPhase() {
    return currentPhaseIndex + 1 < phases.size();
  }

  public Phase currentPhase() {
    return phases.get(currentPhaseIndex);
  }

  public PhasedChangePlan merge(final PhasedChangePlan other) {
    if (id == other.id) {
      // Same plan: furthest-advanced phase index wins
      return currentPhaseIndex >= other.currentPhaseIndex ? this : other;
    }
    // Different plan IDs: take the more advanced to avoid regressing execution
    if (currentPhaseIndex != other.currentPhaseIndex) {
      return currentPhaseIndex > other.currentPhaseIndex ? this : other;
    }
    // Equal phase index across different IDs: higher plan ID is more recent coordinator decision
    return id > other.id ? this : other;
  }

  public sealed interface Phase permits ClusterMembershipPhase, PartitionGroupParallelPhase {}

  public record ClusterMembershipPhase(List<ClusterConfigurationChangeOperation> operations)
      implements Phase {
    public ClusterMembershipPhase {
      operations = List.copyOf(operations);
    }
  }

  public record PartitionGroupParallelPhase(
      Map<String, List<ClusterConfigurationChangeOperation>> operationsPerGroup) implements Phase {
    public PartitionGroupParallelPhase {
      operationsPerGroup =
          operationsPerGroup.entrySet().stream()
              .collect(
                  Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }
  }
}
