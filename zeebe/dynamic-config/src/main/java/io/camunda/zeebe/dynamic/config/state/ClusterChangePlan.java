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
 * A change as the legacy single-group configuration represents it: a queue of pending operations,
 * run one at a time, plus the ones already completed.
 *
 * <p><b>Nothing executes this any more.</b> Every scope — the global configuration and every
 * partition group — runs a {@link DependencyChangePlan} instead, so what survives here is the shape
 * the legacy {@code ClusterTopology} message can carry, and only that: a broker without the graph
 * model reads that message from gossip and from a v1 configuration file, and its {@code
 * currentChange} field is typed as this record. A live change is rendered into it by {@link
 * #flatten(ChangePlan)} at that encode, and decoding it is the only way one comes back.
 *
 * <p>{@code version} starts at 1 and increments once per completed operation, which is what a
 * broker on that legacy path merges by ({@link #merge}) to choose between two copies it sees out of
 * order.
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
    // Dependency order, not id order: the receiver executes this queue head-first, so an id
    // ordering that happens not to be topological would make it run an operation before one the
    // graph says it waits for. Every graph the builder produces is already in topological id
    // order; one decoded from the wire is whatever the sender wrote.
    final var graph = (DependencyChangePlan) plan;
    return new ClusterChangePlan(
        graph.id(),
        graph.version(),
        graph.status(),
        graph.startedAt(),
        graph.completedOperations(),
        graph.pendingOperationsInDependencyOrder());
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
