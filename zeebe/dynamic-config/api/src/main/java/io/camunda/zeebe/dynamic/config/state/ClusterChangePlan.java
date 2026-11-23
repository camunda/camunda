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

/**
 * A change as a queue of pending operations, run one at a time, plus the ones already completed.
 *
 * <p><b>Nothing creates or executes this any more.</b> Every scope — the global configuration and
 * every partition group — runs a {@link DependencyChangePlan} instead. What survives here is the
 * nested {@link Status} and {@link CompletedOperation} types, which the rest of the model still
 * uses.
 *
 * <p>{@code version} starts at 1 and increments once per completed operation.
 */
public record ClusterChangePlan(
    long id,
    int version,
    Status status,
    Instant startedAt,
    List<CompletedOperation> completedOperations,
    List<ClusterConfigurationChangeOperation> pendingOperations)
    implements ChangePlan {

  private static final long RESTORE_CHANGE_ID = -2L;

  public ClusterChangePlan {
    completedOperations = List.copyOf(completedOperations);
    pendingOperations = List.copyOf(pendingOperations);
  }

  public static ClusterChangePlan init(
      final long id, final List<ClusterConfigurationChangeOperation> operations) {
    return new ClusterChangePlan(
        id, 1, Status.IN_PROGRESS, Instant.now(), List.of(), List.copyOf(operations));
  }

  public static ClusterChangePlan initForRestore(
      final List<ClusterConfigurationChangeOperation> operations) {
    return init(RESTORE_CHANGE_ID, operations);
  }

  public boolean isRestore() {
    return id == RESTORE_CHANGE_ID;
  }

  public ClusterChangePlan merge(final ClusterChangePlan other) {
    // Pick the highest version
    if (other == null) {
      return this;
    }
    if (other.version > version) {
      return other;
    }
    return this;
  }

  @Override
  public boolean hasPendingChanges() {
    return !pendingOperations().isEmpty();
  }

  @Override
  public CompletedChange cancel() {
    return new CompletedChange(id, Status.CANCELLED, startedAt(), Instant.now());
  }

  public record CompletedOperation(
      ClusterConfigurationChangeOperation operation, Instant completedAt) {}

  public enum Status {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED;
  }
}
