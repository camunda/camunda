/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;
import org.springframework.util.unit.DataSize;

/**
 * Configuration of the system partition: a metadata-only Raft group replicated on every broker of
 * the cluster, including brokers of control zones which host no data partitions. It carries cluster
 * coordination data, and optionally detects brokers gone from the cluster.
 */
public final class SystemPartitionCfg implements ConfigurationEntry {
  public static final DataSize DEFAULT_LOG_SEGMENT_SIZE = DataSize.ofMegabytes(4);
  public static final Duration DEFAULT_SNAPSHOT_PERIOD = Duration.ofMinutes(5);

  private boolean enabled = false;
  private DataSize logSegmentSize = DEFAULT_LOG_SEGMENT_SIZE;
  private Duration snapshotPeriod = DEFAULT_SNAPSHOT_PERIOD;
  private FailureDetectorCfg failureDetector = new FailureDetectorCfg();

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    failureDetector.init(globalConfig, brokerBase);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
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

  public FailureDetectorCfg getFailureDetector() {
    return failureDetector;
  }

  public void setFailureDetector(final FailureDetectorCfg failureDetector) {
    this.failureDetector = failureDetector;
  }

  @Override
  public String toString() {
    return "SystemPartitionCfg{"
        + "enabled="
        + enabled
        + ", logSegmentSize="
        + logSegmentSize
        + ", snapshotPeriod="
        + snapshotPeriod
        + ", failureDetector="
        + failureDetector
        + '}';
  }
}
