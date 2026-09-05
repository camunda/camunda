/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics.OperationObserver;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.ExponentialBackoffRetryDelay;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives one scope's change: the global configuration, or one partition group. Every scope's change
 * is a dependency graph, so there is one driver — {@link Scope} supplies the parts that differ
 * (which sub-configuration holds the plan, which applier runs an operation, how a completion is
 * recorded), and everything else here is shared.
 *
 * <p>In-flight state is keyed per operation rather than held as a single flag, because a graph
 * offers every operation whose dependencies have completed — typically several (see {@link
 * OperationKey}). The global configuration's graph is sequential and so only ever offers one, but
 * it runs through the same machinery: a driver that assumed one would have to be rewritten the
 * first time a cluster-wide change declares two independent operations, and two drivers for one
 * execution model is how the subtle parts below — re-entrancy, per-operation backoff, reclaiming
 * state from a cancelled plan — drift apart.
 *
 * <p>Staging an operation ({@code initialize().flatMap(updateLocally)}) persists/gossips
 * synchronously, which re-enters {@link ClusterConfigurationManagerImpl#reconcile} — and therefore
 * {@link #reconcile()} — before that call returns. {@code inFlight} is what turns that re-entrant
 * call into a no-op for operations already started, rather than a second concurrent attempt.
 */
@NullMarked
final class GraphScopeReconciler {

  /**
   * How many of a scope's runnable operations one broker starts at once.
   *
   * <p>A runtime policy knob, not a correctness bound: the graph already says what <em>may</em> run
   * together, and this caps what this broker chooses to. Restore is I/O bound, so whether running
   * several partition restores at once on one broker actually helps is a question to settle by
   * measurement.
   */
  private static final int MAX_CONCURRENT_OPERATIONS = 4;

  private static final Logger LOG = LoggerFactory.getLogger(GraphScopeReconciler.class);

  private final Scope scope;
  private final MemberId localMemberId;
  private final Supplier<CurrentClusterConfiguration> currentConfiguration;
  private final Function<
          CurrentClusterConfiguration, Either<Exception, CurrentClusterConfiguration>>
      updateLocally;
  private final ConcurrencyControl executor;
  private final TopologyManagerMetrics topologyMetrics;
  private final Duration minRetryDelay;
  private final Duration maxRetryDelay;

  private final Set<OperationKey> inFlight = new HashSet<>();
  private final Map<OperationKey, ExponentialBackoffRetryDelay> backoffs = new HashMap<>();

  GraphScopeReconciler(
      final Scope scope,
      final MemberId localMemberId,
      final Supplier<CurrentClusterConfiguration> currentConfiguration,
      final Function<CurrentClusterConfiguration, Either<Exception, CurrentClusterConfiguration>>
          updateLocally,
      final ConcurrencyControl executor,
      final TopologyManagerMetrics topologyMetrics,
      final Duration minRetryDelay,
      final Duration maxRetryDelay) {
    this.scope = scope;
    this.localMemberId = localMemberId;
    this.currentConfiguration = currentConfiguration;
    this.updateLocally = updateLocally;
    this.executor = executor;
    this.topologyMetrics = topologyMetrics;
    this.minRetryDelay = minRetryDelay;
    this.maxRetryDelay = maxRetryDelay;
  }

  /**
   * Starts every operation of this scope the local member may run right now and has not started.
   *
   * <p>Caps on {@code inFlight.size()}, not on a count local to this call. Staging an operation (in
   * {@link #start}) persists synchronously, which re-enters this method before {@link #start}
   * returns — a local counter would reset to zero on each nested call and the cap would only ever
   * bound one nesting level, not the scope as a whole. {@code inFlight} is the one piece of state
   * every nesting level shares, so it is what the cap has to read.
   */
  void reconcile() {
    final var plan = scope.plan(currentConfiguration.get());
    if (plan == null) {
      forgetOperationsOutside(null);
      return;
    }
    forgetOperationsOutside(plan.id());

    for (final var entry : plan.runnableFor(localMemberId).entrySet()) {
      final var key = new OperationKey(plan.id(), entry.getKey());
      if (inFlight.size() >= MAX_CONCURRENT_OPERATIONS) {
        break;
      }
      if (inFlight.contains(key)) {
        continue;
      }
      start(key, entry.getValue());
    }
  }

  /**
   * Drops in-flight and backoff state belonging to any plan other than {@code planId} ({@code null}
   * meaning the scope has no change at all).
   *
   * <p>This is the only thing that reclaims that state when the plan it belongs to goes away.
   * {@code cancelPendingChanges()} — the operator's last resort for a stuck change — is a pure
   * transformation of the persisted configuration and cannot reach these in-memory maps. The
   * per-operation completion path cannot be relied on either: it only runs once the operation's
   * {@code apply()} future completes, and a genuinely hung operation (precisely what gets a change
   * cancelled) never gets there. Without this, that operation's entry would hold one of {@link
   * #MAX_CONCURRENT_OPERATIONS} slots for the lifetime of the process.
   */
  private void forgetOperationsOutside(final @Nullable Long planId) {
    inFlight.removeIf(key -> planId == null || key.planId() != planId);
    backoffs.keySet().removeIf(key -> planId == null || key.planId() != planId);
  }

  /**
   * Leaves the operation pending — neither in-flight nor retried on a timer — if {@link
   * Scope#operationFor} resolves nothing for it: stalling visibly on a genuinely-unregistered
   * operation, rather than fabricating a no-op success for it.
   */
  private void start(final OperationKey key, final ClusterConfigurationChangeOperation operation) {
    final var resolved = scope.operationFor(operation);
    if (resolved.isEmpty()) {
      return;
    }
    // Read the configuration fresh for each operation rather than once for the loop above: staging
    // persists within this turn, so a hoisted read would make the second operation of a batch
    // overwrite the first one's staged state.
    final var config = currentConfiguration.get();

    inFlight.add(key);
    final var observer = topologyMetrics.observeOperation(operation);
    LOG.info("Applying {} operation {} ({})", scope.describe(), key.operationId(), operation);

    final var initialized = resolved.get().initialize(config).flatMap(updateLocally);
    if (initialized.isLeft()) {
      observer.failed();
      inFlight.remove(key);
      LOG.error(
          "Failed to initialize {} operation {}",
          scope.describe(),
          operation,
          initialized.getLeft());
      return;
    }

    final var startedVersion = scope.versionOf(initialized.get());
    resolved
        .get()
        .apply(key.operationId())
        .onComplete(
            (transformer, error) ->
                onApplied(key, operation, transformer, error, startedVersion, observer));
  }

  private void onApplied(
      final OperationKey key,
      final ClusterConfigurationChangeOperation operation,
      final UnaryOperator<CurrentClusterConfiguration> transformer,
      final @Nullable Throwable error,
      final long startedVersion,
      final OperationObserver observer) {
    inFlight.remove(key);

    if (error != null) {
      observer.failed();
      final Duration delay = backoffFor(key).nextDelay();
      LOG.warn(
          "Failed to apply {} operation {}. Will be retried in {}.",
          scope.describe(),
          operation,
          delay,
          error);
      // Only this operation waits; the scope's other runnable operations are unaffected, which is
      // the point of keying in-flight state per operation.
      executor.schedule(delay, this::reconcile);
      return;
    }

    observer.applied();

    final var config = currentConfiguration.get();
    if (scope.versionOf(config) != startedVersion) {
      // Recording an operation moves no version, so a version change here means the change was
      // cancelled or already completed while this operation was running. Nothing to clean up or
      // re-check: the key carries the plan id, so whatever plan runs on this scope now cannot be
      // waiting on state this completion holds, and the update that started it already reconciled.
      LOG.debug(
          "{} changed while applying operation {}. Most likely the change was cancelled.",
          scope.describe(),
          operation);
      return;
    }

    // Persisting re-triggers reconciliation across every scope and plan, which is what picks up the
    // operations this completion just made runnable — no explicit "run the loop again" is needed.
    final var persisted = updateLocally.apply(transformer.apply(config));
    if (persisted.isLeft()) {
      // The operation itself succeeded; only recording it locally failed. Retrying re-derives it
      // from the graph, which is safe because operations are idempotent.
      final Duration delay = backoffFor(key).nextDelay();
      LOG.warn(
          "Applied {} operation {} but failed to record it. Will be retried in {}.",
          scope.describe(),
          operation,
          delay,
          persisted.getLeft());
      executor.schedule(delay, this::reconcile);
      return;
    }
    backoffs.remove(key);
    LOG.info("{} operation {} applied.", scope.describe(), operation);
  }

  private ExponentialBackoffRetryDelay backoffFor(final OperationKey key) {
    return backoffs.computeIfAbsent(
        key, ignored -> new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay));
  }

  /**
   * Identifies one operation of one plan. The plan id is part of the key because {@code
   * OperationGraph.Builder} numbers every plan's operations from zero: keyed by {@link OperationId}
   * alone, an entry left behind by a cancelled plan would be indistinguishable from — and would
   * block — the same-numbered operation of whatever plan replaces it on this scope.
   */
  private record OperationKey(long planId, OperationId operationId) {}

  /** What one scope contributes to the shared driving loop above. */
  interface Scope {

    /**
     * This scope's change, or {@code null} if it has none — including if the scope itself is gone.
     */
    @Nullable DependencyChangePlan plan(CurrentClusterConfiguration config);

    /** The sub-configuration's own version, used to detect a concurrent cancel or completion. */
    long versionOf(CurrentClusterConfiguration config);

    /** Human-readable scope name for logs, e.g. {@code "global configuration"}. */
    String describe();

    /**
     * The applier bound to this operation, or {@link Optional#empty()} to leave it pending because
     * nothing is registered to run it yet.
     */
    Optional<Operation> operationFor(ClusterConfigurationChangeOperation operation);
  }

  /** A single in-flight application of one operation, bound to its applier by {@link Scope}. */
  interface Operation {

    /** Validates and stages the operation into the affected sub-configuration. */
    Either<Exception, CurrentClusterConfiguration> initialize(CurrentClusterConfiguration config);

    /**
     * Performs the operation, completing with a transform that applies its effect, records {@code
     * operationId} as complete against the scope's plan, and finishes the change if that was the
     * last operation outstanding.
     */
    ActorFuture<UnaryOperator<CurrentClusterConfiguration>> apply(OperationId operationId);
  }
}
