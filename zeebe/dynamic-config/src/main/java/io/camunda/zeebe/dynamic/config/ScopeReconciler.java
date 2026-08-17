/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics.OperationObserver;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.ExponentialBackoffRetryDelay;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives ongoing/retry/backoff for the local member's next pending operation within a single
 * reconciliation scope (the global configuration, or one named partition group). One instance per
 * scope, each with its own {@link ExponentialBackoffRetryDelay}, so a repeated failure applying one
 * scope's operation cannot push another scope's retry delay toward the max the way a single shared
 * backoff instance would (see {@link ClusterConfigurationManagerImpl}, which used to keep three
 * parallel {@code Map}s for this per group, plus two more scalar fields for the global case).
 *
 * <p>{@link Operations} supplies the scope-specific pieces (finding the local member's pending
 * operation, and staging/applying it) so this class only implements the orchestration that is
 * otherwise identical between the global and per-group cases.
 *
 * <p>Staging an operation ({@code initialize().flatMap(updateLocally)}) persists/gossips the staged
 * config synchronously, which re-enters {@link ClusterConfigurationManagerImpl#reconcile} — and
 * therefore every registered {@code ScopeReconciler}'s {@link #reconcile()}, including this one —
 * before that call returns. The {@code ongoing} flag (set before staging, not after) turns that
 * re-entrant call into a no-op rather than a recursive one, so this doesn't deepen with the number
 * of operations processed; it's bounded only by the number of scopes that simultaneously have a
 * ready operation, which is fine at the scope counts (global plus one per physical tenant) this
 * module deals with today.
 */
@NullMarked
final class ScopeReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(ScopeReconciler.class);

  private final Operations operations;
  private final Supplier<CurrentClusterConfiguration> currentConfiguration;
  private final Function<
          CurrentClusterConfiguration, Either<Exception, CurrentClusterConfiguration>>
      updateLocally;
  private final ConcurrencyControl executor;
  private final TopologyManagerMetrics topologyMetrics;
  private final ExponentialBackoffRetryDelay backoff;

  private boolean ongoing;

  ScopeReconciler(
      final Operations operations,
      final Supplier<CurrentClusterConfiguration> currentConfiguration,
      final Function<CurrentClusterConfiguration, Either<Exception, CurrentClusterConfiguration>>
          updateLocally,
      final ConcurrencyControl executor,
      final TopologyManagerMetrics topologyMetrics,
      final Duration minRetryDelay,
      final Duration maxRetryDelay) {
    this.operations = operations;
    this.currentConfiguration = currentConfiguration;
    this.updateLocally = updateLocally;
    this.executor = executor;
    this.topologyMetrics = topologyMetrics;
    backoff = new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay);
  }

  /**
   * Applies the local member's next pending operation in this scope, if any and if none is already
   * in flight. A no-op if an operation is already ongoing.
   */
  void reconcile() {
    if (ongoing) {
      return;
    }
    final var config = currentConfiguration.get();
    final var next = operations.nextOperation(config);
    if (next.isEmpty()) {
      return;
    }

    // Set before staging the operation (not in the branches below): staging calls back into
    // updateLocally, which re-invokes reconcile() on every scope, including this one — this flag
    // is what makes that re-entrant call a no-op instead of a second concurrent attempt.
    ongoing = true;
    final var op = next.get();
    final var observer = topologyMetrics.observeOperation(op.operation());
    LOG.info(
        "Applying {} configuration change operation {}", operations.describe(), op.operation());
    final var initialized = op.initialize(config).flatMap(updateLocally);
    if (initialized.isLeft()) {
      observer.failed();
      ongoing = false;
      LOG.error(
          "Failed to initialize {} configuration change operation {}",
          operations.describe(),
          op.operation(),
          initialized.getLeft());
      return;
    }

    final var startedConfiguration = initialized.get();
    final var startedVersion = operations.versionOf(startedConfiguration);
    op.apply()
        .onComplete(
            (transformer, error) ->
                onApplied(op.operation(), transformer, error, startedVersion, observer));
  }

  private void onApplied(
      final ClusterConfigurationChangeOperation operation,
      // Declared non-null to match ActorFuture#onComplete's callback type (V, @Nullable
      // Throwable): only dereferenced below once error == null is confirmed, which is the only
      // branch where the future actually completed with a value.
      final UnaryOperator<CurrentClusterConfiguration> transformer,
      final @Nullable Throwable error,
      final long startedVersion,
      final OperationObserver observer) {
    ongoing = false;
    if (error != null) {
      observer.failed();
      final Duration delay = backoff.nextDelay();
      LOG.warn(
          "Failed to apply {} configuration change operation {}. Will be retried in {}.",
          operations.describe(),
          operation,
          delay,
          error);
      executor.schedule(delay, this::reconcile);
      return;
    }

    observer.applied();
    final var config = currentConfiguration.get();
    if (operations.versionOf(config) != startedVersion) {
      // The remote operation itself succeeded (observer.applied() above already reflects that);
      // only the local advance is moot because the scope changed underneath it (most likely the
      // change was cancelled). Reset the backoff here too, or a scope that failed a few times
      // before this cancellation would face an inflated delay on its next, unrelated failure.
      backoff.reset();
      LOG.debug(
          "{} changed while applying operation {}. Most likely the change was cancelled.",
          operations.describe(),
          operation);
      return;
    }
    final var advanced = transformer.apply(config);
    // Persisting/gossiping the advanced config re-triggers reconciliation across every scope and
    // plan (see ClusterConfigurationManagerImpl#reconcile), which is what picks up a newly-drained
    // phase's next operation — no separate "run the whole loop again" call is needed here.
    final var persisted = updateLocally.apply(advanced);
    if (persisted.isLeft()) {
      // The remote operation already succeeded; only the local persist/gossip of that fact
      // failed. Retrying from reconcile() re-derives and re-applies the same still-pending
      // operation from scratch — the same path a crash between the remote apply and the local
      // persist would already force this module to tolerate, so no separate "retry just the
      // persist" mechanism is needed here.
      final Duration delay = backoff.nextDelay();
      LOG.warn(
          "Failed to persist {} configuration change operation {} after applying it. Will be"
              + " retried in {}.",
          operations.describe(),
          operation,
          delay,
          persisted.getLeft());
      executor.schedule(delay, this::reconcile);
      return;
    }
    backoff.reset();
    LOG.info("{} operation {} applied.", operations.describe(), operation);
  }

  /** Scope-specific strategy for finding and staging the local member's next pending operation. */
  interface Operations {

    Optional<Operation> nextOperation(CurrentClusterConfiguration config);

    /** The sub-configuration's own version, used to detect a concurrent cancel/change. */
    long versionOf(CurrentClusterConfiguration config);

    /**
     * Human-readable scope name for logs, e.g. {@code "global"} or {@code "partition group 'x'"}.
     */
    String describe();
  }

  /** A single in-flight application of one pending operation, found by {@link Operations}. */
  interface Operation {

    ClusterConfigurationChangeOperation operation();

    /** Validates and stages the operation into the affected sub-configuration. */
    Either<Exception, CurrentClusterConfiguration> initialize(CurrentClusterConfiguration config);

    /**
     * Performs the operation, completing with a transform that advances the affected
     * sub-configuration once it succeeds.
     */
    ActorFuture<UnaryOperator<CurrentClusterConfiguration>> apply();
  }
}
