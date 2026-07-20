/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

/**
 * Opens a partition's {@link PartitionRoleController} for the runtime ({@link
 * io.camunda.eventbridge.streaming.StreamRuntime}), when the application opts into the failover
 * lifecycle (event-bridge-streaming ADR 0009 decision 6 / consumer-groups ADR 0006 decision 1)
 * instead of the plain {@link io.camunda.eventbridge.streaming.TaskFactory} path.
 *
 * <p>There is a single entry point, {@link #startAsStandby(int)}, by design: the runtime always
 * materializes a partition by opening (or reusing) its controller in the STANDBY role and then, if
 * the partition is assigned ACTIVE, immediately calling {@link PartitionRoleController#promote()} —
 * the identical cold-rebuild-then-fold path for a brand-new active assignment and a caught-up
 * standby's promotion, exactly as {@link PartitionRoleController}'s class javadoc describes ("no
 * special case"). {@link PartitionRoleController#startAsActive} is deliberately never called from
 * the runtime; it remains available for a caller with its own non-rebalance-driven startup path.
 *
 * @param <R> the decoded record type the active {@link io.camunda.eventbridge.streaming.Task}
 *     consumes
 */
@FunctionalInterface
public interface PartitionRoleControllerFactory<R> {

  /**
   * Opens {@code partition}'s controller in the STANDBY role: an empty/fresh store warms from the
   * changelog start, an intact on-disk store resumes from its persisted position — both are the
   * controller's/applier's own concern, not the runtime's. Called at most once per partition per
   * materialization; the runtime reuses the same controller (and therefore the same open store)
   * across every subsequent role flip until the partition is fully revoked (no longer assigned in
   * any role).
   */
  PartitionRoleController<?, R> startAsStandby(int partition);
}
