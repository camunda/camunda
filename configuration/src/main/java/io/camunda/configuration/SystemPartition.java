/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.unit.DataSize;

/**
 * Experimental: the system partition is a metadata-only Raft group replicated on every broker,
 * including brokers of control zones (zones with zero replicas). It carries cluster coordination
 * records such as broker heartbeats.
 */
public class SystemPartition {

  /** Whether the system partition is bootstrapped on this broker. */
  private boolean enabled = false;

  /**
   * How many brokers replicate the system partition, independently of the data partitions'
   * replication factor. With zone-aware partitioning, the replicas are spread round-robin across
   * the zones in their configured order, control zones included, e.g. 5 replicas over two data
   * zones of 3 brokers and a control zone of 1 broker are placed 2, 2 and 1. Defaults to every
   * broker of the cluster.
   */
  private @Nullable Integer replicationFactor;

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

  /** Experimental, opt-in detection of brokers gone from the cluster. */
  @NestedConfigurationProperty private FailureDetector failureDetector = new FailureDetector();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public @Nullable Integer getReplicationFactor() {
    return replicationFactor;
  }

  public void setReplicationFactor(final @Nullable Integer replicationFactor) {
    this.replicationFactor = replicationFactor;
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

  public FailureDetector getFailureDetector() {
    return failureDetector;
  }

  public void setFailureDetector(final FailureDetector failureDetector) {
    this.failureDetector = failureDetector;
  }

  @Override
  public String toString() {
    return "SystemPartition{"
        + "enabled="
        + enabled
        + ", replicationFactor="
        + replicationFactor
        + ", logSegmentSize="
        + logSegmentSize
        + ", snapshotPeriod="
        + snapshotPeriod
        + ", failureDetector="
        + failureDetector
        + '}';
  }
}
