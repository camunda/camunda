/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics.OperationObserver;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
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
 * Drives one partition group's change, the per-group counterpart of {@link ScopeReconciler} (which
 * drives the global configuration, and only that).
 *
 * <p>The difference that makes this a separate class rather than a mode of {@link ScopeReconciler}:
 * the global configuration's queue offers the local member exactly one operation at a time, so a
 * single {@code ongoing} flag is enough to keep re-entrant reconciliation idempotent. A group's
 * graph offers every operation whose dependencies have completed — typically several — so in-flight
 * state and retry backoff are keyed per operation instead (see {@link OperationKey}). Everything
 * else (read the config fresh, stage, apply, record, persist, re-reconcile) mirrors {@link
 * ScopeReconciler} deliberately.
 */
@NullMarked
final class GraphScopeReconciler {

  /**
   * How many of a group's runnable operations one broker starts at once.
   *
   * <p>A runtime policy knob, not a correctness bound: the graph already says what <em>may</em> run
   * together, and this caps what this broker chooses to. Restore is I/O bound, so whether running
   * several partition restores at once on one broker actually helps is a question to settle by
   * measurement.
   */
  private static final int MAX_CONCURRENT_OPERATIONS = 4;

  private static final Logger LOG = LoggerFactory.getLogger(GraphScopeReconciler.class);

  private final String groupId;
  private final MemberId localMemberId;
  private final Supplier<CurrentClusterConfiguration> currentConfiguration;
  private final Function<
          PartitionGroupOperation, Optional<PartitionGroupConfigurationChangeApplier>>
      applierFor;
  private final Function<
          CurrentClusterConfiguration, Either<Exception, CurrentClusterConfiguration>>
      updateLocally;
  private final ConcurrencyControl executor;
  private final TopologyManagerMetrics topologyMetrics;
  private final Duration minRetryDelay;
  private final Duration maxRetryDelay;

  private final Set<OperationKey> inFlight = new HashSet<>();
  private final Map<OperationKey, ExponentialBackoffRetryDelay> backoffs = new HashMap<>();

  /**
   * @param applierFor resolves the applier for one operation, or {@link Optional#empty()} to leave
   *     it pending. Resolution is per operation rather than per group so that a {@code
   *     RemovePhysicalTenantOperation} — dispatchable with no registered appliers at all, since it
   *     is a pure configuration edit — is not blocked by this group having none.
   */
  GraphScopeReconciler(
      final String groupId,
      final MemberId localMemberId,
      final Supplier<CurrentClusterConfiguration> currentConfiguration,
      final Function<PartitionGroupOperation, Optional<PartitionGroupConfigurationChangeApplier>>
          applierFor,
      final Function<CurrentClusterConfiguration, Either<Exception, CurrentClusterConfiguration>>
          updateLocally,
      final ConcurrencyControl executor,
      final TopologyManagerMetrics topologyMetrics,
      final Duration minRetryDelay,
      final Duration maxRetryDelay) {
    this.groupId = groupId;
    this.localMemberId = localMemberId;
    this.currentConfiguration = currentConfiguration;
    this.applierFor = applierFor;
    this.updateLocally = updateLocally;
    this.executor = executor;
    this.topologyMetrics = topologyMetrics;
    this.minRetryDelay = minRetryDelay;
    this.maxRetryDelay = maxRetryDelay;
  }

