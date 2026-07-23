/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

/**
 * Performs the local, broker-side steps of an in-process restore of a single partition while the
 * member is in recovery mode. Invoked by the restore appliers while the restore cluster
 * configuration change is being applied.
 *
 * <p>Implementations must be idempotent: every step may be retried until it succeeds.
 */
public interface RestoreChangeExecutor {

  /** Drops the member's local disk data for {@code partitionId}, preparing it for a restore. */
  ActorFuture<Void> preRestore(int partitionId);

  /** No-op executor for plan simulation, where operations must succeed without side effects. */
  final class NoopRestoreChangeExecutor implements RestoreChangeExecutor {

    @Override
    public ActorFuture<Void> preRestore(final int partitionId) {
      return CompletableActorFuture.completed(null);
    }
  }

  /**
   * Default executor registered outside recovery mode; fails every restore step because a restore
   * plan must only execute while the cluster is recovering.
   */
  final class DeniedRestoreChangeExecutor implements RestoreChangeExecutor {

    @Override
    public ActorFuture<Void> preRestore(final int partitionId) {
      return denied();
    }

    private static ActorFuture<Void> denied() {
      return CompletableActorFuture.completedExceptionally(
          new IllegalStateException(
              "Restore operations are only supported while the broker is in recovery mode"));
    }
  }
}
