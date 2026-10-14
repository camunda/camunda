/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import org.springframework.util.unit.DataSize;

/**
 * Experimental: the system partition is a metadata-only Raft group replicated on every broker,
 * including brokers of control zones (zones with zero replicas). It carries cluster coordination
 * records such as broker heartbeats.
 */
public class SystemPartition {

  /** Whether the system partition is bootstrapped on this broker. */
  private boolean enabled = false;

  /** How often this broker appends a heartbeat to the system partition. */
  private Duration heartbeatInterval = Duration.ofSeconds(1);

  /**
   * The size of the system partition's log segments. The log is compacted by deleting whole
   * segments, so this is kept far below the data partitions' segment size: the system partition
   * holds little data, and a large segment would keep it from ever being compacted. Must not be
   * smaller than the maximum message size.
   */
  private DataSize logSegmentSize = DataSize.ofMegabytes(4);

  /**
   * How often the system partition's state is snapshotted, if it changed since the latest snapshot.
   * Snapshots let the log be compacted, and are the only place the state is written to disk as a
   * file.
   */
  private Duration snapshotPeriod = Duration.ofMinutes(5);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public void setHeartbeatInterval(final Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  public DataSize getLogSegmentSize() {
    return logSegmentSize;
  }

  public void setLogSegmentSize(final DataSize logSegmentSize) {
    this.logSegmentSize = logSegmentSize;
  }

  public Duration getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public void setSnapshotPeriod(final Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
  }

  @Override
  public String toString() {
    return "SystemPartition{"
        + "enabled="
        + enabled
        + ", heartbeatInterval="
        + heartbeatInterval
        + ", logSegmentSize="
        + logSegmentSize
        + ", snapshotPeriod="
        + snapshotPeriod
        + '}';
  }
}
