/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.ExponentialBackoffRetryDelay;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives a single pending {@code PhasedChangePlan}'s advancement through its phases, one instance
 * per plan id. Owns its own {@link ExponentialBackoffRetryDelay} so a repeated failure advancing
 * this plan cannot push another concurrently-pending plan's retry delay toward the max the way a
 * single shared backoff instance would.
 *
 * <p>Fixes the "two independent triggers both observe the same completed phase and both enqueue an
 * advance" race: {@link #advancing} is set the moment an advance/complete action is enqueued and
 * cleared only once it resolves, so a second trigger arriving while the first is still in flight is
 * a no-op here. As a second, independent safety net (e.g. across a coordinator handoff, where two
 * different {@code PlanAdvancer} instances on two different members could each decide to act),
 * {@code CurrentClusterConfiguration#tryActivateNextPhase(long, int)} and {@code
 * CurrentClusterConfiguration#tryCompletePlan(long, int, PhasedChangePlanStatus, int)} re-validate
 * the plan is still at the expected phase before mutating it, turning a stale/duplicate action into
 * a no-op rather than a double-advance or an exception.
 */
@NullMarked
final class PlanAdvancer {

  private static final Logger LOG = LoggerFactory.getLogger(PlanAdvancer.class);

  private final long planId;
  private final ConcurrencyControl executor;
  private final Function<
          UnaryOperator<CurrentClusterConfiguration>, ActorFuture<CurrentClusterConfiguration>>
      submitUpdate;
  private final Supplier<CurrentClusterConfiguration> currentConfiguration;
  private final BooleanSupplier isLocalMemberCoordinator;
  private final int completedChangeHistoryLimit;
  private final ExponentialBackoffRetryDelay backoff;

  private boolean advancing;
  private @Nullable ScheduledTimer retryTimer;

  PlanAdvancer(
      final long planId,
      final ConcurrencyControl executor,
      final Function<
              UnaryOperator<CurrentClusterConfiguration>, ActorFuture<CurrentClusterConfiguration>>
          submitUpdate,
      final Supplier<CurrentClusterConfiguration> currentConfiguration,
      final BooleanSupplier isLocalMemberCoordinator,
      final Duration minRetryDelay,
      final Duration maxRetryDelay,
      final int completedChangeHistoryLimit) {
    this.planId = planId;
    this.executor = executor;
    this.submitUpdate = submitUpdate;
    this.currentConfiguration = currentConfiguration;
    this.isLocalMemberCoordinator = isLocalMemberCoordinator;
    this.completedChangeHistoryLimit = completedChangeHistoryLimit;
    backoff = new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay);
  }

  /**
   * Advances the plan to its next phase, or completes it, if {@code config} shows its current phase
   * as fully drained. A no-op if an action for this plan is already in flight, the plan is no
   * longer pending, its current phase has not (yet) drained, or the local member is no longer the
   * coordinator.
   *
   * <p>The coordinator check is re-evaluated on every call, not just the first: this instance can
   * outlive the local member's coordinator status (e.g. a membership change hands coordination to
   * another member while this plan's advance is in flight, or while a retry is scheduled), and both
   * the success-continuation and the retry callback below call back into this method directly,
   * bypassing whatever coordinator check gated the original external trigger.
   */
  void maybeAdvance(final CurrentClusterConfiguration config) {
    if (advancing || !isLocalMemberCoordinator.getAsBoolean()) {
      return;
    }
    final var plan = config.phasedChangeState().pending().get(planId);
    if (plan == null || !config.isCurrentPhaseComplete(planId)) {
      return;
    }

    advancing = true;
    final int expectedPhaseIndex = plan.currentPhaseIndex();
    final var future =
        plan.hasNextPhase()
            ? submitUpdate.apply(c -> c.tryActivateNextPhase(planId, expectedPhaseIndex))
            : submitUpdate.apply(
                c ->
                    c.tryCompletePlan(
                        planId,
                        expectedPhaseIndex,
                        PhasedChangePlanStatus.COMPLETED,
                        completedChangeHistoryLimit));
    future.onComplete(
        (ignore, error) -> {
          advancing = false;
          if (error != null) {
            LOG.warn("Failed to advance phased change plan '{}' to next phase", planId, error);
            scheduleRetry();
            return;
          }
          backoff.reset();
          if (retryTimer != null) {
            retryTimer.cancel();
            retryTimer = null;
          }
          // The phase just activated (or the plan just completed) may itself already be fully
          // drained by the time this callback runs: applying it can complete synchronously as a
          // side effect of persisting the update above (see ScopeReconciler), and that completion
          // arrived while this instance was still marked advancing, so it was ignored (see the
          // dedup guard above). Re-checking here against the latest config is what picks it back
          // up, instead of leaving the plan stuck until some unrelated later trigger happens to
          // reconcile it.
          maybeAdvance(currentConfiguration.get());
        });
  }

  private void scheduleRetry() {
    if (retryTimer != null) {
      return;
    }
    retryTimer =
        executor.schedule(
            backoff.nextDelay(),
            () -> {
              // Cleared before firing (not in maybeAdvance's completion callback), so a repeat
              // failure can schedule a fresh timer instead of finding a stale, already-fired one
              // still occupying this field.
              retryTimer = null;
              maybeAdvance(currentConfiguration.get());
            });
  }
}
