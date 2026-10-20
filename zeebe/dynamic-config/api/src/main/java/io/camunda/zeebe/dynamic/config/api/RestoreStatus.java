/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.state.ChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A pending change plan is considered a restore if its pending operations contain a {@link
 * PartitionRestoreOperation} or {@link PartitionPreRestoreOperation} (partitions are still being
 * restored), or a trailing epilogue operation - a mode transition to {@link Mode#PROCESSING} or the
 * final {@link UpdateIncarnationNumberOperation} - whose completed operations already include a
 * {@link PartitionRestoreOperation} or {@link PartitionPreRestoreOperation}. There is at most one
 * restore in flight at any time.
 */
@NullMarked
public record RestoreStatus(ChangePlan plan, List<BrokerRestoreStatus> brokers) {

  /** Derives {@code brokers} from the plan once, rather than recomputing it on every access. */
  public RestoreStatus(final ChangePlan plan) {
    this(plan, mapBrokers(plan));
  }

  public static Optional<RestoreStatus> of(final PartitionGroupConfiguration configuration) {
    return configuration
        .pendingChanges()
        .filter(RestoreStatus::isRestoreInProgress)
        .map(RestoreStatus::new);
  }

  public Status status() {
    return plan.status();
  }

  public long changeId() {
    return plan.id();
  }

  public @Nullable Instant startedAt() {
    return plan.startedAt();
  }

  private static boolean isRestoreInProgress(final ChangePlan plan) {
    final var pending = plan.pendingOperations();
    if (pending.stream().anyMatch(RestoreStatus::isRestoreOperation)) {
      return true;
    }
    return pending.stream().anyMatch(RestoreStatus::isPostRestoreOperation)
        && plan.completedOperations().stream()
            .map(CompletedOperation::operation)
            .anyMatch(RestoreStatus::isRestoreOperation);
  }

  private static boolean isRestoreOperation(final ClusterConfigurationChangeOperation operation) {
    return operation instanceof PartitionRestoreOperation
        || operation instanceof PartitionPreRestoreOperation;
  }

  private static boolean isPostRestoreOperation(
      final ClusterConfigurationChangeOperation operation) {
    return switch (operation) {
      case final ModeChangeOperation modeChange -> modeChange.mode() == Mode.PROCESSING;
      case final AwaitModeChangeOperation awaitModeChange ->
          awaitModeChange.mode() == Mode.PROCESSING;
      case final UpdateIncarnationNumberOperation ignored -> true;
      default -> false;
    };
  }

  private static List<BrokerRestoreStatus> mapBrokers(final ChangePlan plan) {
    // Sorted by broker, then by partition, so the report has a stable order.
    final SortedMap<String, SortedMap<Integer, PartitionAccumulator>> brokerPartitionMap =
        new TreeMap<>();

    plan.completedOperations()
        .forEach(
            completed ->
                accumulate(brokerPartitionMap, completed.operation(), completed.completedAt()));
    plan.pendingOperations().forEach(pending -> accumulate(brokerPartitionMap, pending));

    final var brokers = new ArrayList<BrokerRestoreStatus>();
    brokerPartitionMap.forEach(
        (brokerId, partitions) -> brokers.add(toBrokerStatus(brokerId, partitions)));
    return List.copyOf(brokers);
  }

  private static BrokerRestoreStatus toBrokerStatus(
      final String brokerId, final SortedMap<Integer, PartitionAccumulator> partitions) {
    final var statuses = new ArrayList<PartitionRestoreStatus>();
    partitions.forEach(
        (partitionId, accumulator) -> statuses.add(accumulator.toStatus(partitionId)));
    final var restored =
        statuses.stream().filter(p -> p.state() == PartitionRestoreState.RESTORED).count();

    return new BrokerRestoreStatus(
        brokerId, (int) restored, statuses.size(), List.copyOf(statuses));
  }

  private static void accumulate(
      final Map<String, SortedMap<Integer, PartitionAccumulator>> brokerPartitionMap,
      final ClusterConfigurationChangeOperation operation,
      final @Nullable Instant completedAt) {
    switch (operation) {
      case final PartitionPreRestoreOperation preRestore -> {
        if (completedAt != null) {
          final var accumulator =
              accumulatorOf(
                  brokerPartitionMap, preRestore.memberId().id(), preRestore.partitionId());
          accumulator.preRestored = true;
        }
      }
      case final PartitionRestoreOperation restore -> {
        final var accumulator =
            accumulatorOf(brokerPartitionMap, restore.memberId().id(), restore.partitionId());
        accumulator.backupIds = restore.backupIds();
        if (completedAt != null) {
          accumulator.restoredAt = completedAt;
        }
      }
      default -> {
        // Non-partition restore operations (mode change, incarnation number) are not reported.
      }
    }
  }

  private static void accumulate(
      final Map<String, SortedMap<Integer, PartitionAccumulator>> brokerPartitionMap,
      final ClusterConfigurationChangeOperation operation) {
    accumulate(brokerPartitionMap, operation, null);
  }

  private static PartitionAccumulator accumulatorOf(
      final Map<String, SortedMap<Integer, PartitionAccumulator>> brokerPartitionMap,
      final String brokerId,
      final int partitionId) {
    return brokerPartitionMap
        .computeIfAbsent(brokerId, ignored -> new TreeMap<>())
        .computeIfAbsent(partitionId, ignored -> new PartitionAccumulator());
  }

  /** The restore status of a single broker. */
  public record BrokerRestoreStatus(
      String brokerId,
      int partitionsRestored,
      int partitionsToRestore,
      List<PartitionRestoreStatus> partitions) {}

  /** The restore status of a single partition on a broker. */
  public record PartitionRestoreStatus(
      int partitionId,
      PartitionRestoreState state,
      List<Long> backupIds,
      @Nullable Instant completedAt) {}

  /** Mutable accumulator for a single partition's restore state while mapping a change plan. */
  private static final class PartitionAccumulator {
    private @Nullable SortedSet<Long> backupIds;
    private boolean preRestored;
    private @Nullable Instant restoredAt;

    private PartitionRestoreStatus toStatus(final int partitionId) {
      final PartitionRestoreState state;
      if (restoredAt != null) {
        state = PartitionRestoreState.RESTORED;
      } else if (preRestored) {
        state = PartitionRestoreState.RESTORING;
      } else {
        state = PartitionRestoreState.PENDING;
      }
      return new PartitionRestoreStatus(
          partitionId, state, backupIds == null ? List.of() : List.copyOf(backupIds), restoredAt);
    }
  }

  /** The restore state of a single partition on a broker. */
  public enum PartitionRestoreState {
    /** The partition has not started restoring yet. */
    PENDING,
    /** The partition's local data has been dropped and it is being restored. */
    RESTORING,
    /** The partition has been restored. */
    RESTORED
  }
}
