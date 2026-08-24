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
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link GraphScopeReconciler} directly, with a fake {@link GraphScopeReconciler.Scope}
 * and a controllable {@code updateLocally} collaborator, rather than through the whole {@link
 * ClusterConfigurationManagerImpl}. This is what makes the "persist after a successful apply can
 * fail too" path independently verifiable — every manager-level test gives the persist step a real,
 * always-succeeding {@code PersistedCurrentClusterConfiguration}, so that failure branch is
 * otherwise never exercised.
 *
 * <p>The fake scope stands in for both real ones: the global configuration and a partition group
 * differ only in where their plan lives and which applier runs an operation, which is exactly what
 * {@link GraphScopeReconciler.Scope} abstracts. The orchestration under test is shared by both.
 */
final class GraphScopeReconcilerTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");

  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final TopologyManagerMetrics topologyMetrics =
      new TopologyManagerMetrics(new SimpleMeterRegistry());

  private static DependencyChangePlan planWith(final int operations) {
    final var builder = OperationGraph.builder();
    for (int i = 0; i < operations; i++) {
      // No edges: every operation is runnable at once, which is what the concurrency cap bounds.
      builder.add(new MemberJoinOperation(MEMBER_0));
    }
    return DependencyChangePlan.init(1, builder.build());
  }

  private GraphScopeReconciler reconciler(
      final GraphScopeReconciler.Scope scope,
      final CurrentClusterConfiguration config,
      final Function<CurrentClusterConfiguration, Either<Exception, CurrentClusterConfiguration>>
          updateLocally,
      final TestConcurrencyControl concurrency) {
    return new GraphScopeReconciler(
        scope,
        MEMBER_0,
        () -> config,
        updateLocally,
        concurrency,
        topologyMetrics,
        Duration.ofMillis(1),
        Duration.ofMillis(1));
  }

  @Test
  void shouldRetryWhenPersistingASuccessfullyAppliedOperationFails() {
    // given — a fake scope with one runnable operation, and an updateLocally collaborator whose
    // second call (the persist of the *recorded* config, right after the operation's remote apply
    // succeeded) fails once before succeeding
    final var appliedCount = new AtomicInteger();
    final var persistAttempts = new AtomicInteger();
    final var config = CurrentClusterConfiguration.init();
    final var plan = planWith(1);

    final var scope =
        scope(
            plan,
            () ->
                operation(
                    ignored -> {
                      appliedCount.incrementAndGet();
                      return CompletableActorFuture.completed(UnaryOperator.identity());
                    }));

    final var updateLocally =
        (Function<CurrentClusterConfiguration, Either<Exception, CurrentClusterConfiguration>>)
            c -> {
              // The 2nd call is the persist of the recorded config right after a successful apply
              // (the 1st call is staging/"init", the 3rd is the retried pass's own init, the 4th is
              // the retried pass's own post-apply persist).
              if (persistAttempts.incrementAndGet() == 2) {
                return Either.left(new IOException("disk full"));
              }
              return Either.right(c);
            };

    // when
    reconciler(scope, config, updateLocally, executor).reconcile();

    // then — the remote operation was re-applied once the persist failure forced a full retry
    // (retrying just the persist, without redoing the apply, would require tracking more state
    // than this module currently does — see GraphScopeReconciler#onApplied), and that retry's own
    // persist succeeded, so the failure was not silently swallowed
    assertThat(appliedCount).hasValue(2);
    assertThat(persistAttempts).hasValue(4);
  }

  @Test
  void shouldRetryApplyFailureMultipleTimesBeforeSucceeding() {
    // given — a fake operation whose apply() fails twice (e.g. the applier's own "not ready yet"
    // retryable check) before succeeding on the third attempt
    final var applyAttempts = new AtomicInteger();
    final var config = CurrentClusterConfiguration.init();
    final var scope = failingUntilSuccess(applyAttempts, 2);

    // when — with the default (synchronous) TestConcurrencyControl, each scheduled retry runs
    // inline, so one top-level reconcile() call drives the whole failure/retry sequence
    reconciler(scope, config, Either::right, executor).reconcile();

    // then — two failed attempts, then a third that succeeded; the failure was not swallowed and
    // retries did not stop after the first one
    assertThat(applyAttempts).hasValue(3);
  }

  @Test
  void shouldPickUpOperationWhenExternalTriggerArrivesBeforeScheduledRetryFires() {
    // given — an async-scheduling executor, so a retry scheduled after a failure is queued rather
    // than run inline, leaving a real window for another trigger to race it (e.g. the coordinator's
    // own local-apply callback landing while a peer's gossip echo is also pending)
    final var asyncExecutor = new TestConcurrencyControl(true);
    final var applyAttempts = new AtomicInteger();
    final var config = CurrentClusterConfiguration.init();
    final var scope = failingUntilSuccess(applyAttempts, 1);
    final var reconciler = reconciler(scope, config, Either::right, asyncExecutor);

    // when — the first attempt fails; its retry is scheduled but not yet run
    reconciler.reconcile();
    assertThat(applyAttempts).hasValue(1);
    assertThat(asyncExecutor.scheduledTasks()).isEqualTo(1);

    // and — an external trigger calls reconcile() directly, before the scheduled retry fires
    reconciler.reconcile();

    // then — the external trigger's own call already retried and succeeded
    assertThat(applyAttempts).hasValue(2);

    // and — when the stale scheduled retry eventually fires too, it finds the scope with no change
    // left and is a harmless no-op, not a second application of the same operation
    final var tasksRun = asyncExecutor.runAll();
    assertThat(tasksRun).isEqualTo(1);
    assertThat(applyAttempts).hasValue(2);
  }

  @Test
  void shouldNotStartASecondAttemptOnTheSameOperationWhileOneIsInFlight() {
    // given — apply() returns a future that does not resolve on its own, simulating an in-flight
    // asynchronous remote operation (e.g. waiting on a partition role change)
    final var applyAttempts = new AtomicInteger();
    final var pendingFuture =
        new AtomicReference<CompletableActorFuture<UnaryOperator<CurrentClusterConfiguration>>>();
    final var config = CurrentClusterConfiguration.init();
    final var scope =
        scope(
            planWith(1),
            () ->
                operation(
                    ignored -> {
                      applyAttempts.incrementAndGet();
                      final var future =
                          new CompletableActorFuture<UnaryOperator<CurrentClusterConfiguration>>();
                      pendingFuture.set(future);
                      return future;
                    }));
    final var reconciler = reconciler(scope, config, Either::right, executor);

    // when — the first reconcile() call starts an attempt that never resolves on its own, and a
    // second trigger (e.g. a concurrent gossip receipt) arrives while it's still in flight
    reconciler.reconcile();
    reconciler.reconcile();

    // then — only the first attempt actually called apply(); the second call skipped the operation
    // it already had in flight
    assertThat(applyAttempts).hasValue(1);

    // and — once the in-flight attempt resolves, the operation is startable again
    pendingFuture.get().complete(UnaryOperator.identity());
    reconciler.reconcile();
    assertThat(applyAttempts).hasValue(2);
  }

  @Test
  void shouldStartSeveralUnorderedOperationsUpToTheConcurrencyCap() {
    // given — six operations with no edges between them, so the graph declares all six runnable at
    // once, each with an apply() that never resolves on its own
    final var started = new ArrayList<OperationId>();
    final var config = CurrentClusterConfiguration.init();
    final var scope =
        scope(
            planWith(6),
            () ->
                operation(
                    operationId -> {
                      started.add(operationId);
                      return new CompletableActorFuture<>();
                    }));

    // when
    reconciler(scope, config, Either::right, executor).reconcile();

    // then — the broker takes on only as many as its own cap allows. The graph says what may run
    // together; how much of that one broker starts at once is a policy knob, so this asserts the
    // cap is applied at all rather than pinning its value.
    assertThat(started).hasSizeLessThan(6).isNotEmpty();
    assertThat(started).doesNotHaveDuplicates();
  }

  /** A scope whose plan is {@code plan} until {@code plan} is exhausted by the fake operations. */
  private static GraphScopeReconciler.Scope scope(
      final DependencyChangePlan plan, final Supplier<GraphScopeReconciler.Operation> operations) {
    return new GraphScopeReconciler.Scope() {
      @Override
      public DependencyChangePlan plan(final CurrentClusterConfiguration config) {
        return plan;
      }

      @Override
      public long versionOf(final CurrentClusterConfiguration config) {
        return 0;
      }

      @Override
      public String describe() {
        return "fake scope";
      }

      @Override
      public Optional<GraphScopeReconciler.Operation> operationFor(
          final ClusterConfigurationChangeOperation operation) {
        return Optional.of(operations.get());
      }
    };
  }

  private static GraphScopeReconciler.Operation operation(
      final Function<OperationId, ActorFuture<UnaryOperator<CurrentClusterConfiguration>>> apply) {
    return new GraphScopeReconciler.Operation() {
      @Override
      public Either<Exception, CurrentClusterConfiguration> initialize(
          final CurrentClusterConfiguration config) {
        return Either.right(config);
      }

      @Override
      public ActorFuture<UnaryOperator<CurrentClusterConfiguration>> apply(
          final OperationId operationId) {
        return apply.apply(operationId);
      }
    };
  }

  /**
   * A scope whose single operation fails {@code failuresBeforeSuccess} times before succeeding, and
   * which stops offering a change at all once it has succeeded — i.e. it models a scope that
   * genuinely has nothing left pending once resolved, the same as a real sub-configuration once its
   * change plan drains and is cleared.
   */
  private static GraphScopeReconciler.Scope failingUntilSuccess(
      final AtomicInteger applyAttempts, final int failuresBeforeSuccess) {
    final var succeeded = new AtomicBoolean(false);
    final var plan = planWith(1);
    return new GraphScopeReconciler.Scope() {
      @Override
      public DependencyChangePlan plan(final CurrentClusterConfiguration config) {
        return succeeded.get() ? null : plan;
      }

      @Override
      public long versionOf(final CurrentClusterConfiguration config) {
        return 0;
      }

      @Override
      public String describe() {
        return "fake scope";
      }

      @Override
      public Optional<GraphScopeReconciler.Operation> operationFor(
          final ClusterConfigurationChangeOperation operation) {
        return Optional.of(
            operation(
                ignored -> {
                  final int attempt = applyAttempts.incrementAndGet();
                  if (attempt <= failuresBeforeSuccess) {
                    return CompletableActorFuture.completedExceptionally(
                        new IllegalStateException("not ready yet"));
                  }
                  succeeded.set(true);
                  return CompletableActorFuture.completed(UnaryOperator.identity());
                }));
      }
    };
  }
}
