/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

/**
 * Configures RocksDB's compact-on-deletion collector, which marks an SST file for compaction as
 * soon as it is written if it contains a high density of deletions. This removes tombstones of
 * queue-like state (e.g. activatable jobs, job deadlines, timers) early, which keeps seeks over
 * those ranges cheap, at the cost of additional compaction I/O. Disabled by default.
 */
public class RocksDbCompactOnDeletion {

  /** Enables the compact-on-deletion collector. */
  private boolean enabled = false;

  /** Size of the sliding window of consecutive entries in which deletions are counted. */
  private long windowSize = 1000;

  /**
   * A file is marked for compaction if any sliding window of windowSize entries contains at least
   * this many deletions. Must be greater than 0 and at most windowSize.
   */
  private long deletionTrigger = 500;

  /**
   * A file is also marked for compaction if the share of deletions among all its entries is at
   * least this ratio. Must be in [0, 1]; 0 disables this check.
   */
  private double deletionRatio = 0;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public long getWindowSize() {
    return windowSize;
  }

  public void setWindowSize(final long windowSize) {
    this.windowSize = windowSize;
  }

  public long getDeletionTrigger() {
    return deletionTrigger;
  }

  public void setDeletionTrigger(final long deletionTrigger) {
    this.deletionTrigger = deletionTrigger;
  }

  public double getDeletionRatio() {
    return deletionRatio;
  }

  public void setDeletionRatio(final double deletionRatio) {
    this.deletionRatio = deletionRatio;
  }

  @Override
  public String toString() {
    return "RocksDbCompactOnDeletion{"
        + "enabled="
        + enabled
        + ", windowSize="
        + windowSize
        + ", deletionTrigger="
        + deletionTrigger
        + ", deletionRatio="
        + deletionRatio
        + '}';
  }
}
