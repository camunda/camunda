/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link PlanAdvancer} directly, with its collaborators faked, rather than through the
 * whole {@link ClusterConfigurationManagerImpl}. This is what makes the in-flight dedup guard (the
 * actual fix for "a phase can start before the previous one finishes") independently verifiable:
 * the test controls exactly when the submitted update resolves, so it can assert that a second
 * trigger arriving while the first is still in flight is rejected, deterministically.
 *
 * <p>Several tests below assert on the fake {@code submitUpdate} collaborator's invocation count
 * rather than on the resulting {@link CurrentClusterConfiguration}'s state. That's deliberate, not
 * a shortcut: the dedup guard's whole purpose is to prevent a call from happening at all, and
 * {@link CurrentClusterConfiguration#tryActivateNextPhase(long, int)}/{@link
 * CurrentClusterConfiguration#tryCompletePlan} already make a redundant call a no-op on the
 * resulting state — so a broken dedup guard that let a second call through would still converge to
 * the same final state, and a state-only assertion could not tell the two apart. The call count is
 * the only observable that actually distinguishes "the guard fired" from "the guard didn't fire but
 * the model-layer re-validation caught it anyway".
 */
final class PlanAdvancerTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");

  private final TestConcurrencyControl executor = new TestConcurrencyControl();

  /**
   * Completes every operation of the global phase's change and clears the drained plan — what the
   * broker applying them would leave behind, without any of their effects on member state. The plan
   * has to be cleared too, not merely drained: {@code isCurrentPhaseComplete} treats a drained but
   * still-present plan as incomplete.
   */
  private static CurrentClusterConfiguration drainCurrentPhase(
      final CurrentClusterConfiguration config) {
    var current = config;
    while (current.globalConfiguration().hasPendingChanges()) {
      final var plan = current.globalConfiguration().pendingChanges().orElseThrow();
      final var next =
          plan.operations().keySet().stream().filter(plan::isRunnable).findFirst().orElseThrow();
      current =
          current.updateGlobalConfiguration(
              g -> g.completeOperation(next, UnaryOperator.identity()));
    }
    return current.updateGlobalConfiguration(GlobalConfiguration::completeGraphChangeIfDrained);
  }

  @Test
  void shouldSubmitExactlyOneAdvanceForTwoConcurrentTriggers() {
    // given — a two-phase plan whose current phase (phase 0) has fully drained; two independent
    // triggers (e.g. the coordinator's own local-apply callback and a peer's gossip echo) are
    // about to both observe "phase complete" for the same plan
    final var initialized =
        CurrentClusterConfiguration.init()
            .initPlan(
                List.of(
                    new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0))),
                    new GlobalPhase(List.of(new MemberLeaveOperation(MEMBER_0)))));
    final var planId = initialized.phasedChangeState().onlyPending().id();
    final var drained = drainCurrentPhase(initialized);

    final var submissions = new AtomicInteger();
    final var pendingFuture =
        new AtomicReference<CompletableActorFuture<CurrentClusterConfiguration>>();
    final var currentConfig = new AtomicReference<>(drained);
    final var advancer =
        new PlanAdvancer(
            planId,
            executor,
            updater -> {
              submissions.incrementAndGet();
              final var future = new CompletableActorFuture<CurrentClusterConfiguration>();
              pendingFuture.set(future);
              return future;
            },
            currentConfig::get,
            () -> true,
            Duration.ofMillis(1),
            Duration.ofMillis(1),
            10);

    // when — both triggers fire before the first submitted action resolves
    advancer.maybeAdvance(drained);
    advancer.maybeAdvance(drained);

    // then — only the first trigger actually submitted an update; the second was a no-op
    assertThat(submissions).hasValue(1);

    // when — the in-flight action resolves: the plan moved to phase 1, and phase 1 happens to
    // already be fully drained by the time this callback runs (the exact scenario that used to
    // strand the plan — see PlanAdvancer's completion callback)
    final var advancedToPhase1 = drained.tryActivateNextPhase(planId, 0);
    final var phase1Drained = drainCurrentPhase(advancedToPhase1);
    currentConfig.set(phase1Drained);
    pendingFuture.get().complete(advancedToPhase1);

    // then — resolving cleared the dedup guard, and the callback's own re-check against the
    // latest config picked up the already-drained phase 1 and completed the plan, without
    // needing a further external trigger
    assertThat(submissions).hasValue(2);
  }

  @Test
  void shouldNoOpWhenCurrentPhaseHasNotDrained() {
    // given — a plan whose current phase still has a pending operation
    final var initialized =
        CurrentClusterConfiguration.init()
            .initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0)))));
    final var planId = initialized.phasedChangeState().onlyPending().id();

    final var submissions = new AtomicInteger();
    final var advancer =
        new PlanAdvancer(
            planId,
            executor,
            updater -> {
              submissions.incrementAndGet();
              return new CompletableActorFuture<>();
            },
            () -> initialized,
            () -> true,
            Duration.ofMillis(1),
            Duration.ofMillis(1),
            10);

    // when
    advancer.maybeAdvance(initialized);

    // then — nothing submitted; the phase has not drained yet
    assertThat(submissions).hasValue(0);
  }

  @Test
  void shouldNoOpWhenPlanIsNoLongerPending() {
    // given — a plan id that was never issued (nothing pending for it)
    final var config = CurrentClusterConfiguration.init();
    final var submissions = new AtomicInteger();
    final var advancer =
        new PlanAdvancer(
            1L,
            executor,
            updater -> {
              submissions.incrementAndGet();
              return new CompletableActorFuture<>();
            },
            () -> config,
            () -> true,
            Duration.ofMillis(1),
            Duration.ofMillis(1),
            10);

    // when
    advancer.maybeAdvance(config);

    // then
    assertThat(submissions).hasValue(0);
  }

  @Test
  void shouldNoOpWhenLocalMemberIsNotCoordinator() {
    // given — a plan whose current phase has fully drained, but the local member is not (or is no
    // longer) the coordinator
    final var initialized =
        CurrentClusterConfiguration.init()
            .initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0)))));
    final var planId = initialized.phasedChangeState().onlyPending().id();
    final var drained = drainCurrentPhase(initialized);

    final var submissions = new AtomicInteger();
    final var advancer =
        new PlanAdvancer(
            planId,
            executor,
            updater -> {
              submissions.incrementAndGet();
              return new CompletableActorFuture<>();
            },
            () -> drained,
            () -> false,
            Duration.ofMillis(1),
            Duration.ofMillis(1),
            10);

    // when
    advancer.maybeAdvance(drained);

    // then — nothing submitted, even though the phase is complete
    assertThat(submissions).hasValue(0);
  }

  @Test
  void shouldNotSelfContinueAfterLosingCoordinatorStatusMidFlight() {
    // given — a two-phase plan whose phase 0 has drained; the local member starts out as
    // coordinator, but stops being one before the in-flight advance resolves (e.g. a membership
    // change hands coordination to another member)
    final var initialized =
        CurrentClusterConfiguration.init()
            .initPlan(
                List.of(
                    new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0))),
                    new GlobalPhase(List.of(new MemberLeaveOperation(MEMBER_0)))));
    final var planId = initialized.phasedChangeState().onlyPending().id();
    final var drained = drainCurrentPhase(initialized);

    final var submissions = new AtomicInteger();
    final var pendingFuture =
        new AtomicReference<CompletableActorFuture<CurrentClusterConfiguration>>();
    final var currentConfig = new AtomicReference<>(drained);
    final var isCoordinator = new AtomicReference<>(Boolean.TRUE);
    final var advancer =
        new PlanAdvancer(
            planId,
            executor,
            updater -> {
              submissions.incrementAndGet();
              final var future = new CompletableActorFuture<CurrentClusterConfiguration>();
              pendingFuture.set(future);
              return future;
            },
            currentConfig::get,
            isCoordinator::get,
            Duration.ofMillis(1),
            Duration.ofMillis(1),
            10);

    // when — the advance is submitted while still coordinator, but resolves (with phase 1 already
    // drained too) only after coordinator status is lost
    advancer.maybeAdvance(drained);
    assertThat(submissions).hasValue(1);

    final var advancedToPhase1 = drained.tryActivateNextPhase(planId, 0);
    final var phase1Drained = drainCurrentPhase(advancedToPhase1);
    currentConfig.set(phase1Drained);
    isCoordinator.set(false);
    pendingFuture.get().complete(advancedToPhase1);

    // then — the self-continuation re-checks coordinator status and does not submit the
    // otherwise-ready completion
    assertThat(submissions).hasValue(1);
  }

  @Test
  void shouldRetryOnFailureUntilSuccess() {
    // given — a single-phase plan whose only phase has drained, so maybeAdvance will submit a
    // completePlan; the first two submissions fail (e.g. a transient persist error) before the
    // third succeeds. currentConfig is updated on every successful submission — as the real
    // manager's updateMultiConfiguration would — so the self-continuation after success sees the
    // plan genuinely resolved instead of recursing forever against a config that never changes.
    final var initialized =
        CurrentClusterConfiguration.init()
            .initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0)))));
    final var planId = initialized.phasedChangeState().onlyPending().id();
    final var drained = drainCurrentPhase(initialized);
    final var currentConfig = new AtomicReference<>(drained);

    final var attempts = new AtomicInteger();
    final var advancer =
        new PlanAdvancer(
            planId,
            executor,
            updater -> {
              if (attempts.incrementAndGet() <= 2) {
                return TestActorFuture.failedFuture(new RuntimeException("transient failure"));
              }
              final var updated = updater.apply(currentConfig.get());
              currentConfig.set(updated);
              return TestActorFuture.completedFuture(updated);
            },
            currentConfig::get,
            () -> true,
            Duration.ofMillis(1),
            Duration.ofMillis(1),
            10);

    // when — with the default (synchronous) TestConcurrencyControl, each scheduled retry runs
    // inline, so one top-level call drives the whole failure/retry sequence to completion
    advancer.maybeAdvance(currentConfig.get());

    // then — two failed submissions, then a third that succeeded; the failure did not stop
    // retries, and did not leave the advancer permanently marked as in-flight
    assertThat(attempts).hasValue(3);
    assertThat(currentConfig.get().phasedChangeState().pending()).isEmpty();
  }

  @Test
  void shouldPickUpAdvanceWhenExternalTriggerArrivesBeforeScheduledRetryFires() {
    // given — an async-scheduling executor, so the retry scheduled after a failure is queued
    // rather than run inline, leaving a real window for another trigger (e.g. a peer's gossip
    // echo of the same completed phase) to race it
    final var asyncExecutor = new TestConcurrencyControl(true);
    final var initialized =
        CurrentClusterConfiguration.init()
            .initPlan(List.of(new GlobalPhase(List.of(new MemberJoinOperation(MEMBER_0)))));
    final var planId = initialized.phasedChangeState().onlyPending().id();
    final var drained = drainCurrentPhase(initialized);
    final var currentConfig = new AtomicReference<>(drained);

    final var attempts = new AtomicInteger();
    final var advancer =
        new PlanAdvancer(
            planId,
            asyncExecutor,
            updater -> {
              if (attempts.incrementAndGet() == 1) {
                return TestActorFuture.failedFuture(new RuntimeException("transient failure"));
              }
              final var updated = updater.apply(currentConfig.get());
              currentConfig.set(updated);
              return TestActorFuture.completedFuture(updated);
            },
            currentConfig::get,
            () -> true,
            Duration.ofMillis(1),
            Duration.ofMillis(1),
            10);

    // when — the first advance attempt fails; its retry is scheduled but not yet run
    advancer.maybeAdvance(currentConfig.get());
    assertThat(attempts).hasValue(1);
    assertThat(asyncExecutor.scheduledTasks()).isEqualTo(1);

    // and — an external trigger calls maybeAdvance() directly, before the scheduled retry fires
    advancer.maybeAdvance(currentConfig.get());

    // then — the external trigger's own call already retried and succeeded, and — unlike
    // ScopeReconciler, which has no retryTimer to cancel — the success path here explicitly
    // cancels the now-stale scheduled retry rather than leaving it to fire as a no-op later
    assertThat(attempts).hasValue(2);
    assertThat(currentConfig.get().phasedChangeState().pending()).isEmpty();
    assertThat(asyncExecutor.scheduledTasks()).isZero();
    assertThat(asyncExecutor.runAll()).isZero();
    assertThat(attempts).hasValue(2);
  }
}
