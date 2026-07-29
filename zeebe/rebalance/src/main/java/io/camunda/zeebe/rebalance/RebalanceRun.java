/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import org.jspecify.annotations.NullMarked;

/**
 * A rebalance the coordinator is running.
 *
 * <p>Access is confined to the coordinator's actor thread.
 */
@NullMarked
public final class RebalanceRun {

  private final long id;
  private final RebalanceOverrides overrides;
  private final boolean dryRun;

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

  /** Per-rebalance overrides for the configured default rebalance settings. */
  public RebalanceOverrides overrides() {
    return overrides;
  }

  /** Report the plan without pausing any partition or transferring any leadership. */
  public boolean dryRun() {
    return dryRun;
  }

  /**
   * Asks the rebalance to stop. A cancellation never interrupts a partition transfer which is
   * currently in flight.
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
