/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliers;
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
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives one partition group's dependency-graph change, the counterpart of {@link ScopeReconciler}
 * for the other execution model.
 *
 * <p>The difference that makes this a separate class rather than a mode of {@link ScopeReconciler}:
 * a queue offers the local member exactly one operation at a time, so a single {@code ongoing} flag
 * is enough to keep re-entrant reconciliation idempotent. A graph offers every operation whose
 * dependencies have completed — typically several — so in-flight state and retry backoff are keyed
 * per {@link OperationId} instead. Everything else (read the config fresh, stage, apply, record,
 * persist, re-reconcile) mirrors {@link ScopeReconciler} deliberately.
 *
 * <p>A group holds one change of one model, so for any given group at most one of the two
 * reconcilers ever finds work.
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
  private final Supplier<@Nullable PartitionGroupConfigurationChangeAppliers> appliers;
  private final Function<
          CurrentClusterConfiguration, Either<Exception, CurrentClusterConfiguration>>
      updateLocally;
  private final ConcurrencyControl executor;
  private final TopologyManagerMetrics topologyMetrics;
  private final Duration minRetryDelay;
  private final Duration maxRetryDelay;

  private final Set<OperationId> inFlight = new HashSet<>();
  private final Map<OperationId, ExponentialBackoffRetryDelay> backoffs = new HashMap<>();

  GraphScopeReconciler(
      final String groupId,
      final MemberId localMemberId,
      final Supplier<CurrentClusterConfiguration> currentConfiguration,
      final Supplier<@Nullable PartitionGroupConfigurationChangeAppliers> appliers,
      final Function<CurrentClusterConfiguration, Either<Exception, CurrentClusterConfiguration>>
          updateLocally,
      final ConcurrencyControl executor,
      final TopologyManagerMetrics topologyMetrics,
      final Duration minRetryDelay,
      final Duration maxRetryDelay) {
    this.groupId = groupId;
    this.localMemberId = localMemberId;
    this.currentConfiguration = currentConfiguration;
    this.appliers = appliers;
    this.updateLocally = updateLocally;
    this.executor = executor;
    this.topologyMetrics = topologyMetrics;
    this.minRetryDelay = minRetryDelay;
    this.maxRetryDelay = maxRetryDelay;
  }

  /**
   * Starts every operation of this group the local member may run right now and has not started.
   */
  void reconcile() {
    final var group = currentConfiguration.get().partitionGroup(groupId);
    final var groupAppliers = appliers.get();
    if (group == null || groupAppliers == null) {
      return;
    }

    int started = 0;
    for (final var entry : group.runnableFor(localMemberId).entrySet()) {
      if (started >= MAX_CONCURRENT_OPERATIONS) {
        break;
      }
      if (inFlight.contains(entry.getKey())) {
        continue;
      }
      start(entry.getKey(), entry.getValue(), groupAppliers);
      started++;
    }
  }

  private void start(
      final OperationId operationId,
      final PartitionGroupOperation operation,
      final PartitionGroupConfigurationChangeAppliers groupAppliers) {
    // Read the configuration fresh for each operation rather than once for the loop above: staging
    // persists within this turn, so a hoisted read would make the second operation of a batch
    // overwrite the first one's staged state.
    final var config = currentConfiguration.get();
    final var group = config.partitionGroup(groupId);
    if (group == null) {
      return;
    }

    inFlight.add(operationId);
    final var observer = topologyMetrics.observeOperation(operation);
    LOG.info("Applying partition group '{}' operation {} ({})", groupId, operationId, operation);

    final var applier = groupAppliers.getApplier(operation);
    final var initialized =
        applier
            .init(config.globalConfiguration(), group)
            .map(transformer -> config.updatePartitionGroupConfig(groupId, transformer))
            .flatMap(updateLocally);
    if (initialized.isLeft()) {
      observer.failed();
      inFlight.remove(operationId);
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
                onApplied(operationId, operation, transformer, error, startedVersion, observer));
  }

  private void onApplied(
      final OperationId operationId,
      final PartitionGroupOperation operation,
      final UnaryOperator<PartitionGroupConfiguration> transformer,
      final @Nullable Throwable error,
      final long startedVersion,
      final OperationObserver observer) {
    inFlight.remove(operationId);

    if (error != null) {
      observer.failed();
      final Duration delay =
          backoffs
              .computeIfAbsent(
                  operationId,
                  ignored -> new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay))
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
    backoffs.remove(operationId);

    final var config = currentConfiguration.get();
    if (versionOf(config) != startedVersion) {
      // Recording an operation moves no version, so a version change here means the change was
      // cancelled or already completed while this operation was running.
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
                group.completeOperation(operationId, transformer).completeGraphChangeIfDrained());
    // Persisting re-triggers reconciliation across every scope and plan, which is what picks up the
    // operations this completion just made runnable — no explicit "run the loop again" is needed.
    final var persisted = updateLocally.apply(advanced);
    if (persisted.isLeft()) {
      // The operation itself succeeded; only recording it locally failed. Retrying re-derives it
      // from the graph, which is safe because operations are idempotent.
      final Duration delay =
          backoffs
              .computeIfAbsent(
                  operationId,
                  ignored -> new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay))
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
}
