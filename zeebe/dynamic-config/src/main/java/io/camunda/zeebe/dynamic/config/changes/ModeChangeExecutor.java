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
import java.util.Set;

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
   * Completes once every local partition has reached the role expected for {@code mode} - and, when
   * entering recovery, once health has been reported for each (closing a race between
   * RecoveryPartitionManager's role and health reporting). Completes immediately if no transition
   * is in flight (e.g. the member was already in {@code mode}). A failed check is propagated so the
   * cluster change can be retried.
   *
   * @return the subset of local partitions confirmed ready for {@code mode}: for {@code
   *     PROCESSING}, every role-confirmed partition; for {@code RECOVERING}, only those also
   *     reported healthy (a partition reported DEAD still lets the operation complete but is
   *     excluded from this set).
   */
  ActorFuture<Set<Integer>> awaitModeApplied(Mode mode);

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
    public ActorFuture<Set<Integer>> awaitModeApplied(final Mode mode) {
      return CompletableActorFuture.completed(Set.of());
    }
  }
}
