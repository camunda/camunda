/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

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
  private final List<PartitionRebalance> partitions = new ArrayList<>();

  private boolean cancelRequested;
  private boolean abandoned;

  public RebalanceRun(final long id, final RebalanceOverrides overrides, final boolean dryRun) {
    this.id = id;
    this.overrides = overrides;
    this.dryRun = dryRun;
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

  /**
   * Gives up on the rebalance entirely, for a coordinator that has lost the role. Unlike a
   * cancellation this is not an outcome anybody is waiting to hear: the state it would be reported
   * from is already gone, and the runner's only remaining job is to stop working on it.
   */
  public void abandon() {
    abandoned = true;
  }

  public boolean isAbandoned() {
    return abandoned;
  }
}
