/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullMarked;

/**
 * A rebalance the coordinator is running. It holds no more than the coordinator needs to answer for
 * the rebalance while it is in flight, and lives only as long as the rebalance does: nothing here
 * is persisted, so a coordinator that restarts or moves has no rebalance to resume.
 *
 * <p>Confined to the coordinator's actor thread, which is also the thread the {@link
 * RebalanceRunner} driving it plans its partitions and reads {@link #isCancelRequested()} on.
 */
@NullMarked
public final class RebalanceRun {

  private final long id;
  private final RebalanceOverrides overrides;
  private final boolean dryRun;
  private final ClusterConfiguration configuration;
  private final List<PartitionRebalance> partitions = new ArrayList<>();
  private final long startedAtNanos = System.nanoTime();

  private long partitionStartedAtNanos = System.nanoTime();
  private boolean cancelRequested;

  public RebalanceRun(
      final long id,
      final RebalanceOverrides overrides,
      final boolean dryRun,
      final ClusterConfiguration configuration) {
    this.id = id;
    this.overrides = overrides;
    this.dryRun = dryRun;
    this.configuration = configuration;
  }

  public long id() {
    return id;
  }

  /** The settings this rebalance runs under, in place of each leader's configured ones. */
  public RebalanceOverrides overrides() {
    return overrides;
  }

  /** Report the plan without pausing any partition or transferring any leadership. */
  public boolean dryRun() {
    return dryRun;
  }

  /**
   * The committed cluster configuration this rebalance was admitted under, pinned so that the
   * desired leaders it works towards are decided once. A configuration change that lands while the
   * rebalance runs is not picked up: whatever it changes is for the next rebalance to act on.
   */
  public ClusterConfiguration configuration() {
    return configuration;
  }

  /** How long this rebalance has been running. */
  public Duration elapsed() {
    return Duration.ofNanos(System.nanoTime() - startedAtNanos);
  }

  /**
   * Starts the clock on the partition the rebalance is taking on. Only one partition is ever in
   * flight, so one clock is enough for all of them.
   */
  public void startPartition() {
    partitionStartedAtNanos = System.nanoTime();
  }

  /** How long the rebalance has been working on the partition it took on last. */
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
}
