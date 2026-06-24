/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

/** Represents the metrics for a single job status. */
public final class StatusMetrics {

  /** Size in bytes: int (4 bytes) + long (8 bytes) = 12 bytes */
  public static final int TOTAL_SIZE_BYTES = Integer.BYTES + Long.BYTES;

  private int count;
  private long lastUpdatedAt;

  public StatusMetrics() {
    reset();
  }

  public StatusMetrics(final int count, final long lastUpdatedAt) {
    this.count = count;
    this.lastUpdatedAt = lastUpdatedAt;
  }

  public int getCount() {
    return count;
  }

  public void setCount(final int count) {
    this.count = count;
  }

  public long getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public void setLastUpdatedAt(final long lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
  }

  public void increment(final long timestamp) {
    count++;
    lastUpdatedAt = timestamp;
  }

  public void reset() {
    count = 0;
    lastUpdatedAt = -1L;
  }

  @Override
  public String toString() {
    return "StatusMetrics{" + "count=" + count + ", lastUpdatedAt=" + lastUpdatedAt + '}';
  }
}
