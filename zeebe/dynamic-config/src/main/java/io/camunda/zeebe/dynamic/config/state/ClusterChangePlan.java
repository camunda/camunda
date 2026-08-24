/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import io.atomix.cluster.MemberId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the ongoing cluster configuration changes. The pendingOperations are executed
 * sequentially. Only after completing one operation, the next operation is started. Once an
 * operation is completed, it should be removed from the plan, so that the next operation can be
 * picked up.
 *
 * <p>version starts at 1 and increments every time an operation is completed and removed from the
 * pending operations. This helps to choose the latest state of the configuration change when
 * receiving gossip update out of order.
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

  /**
   * Renders any change as this queue. A queue is returned as itself; a {@link DependencyChangePlan}
   * flattens into its pending and completed operations in plan order — a valid, merely slower,
   * sequential reading of the same change.
   *
   * <p>Necessarily lossy: a queue cannot express that several operations are running at once, and a
   * reader that merges by {@link #version()} alone would keep only one of two concurrent
   * completions. That is a property of this model, not something the flattening can repair, so
   * nothing here tries to.
   *
   * <p><b>Only call this where the queue shape is actually required</b> — today that is encoding
   * the legacy {@code ClusterTopology} message, which a broker without the graph model reads from
   * gossip and from a v1 configuration file, and whose {@code currentChange} field is typed as this
   * record. A receiver on that path merges what it decodes with {@link #merge}, using the version
   * above, and would execute the flattened queue one operation at a time. Every consumer that only
   * reads a change should take a {@link ChangePlan} instead and see the real one.
   */
  public static ClusterChangePlan flatten(final ChangePlan plan) {
    if (plan instanceof final ClusterChangePlan queue) {
      return queue;
    }
    return new ClusterChangePlan(
        plan.id(),
        plan.version(),
        plan.status(),
        plan.startedAt(),
        plan.completedOperations(),
        plan.pendingOperations());
  }

  public boolean isRestore() {
    return id == RESTORE_CHANGE_ID;
  }

  /** To be called when the first operation is completed. */
  ClusterChangePlan advance() {
    // List#subList hold on to the original list. Make a copy to prevent a potential memory leak.
    final var nextPendingOperations =
        List.copyOf(pendingOperations.subList(1, pendingOperations.size()));
    final var newCompletedOperations = new ArrayList<>(completedOperations);
    newCompletedOperations.add(new CompletedOperation(pendingOperations.get(0), Instant.now()));
    return new ClusterChangePlan(
        id, version + 1, status, startedAt(), newCompletedOperations, nextPendingOperations);
  }

  CompletedChange completed() {
    return new CompletedChange(id, Status.COMPLETED, startedAt(), Instant.now());
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

  public boolean hasPendingChangesFor(final MemberId memberId) {
    return !pendingOperations.isEmpty() && pendingOperations.get(0).memberId().equals(memberId);
  }

  public ClusterConfigurationChangeOperation nextPendingOperation() {
    return pendingOperations().get(0);
  }

  public boolean hasPendingChanges() {
    return !pendingOperations().isEmpty();
  }

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
