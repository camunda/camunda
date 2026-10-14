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
 * coordination records such as broker heartbeats.
 */
public final class SystemPartitionCfg implements ConfigurationEntry {
  public static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(1);
  public static final DataSize DEFAULT_LOG_SEGMENT_SIZE = DataSize.ofMegabytes(4);

  private boolean enabled = false;
  private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
  private DataSize logSegmentSize = DEFAULT_LOG_SEGMENT_SIZE;

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

  @Override
  public String toString() {
    return "SystemPartitionCfg{"
        + "enabled="
        + enabled
        + ", heartbeatInterval="
        + heartbeatInterval
        + ", logSegmentSize="
        + logSegmentSize
        + '}';
  }
}