  /**
   * Starts every operation of this group the local member may run right now and has not started.
   *
   * <p>Caps on {@code inFlight.size()}, not on a count local to this call. Staging an operation (in
   * {@link #start}) persists synchronously, which re-enters this method before {@link #start}
   * returns — a local counter would reset to zero on each nested call and the cap would only ever
   * bound one nesting level, not the group as a whole. {@code inFlight} is the one piece of state
   * every nesting level shares, so it is what the cap has to read.
   */
  void reconcile() {
    final var group = currentConfiguration.get().partitionGroup(groupId);
    if (group == null) {
      forgetOperationsOutside(null);
      return;
    }
    final var plan = group.pendingChanges().orElse(null);
    if (plan == null) {
      forgetOperationsOutside(null);
      return;
    }
    forgetOperationsOutside(plan.id());

    for (final var entry : group.runnableFor(localMemberId).entrySet()) {
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
   * meaning the group has no change at all).
   *
   * <p>This is the only thing that reclaims that state when the plan it belongs to goes away.
   * {@code PartitionGroupConfiguration#cancelPendingChanges()} — the operator's last resort for a
   * stuck change — is a pure transformation of the persisted configuration and cannot reach these
   * in-memory maps. The per-operation completion path cannot be relied on either: it only runs once
   * the operation's {@code apply()} future completes, and a genuinely hung operation (precisely
   * what gets a change cancelled) never gets there. Without this, that operation's entry would hold
   * one of {@link #MAX_CONCURRENT_OPERATIONS} slots for the lifetime of the process.
   */
  private void forgetOperationsOutside(final @Nullable Long planId) {
    inFlight.removeIf(key -> planId == null || key.planId() != planId);
    backoffs.keySet().removeIf(key -> planId == null || key.planId() != planId);
  }

  /**
   * Leaves the operation pending — neither in-flight nor retried on a timer — if {@link
   * #applierFor} resolves nothing for it: stalling visibly on a genuinely-unregistered operation,
   * rather than fabricating a no-op success for it.
   */
  private void start(final OperationKey key, final PartitionGroupOperation operation) {
    final var operationId = key.operationId();
    final var resolvedApplier = applierFor.apply(operation);
    if (resolvedApplier.isEmpty()) {
      return;
    }
    // Read the configuration fresh for each operation rather than once for the loop above: staging
    // persists within this turn, so a hoisted read would make the second operation of a batch
    // overwrite the first one's staged state.
    final var config = currentConfiguration.get();
    final var group = config.partitionGroup(groupId);
    if (group == null) {
      return;
    }

    inFlight.add(key);
    final var observer = topologyMetrics.observeOperation(operation);
    LOG.info("Applying partition group '{}' operation {} ({})", groupId, operationId, operation);

    final var applier = resolvedApplier.get();
    final var initialized =
        applier
            .init(config.globalConfiguration(), group)
            .map(transformer -> config.updatePartitionGroupConfig(groupId, transformer))
            .flatMap(updateLocally);
    if (initialized.isLeft()) {
      observer.failed();
      inFlight.remove(key);
      LOG.error(
          "Failed to initialize partition group '{}' operation {}",
          groupId,
          operation,
          initialized.getLeft());
      return;
    }

    final var startedVersion = versionOf(initialized.get());
    applier
        .apply()
        .onComplete(
            (transformer, error) ->
                onApplied(key, operation, transformer, error, startedVersion, observer));
  }

  private void onApplied(
      final OperationKey key,
      final PartitionGroupOperation operation,
      final UnaryOperator<PartitionGroupConfiguration> transformer,
      final @Nullable Throwable error,
      final long startedVersion,
      final OperationObserver observer) {
    inFlight.remove(key);

    if (error != null) {
      observer.failed();
      final Duration delay =
          backoffs
              .computeIfAbsent(
                  key, ignored -> new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay))
              .nextDelay();
      LOG.warn(
          "Failed to apply partition group '{}' operation {}. Will be retried in {}.",
          groupId,
          operation,
          delay,
          error);
      // Only this operation waits; the group's other runnable operations are unaffected, which is
      // the point of keying in-flight state per operation.
      executor.schedule(delay, this::reconcile);
      return;
    }

    observer.applied();
    backoffs.remove(key);

    final var config = currentConfiguration.get();
    if (versionOf(config) != startedVersion) {
      // Recording an operation moves no version, so a version change here means the change was
      // cancelled or already completed while this operation was running. Nothing to clean up or
      // re-check: the key carries the plan id, so whatever plan runs on this group now cannot be
      // waiting on state this completion holds, and the update that started it already reconciled.
      LOG.debug(
          "Partition group '{}' changed while applying operation {}. Most likely the change was"
              + " cancelled.",
          groupId,
          operation);
      return;
    }

    final var advanced =
        config.updatePartitionGroupConfig(
            groupId,
            group ->
                group
                    .completeOperation(key.operationId(), transformer)
                    .completeGraphChangeIfDrained());
    // Persisting re-triggers reconciliation across every scope and plan, which is what picks up the
    // operations this completion just made runnable — no explicit "run the loop again" is needed.
    final var persisted = updateLocally.apply(advanced);
    if (persisted.isLeft()) {
      // The operation itself succeeded; only recording it locally failed. Retrying re-derives it
      // from the graph, which is safe because operations are idempotent.
      final Duration delay =
          backoffs
              .computeIfAbsent(
                  key, ignored -> new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay))
              .nextDelay();
      LOG.warn(
          "Applied partition group '{}' operation {} but failed to record it. Will be retried in"
              + " {}.",
          groupId,
          operation,
          delay,
          persisted.getLeft());
      executor.schedule(delay, this::reconcile);
      return;
    }
    LOG.info("Partition group '{}' operation {} applied.", groupId, operation);
  }

  private long versionOf(final CurrentClusterConfiguration config) {
    final var group = config.partitionGroup(groupId);
    return group == null ? -1 : group.version();
  }

  /**
   * Identifies one operation of one plan. The plan id is part of the key because {@code
   * OperationGraph.Builder} numbers every plan's operations from zero: keyed by {@link OperationId}
   * alone, an entry left behind by a cancelled plan would be indistinguishable from — and would
   * block — the same-numbered operation of whatever plan replaces it on this group.
   */
  private record OperationKey(long planId, OperationId operationId) {}
}
