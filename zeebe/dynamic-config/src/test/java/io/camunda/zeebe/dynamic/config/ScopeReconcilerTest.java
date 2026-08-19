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
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link ScopeReconciler} directly, with a fake {@link ScopeReconciler.Operations} and a
 * controllable {@code updateLocally} collaborator, rather than through the whole {@link
 * ClusterConfigurationManagerImpl}. This is what makes the "persist after a successful apply can
 * fail too" path independently verifiable — every manager-level test gives the persist step a real,
 * always-succeeding {@code PersistedCurrentClusterConfiguration}, so that failure branch is
 * otherwise never exercised.
 */
final class ScopeReconcilerTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");

  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final TopologyManagerMetrics topologyMetrics =
      new TopologyManagerMetrics(new SimpleMeterRegistry());

  @Test
  void shouldRetryWhenPersistingASuccessfullyAppliedOperationFails() {
    // given — a fake scope with one always-available pending operation, and an updateLocally
    // collaborator whose second call (the persist of the *advanced* config, right after the
    // operation's remote apply succeeded) fails once before succeeding
    final var appliedCount = new AtomicInteger();
    final var persistAttempts = new AtomicInteger();
    final var config = CurrentClusterConfiguration.init();

    final ScopeReconciler.Operations operations =
        new ScopeReconciler.Operations() {
          @Override
          public Optional<ScopeReconciler.Operation> nextOperation(
              final CurrentClusterConfiguration currentConfiguration) {
            return Optional.of(
                new ScopeReconciler.Operation() {
                  @Override
                  public ClusterConfigurationChangeOperation operation() {
                    return new MemberJoinOperation(MEMBER_0);
                  }

                  @Override
                  public Either<Exception, CurrentClusterConfiguration> initialize(
                      final CurrentClusterConfiguration currentConfiguration) {
                    return Either.right(currentConfiguration);
                  }

                  @Override
                  public ActorFuture<UnaryOperator<CurrentClusterConfiguration>> apply() {
                    appliedCount.incrementAndGet();
                    return CompletableActorFuture.completed(UnaryOperator.identity());
                  }
                });
          }

          @Override
          public long versionOf(final CurrentClusterConfiguration currentConfiguration) {
            return 0;
          }

          @Override
          public String describe() {
            return "fake";
          }
        };

    final var updateLocally =
        (Function<CurrentClusterConfiguration, Either<Exception, CurrentClusterConfiguration>>)
            c -> {
              // The 2nd call is the persist of the advanced config right after a successful
              // apply (the 1st call is staging/"init", the 3rd is the retried pass's own init,
              // the 4th is the retried pass's own post-apply persist).
              if (persistAttempts.incrementAndGet() == 2) {
                return Either.left(new IOException("disk full"));
              }
              return Either.right(c);
            };

    final var reconciler =
        new ScopeReconciler(
            operations,
            () -> config,
            updateLocally,
            executor,
            topologyMetrics,
            Duration.ofMillis(1),
            Duration.ofMillis(1));

    // when
    reconciler.reconcile();

    // then — the remote operation was re-applied once the persist failure forced a full retry
    // (retrying just the persist, without redoing the apply, would require tracking more state
    // than this module currently does — see ScopeReconciler#onApplied), and that retry's own
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
    final var operations = alwaysAvailableOperation(applyAttempts, 2);

    final var reconciler =
        new ScopeReconciler(
            operations,
            () -> config,
            c -> Either.right(c),
            executor,
            topologyMetrics,
            Duration.ofMillis(1),
            Duration.ofMillis(1));

    // when — with the default (synchronous) TestConcurrencyControl, each scheduled retry runs
    // inline, so one top-level reconcile() call drives the whole failure/retry sequence
    reconciler.reconcile();

    // then — two failed attempts, then a third that succeeded; the failure was not swallowed and
    // retries did not stop after the first one
    assertThat(applyAttempts).hasValue(3);
  }

  @Test
  void shouldPickUpOperationWhenExternalTriggerArrivesBeforeScheduledRetryFires() {
    // given — an async-scheduling executor, so a retry scheduled after a failure is queued
    // rather than run inline, leaving a real window for another trigger to race it (e.g. the
    // coordinator's own local-apply callback landing while a peer's gossip echo is also pending)
    final var asyncExecutor = new TestConcurrencyControl(true);
    final var applyAttempts = new AtomicInteger();
    final var config = CurrentClusterConfiguration.init();
    final var operations = alwaysAvailableOperation(applyAttempts, 1);

    final var reconciler =
        new ScopeReconciler(
            operations,
            () -> config,
            c -> Either.right(c),
            asyncExecutor,
            topologyMetrics,
            Duration.ofMillis(1),
            Duration.ofMillis(1));

    // when — the first attempt fails; its retry is scheduled but not yet run
    reconciler.reconcile();
    assertThat(applyAttempts).hasValue(1);
    assertThat(asyncExecutor.scheduledTasks()).isEqualTo(1);

    // and — an external trigger calls reconcile() directly, before the scheduled retry fires
    reconciler.reconcile();

    // then — the external trigger's own call already retried and succeeded
    assertThat(applyAttempts).hasValue(2);

    // and — when the stale scheduled retry eventually fires too, it finds nothing left pending
    // (nextOperation() returns empty) and is a harmless no-op, not a second application of the
    // same operation
    final var tasksRun = asyncExecutor.runAll();
    assertThat(tasksRun).isEqualTo(1);
    assertThat(applyAttempts).hasValue(2);
  }

  @Test
  void shouldNotStartASecondAttemptWhileOneIsInFlight() {
    // given — apply() returns a future that does not resolve on its own, simulating an in-flight
    // asynchronous remote operation (e.g. waiting on a partition role change)
    final var applyAttempts = new AtomicInteger();
    final var pendingFuture =
        new AtomicReference<CompletableActorFuture<UnaryOperator<CurrentClusterConfiguration>>>();
    final var config = CurrentClusterConfiguration.init();

    final ScopeReconciler.Operations operations =
        new ScopeReconciler.Operations() {
          @Override
          public Optional<ScopeReconciler.Operation> nextOperation(
              final CurrentClusterConfiguration currentConfiguration) {
            return Optional.of(
                new ScopeReconciler.Operation() {
                  @Override
                  public ClusterConfigurationChangeOperation operation() {
                    return new MemberJoinOperation(MEMBER_0);
                  }

                  @Override
                  public Either<Exception, CurrentClusterConfiguration> initialize(
                      final CurrentClusterConfiguration currentConfiguration) {
                    return Either.right(currentConfiguration);
                  }

                  @Override
                  public ActorFuture<UnaryOperator<CurrentClusterConfiguration>> apply() {
                    applyAttempts.incrementAndGet();
                    final var future =
                        new CompletableActorFuture<UnaryOperator<CurrentClusterConfiguration>>();
                    pendingFuture.set(future);
                    return future;
                  }
                });
          }

          @Override
          public long versionOf(final CurrentClusterConfiguration currentConfiguration) {
            return 0;
          }

          @Override
          public String describe() {
            return "fake";
          }
        };

    final var reconciler =
        new ScopeReconciler(
            operations,
            () -> config,
            c -> Either.right(c),
            executor,
            topologyMetrics,
            Duration.ofMillis(1),
            Duration.ofMillis(1));

    // when — the first reconcile() call starts an attempt that never resolves on its own, and a
    // second trigger (e.g. a concurrent gossip receipt) arrives while it's still in flight
    reconciler.reconcile();
    reconciler.reconcile();

    // then — only the first attempt actually called apply(); the second call was a no-op
    assertThat(applyAttempts).hasValue(1);

    // and — once the in-flight attempt resolves, the guard clears
    pendingFuture.get().complete(UnaryOperator.identity());
    reconciler.reconcile();
    assertThat(applyAttempts).hasValue(2);
  }

  /**
   * An {@link ScopeReconciler.Operations} whose single operation fails {@code
   * failuresBeforeSuccess} times before succeeding, and which stops offering an operation at all
   * once it has succeeded — i.e. it models a scope that genuinely has nothing left pending once
   * resolved, the same as a real {@code PartitionGroupConfiguration} once its change plan drains.
   */
  private static ScopeReconciler.Operations alwaysAvailableOperation(
      final AtomicInteger applyAttempts, final int failuresBeforeSuccess) {
    final var succeeded = new AtomicBoolean(false);
    return new ScopeReconciler.Operations() {
      @Override
      public Optional<ScopeReconciler.Operation> nextOperation(
          final CurrentClusterConfiguration currentConfiguration) {
        if (succeeded.get()) {
          return Optional.empty();
        }
        return Optional.of(
            new ScopeReconciler.Operation() {
              @Override
              public ClusterConfigurationChangeOperation operation() {
                return new MemberJoinOperation(MEMBER_0);
              }

              @Override
              public Either<Exception, CurrentClusterConfiguration> initialize(
                  final CurrentClusterConfiguration currentConfiguration) {
                return Either.right(currentConfiguration);
              }

              @Override
              public ActorFuture<UnaryOperator<CurrentClusterConfiguration>> apply() {
                final int attempt = applyAttempts.incrementAndGet();
                if (attempt <= failuresBeforeSuccess) {
                  return CompletableActorFuture.completedExceptionally(
                      new IllegalStateException("not ready yet"));
                }
                succeeded.set(true);
                return CompletableActorFuture.completed(UnaryOperator.identity());
              }
            });
      }

      @Override
      public long versionOf(final CurrentClusterConfiguration currentConfiguration) {
        return 0;
      }

      @Override
      public String describe() {
        return "fake";
      }
    };
  }
}
