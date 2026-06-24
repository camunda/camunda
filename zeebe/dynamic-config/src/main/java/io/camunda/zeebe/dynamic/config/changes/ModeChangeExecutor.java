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
 * Performs the local, broker-side switch of a member's partition manager between normal processing
 * and recovery.
 *
 * <p>Invoked by the mode change appliers while a cluster configuration change is being applied. The
 * cluster configuration is only updated to the new mode after the returned future completes
 * successfully; a failed future aborts the change and leaves the member in its previous mode.
 *
 * <p>Implementations must be idempotent: requesting a transition to a mode the member is already in
 * must complete successfully without further side effects, so that a configuration change can be
 * safely retried.
 */
public interface ModeChangeExecutor {

  /** Transitions a member into recovery mode. */
  ActorFuture<Void> enterRecovery();

  /** Transitions a member out of recovery mode and back into processing. */
  ActorFuture<Void> exitRecovery();

  final class NoopModeChangeExecutor implements ModeChangeExecutor {
    @Override
    public ActorFuture<Void> enterRecovery() {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> exitRecovery() {
      return CompletableActorFuture.completed(null);
    }
  }
}
