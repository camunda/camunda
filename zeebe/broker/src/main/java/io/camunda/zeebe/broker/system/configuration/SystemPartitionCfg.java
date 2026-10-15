/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneCfg;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.util.unit.DataSize;

/**
 * Configuration of the system partition: a metadata-only Raft group replicated on {@link
 * #getReplicationFactor() replication factor} brokers of the cluster, spread across zones and
 * including brokers of control zones which host no data partitions. It carries cluster coordination
 * data, and optionally detects brokers gone from the cluster.
 */
public final class SystemPartitionCfg implements ConfigurationEntry {
  public static final DataSize DEFAULT_LOG_SEGMENT_SIZE = DataSize.ofMegabytes(4);
  public static final Duration DEFAULT_SNAPSHOT_PERIOD = Duration.ofMinutes(5);

  private boolean enabled = true;
  // null: every broker of the cluster
  private @Nullable Integer replicationFactor;
  private DataSize logSegmentSize = DEFAULT_LOG_SEGMENT_SIZE;
  private Duration snapshotPeriod = DEFAULT_SNAPSHOT_PERIOD;
  private FailureDetectorCfg failureDetector = new FailureDetectorCfg();

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    if (enabled && replicationFactor != null) {
      final var brokers = brokerCount(globalConfig);
      if (replicationFactor < 1 || replicationFactor > brokers) {
        throw new IllegalArgumentException(
            ("Expected the system partition's replication factor to be in [1, %d], the number of"
                    + " brokers, but was %d")
                .formatted(brokers, replicationFactor));
      }
    }
    failureDetector.init(globalConfig, brokerBase);
  }

  /** Returns the number of brokers the system partition can be replicated on. */
  public static int brokerCount(final BrokerCfg brokerCfg) {
    final var partitioning = brokerCfg.getExperimental().getPartitioning();
    if (partitioning.getScheme() == Scheme.ZONE_AWARE) {
      return partitioning.getZoneAware().zones().stream().mapToInt(ZoneCfg::numberOfBrokers).sum();
    }
    return brokerCfg.getCluster().getClusterSize();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns how many brokers replicate the system partition, independent of the data partitions'
   * replication factor, or null to replicate it on every broker.
   */
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
