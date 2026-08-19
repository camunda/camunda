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
 * <p>Two models coexist while the dependency-graph execution is proven out:
 *
 * <ul>
 *   <li>{@link ClusterChangePlan} — a queue, one broker at a time. Every transformer produces this
 *       unless it has opted in, and it is unchanged by the coexistence.
 *   <li>{@link DependencyChangePlan} — operations with declared dependencies, executed concurrently
 *       wherever no edge orders them.
 * </ul>
 *
 * <p>Deliberately read-only. Starting, advancing and merging a change is model-specific and stays
 * on the concrete types, so the sub-configuration switches on which one it holds rather than
 * pretending the two are interchangeable. Reporting — the REST change view, restore status, metrics
 * — does not care, and reads through here.
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
}
