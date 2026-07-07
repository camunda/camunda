/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

/**
 * Performs the local, broker-side switch of a member's partition manager between normal processing
 * and recovery.
 *
 * <p>Invoked by the mode change appliers while a cluster configuration change is being applied. A
 * transition is applied in two steps so that a replicated cluster does not deadlock: {@link
 * #enterRecovery()} / {@link #exitRecovery()} stop the previous partition manager and
 * <em>initiate</em> the new one, completing as soon as the start is under way (the partitions of a
 * replicated group cannot elect a leader until a quorum of members have transitioned, which only
 * happens once every member has applied its mode change). {@link #awaitModeApplied(Mode)} is then
 * invoked, after every member has flipped, to wait for the local partitions to actually finish
 * starting.
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

  /**
   * Completes once the partition manager started by the most recent transition into {@code mode}
   * has finished starting. Completes immediately if no transition is in flight (e.g. the member was
   * already in {@code mode}). A failed start is propagated so the cluster change can be retried.
   */
  ActorFuture<Void> awaitModeApplied(Mode mode);

  final class NoopModeChangeExecutor implements ModeChangeExecutor {
    @Override
    public ActorFuture<Void> enterRecovery() {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> exitRecovery() {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> awaitModeApplied(final Mode mode) {
      return CompletableActorFuture.completed(null);
    }
  }
}
