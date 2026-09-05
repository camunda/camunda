/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * What every consumer outside the execution loop needs from an ongoing change, whichever execution
 * model it uses.
 *
 * <p>Two models coexist:
 *
 * <ul>
 *   <li>{@link ClusterChangePlan} — a queue, one broker at a time. What the global configuration
 *       runs, and the shape the legacy single-group {@link ClusterConfiguration} is encoded as on
 *       the wire a broker without the graph model reads.
 *   <li>{@link DependencyChangePlan} — operations with declared dependencies, executed concurrently
 *       wherever no edge orders them. What every partition group runs.
 * </ul>
 *
 * <p>Deliberately read-only. Starting, advancing and merging a change is model-specific and stays
 * on the concrete types, so the sub-configuration switches on which one it holds rather than
 * pretending the two are interchangeable. Reporting — the REST change view, restore status, metrics
 * — does not care, and reads through here. So does the legacy {@link ClusterConfiguration}, which
 * carries whichever model the sub-configuration it projects is running and only flattens to a queue
 * where the wire demands one (see {@link ClusterChangePlan#flatten(ChangePlan)}).
 *
 * <p>{@link ClusterChangePlan} implements this without any other change: it already had every
 * method below. That is the whole cost of coexistence on the retiring path.
 */
@NullMarked
public sealed interface ChangePlan permits ClusterChangePlan, DependencyChangePlan {

  /** The id of this change, as issued by the coordinator. */
  long id();

  Status status();

  Instant startedAt();

  /** Whether any operation of this change is still outstanding. */
  boolean hasPendingChanges();

  /** The operations still to run, in a stable order two brokers both derive identically. */
  List<ClusterConfigurationChangeOperation> pendingOperations();

  /** The operations already run, in the same stable order. */
  List<CompletedOperation> completedOperations();

  CompletedChange cancel();

  /**
   * How far the change has got, as one plus the number of operations already run. On a {@link
   * ClusterChangePlan} that is exactly its {@code version} field, which starts at 1 and increments
   * once per completed operation; the record component overrides this.
   *
   * <p>For rendering only. On a queue the value also decides a merge, and a graph has no such
   * ordering to offer: two brokers running disjoint operations of the same graph concurrently both
   * report a version their peer has never held, and picking the higher one would discard the
   * other's completion. A graph converges by unioning completions instead — see {@link
   * DependencyChangePlan#merge}.
   */
  default int version() {
    return 1 + completedOperations().size();
  }
}
