/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * A rebalance the coordinator is running.
 *
 * <p>Access is confined to the coordinator's actor thread.
 */
public final class RebalanceRun {

  private final long id;
  private final RebalanceOverrides overrides;
  private final boolean dryRun;
  private final CurrentClusterConfiguration configuration;
  private final Instant startedAt;
  private final List<PartitionRebalance> partitions = new ArrayList<>();

  private final Set<String> disabledPhysicalTenants = new HashSet<>();

  private long partitionStartedAtNanos = System.nanoTime();
  private boolean cancelRequested;
  private boolean abandoned;
  private @Nullable Instant finishedAt;

  public RebalanceRun(
      final long id,
      final RebalanceOverrides overrides,
      final boolean dryRun,
      final CurrentClusterConfiguration configuration,
      final Instant startedAt) {
    this.id = id;
    this.overrides = overrides;
    this.dryRun = dryRun;
    this.configuration = configuration;
    this.startedAt = startedAt;
  }

  public long id() {
    return id;
  }

  /** Per-rebalance overrides for the configured default rebalance settings. */
  public RebalanceOverrides overrides() {
    return overrides;
  }

  /** Report the plan without pausing any partition or transferring any leadership. */
  public boolean dryRun() {
    return dryRun;
  }

  /** The committed cluster configuration this rebalance was admitted under. */
  public CurrentClusterConfiguration configuration() {
    return configuration;
  }

  /** When this rebalance was created. */
  public Instant startedAt() {
    return startedAt;
  }

  /** When this rebalance finished, or {@code null} while it is still running. */
  public @Nullable Instant finishedAt() {
    return finishedAt;
  }

  /** Records that this rebalance finished at the given instant. */
  public void finish(final Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  /**
   * Starts tracking elapsed time for rebalancing a single partition. Only one partition is ever in
   * flight.
   */
  public void startPartition() {
    partitionStartedAtNanos = System.nanoTime();
  }

  /** How long the rebalance has been working on the latest partition it got to. */
  public Duration partitionElapsed() {
    return Duration.ofNanos(System.nanoTime() - partitionStartedAtNanos);
  }

  /**
   * The partitions this rebalance covers, in the order it works through them, each with where the
   * rebalance has got to with it. Empty until the runner has planned the rebalance.
   */
  public List<PartitionRebalance> partitions() {
    return List.copyOf(partitions);
  }

  /** Fixes the partitions this rebalance covers, and the leaders it moves leadership between. */
  public void plan(final List<PartitionRebalance> planned) {
    partitions.clear();
    partitions.addAll(planned);
  }

  /**
   * Notes which of the planned physical tenants {@code current} no longer runs, so that the runner
   * can complete their partitions rather than wait out {@code leaderWaitTimeout} for a leader that
   * will never appear.
   */
  public void observeConfiguration(final CurrentClusterConfiguration current) {
    final var active = current.activePartitionGroups().keySet();
    disabledPhysicalTenants.clear();
    partitions.stream()
        .map(PartitionRebalance::physicalTenantId)
        .filter(physicalTenantId -> !active.contains(physicalTenantId))
        .forEach(disabledPhysicalTenants::add);
  }

  /** Whether this physical tenant has stopped running since the rebalance was planned. */
  public boolean isPhysicalTenantDisabled(final String physicalTenantId) {
    return disabledPhysicalTenants.contains(physicalTenantId);
  }

  public PartitionRebalance partition(final int index) {
    return partitions.get(index);
  }

  public int partitionCount() {
    return partitions.size();
  }

  /** Records what became of the partition at {@code index} as the rebalance works through it. */
  public void updatePartition(final int index, final UnaryOperator<PartitionRebalance> updater) {
    partitions.set(index, updater.apply(partitions.get(index)));
  }

  /**
   * Asks the rebalance to stop. A cancellation never interrupts the transfer in flight, so the
   * runner is expected to observe this between partitions rather than part-way through one.
   */
  public void requestCancel() {
    cancelRequested = true;
  }

  public boolean isCancelRequested() {
    return cancelRequested;
  }

  /** Gives up on the rebalance entirely, for a coordinator that has lost the role. */
  public void abandon() {
    abandoned = true;
  }

  public boolean isAbandoned() {
    return abandoned;
  }
}
