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

public final class RaftCfg implements ConfigurationEntry {
  public static final boolean DEFAULT_ENABLE_PRIORITY_ELECTION = true;
  public static final DataSize DEFAULT_REBALANCE_REPLICATION_LAG_THRESHOLD =
      DataSize.ofMegabytes(8);
  public static final Duration DEFAULT_REBALANCE_REPLICATION_TIMEOUT = Duration.ofSeconds(10);
  public static final int DEFAULT_REBALANCE_MAX_TRANSFER_ATTEMPTS = 3;
  public static final Duration DEFAULT_REBALANCE_LEADER_WAIT_TIMEOUT = Duration.ofMinutes(1);
  private static final FlushConfig DEFAULT_FLUSH_CONFIG = new FlushConfig(true, Duration.ZERO);

  private boolean enablePriorityElection = DEFAULT_ENABLE_PRIORITY_ELECTION;

  private FlushConfig flush = DEFAULT_FLUSH_CONFIG;
  private DataSize rebalanceReplicationLagThreshold = DEFAULT_REBALANCE_REPLICATION_LAG_THRESHOLD;
  private Duration rebalanceReplicationTimeout = DEFAULT_REBALANCE_REPLICATION_TIMEOUT;
  private int rebalanceMaxTransferAttempts = DEFAULT_REBALANCE_MAX_TRANSFER_ATTEMPTS;
  private Duration rebalanceLeaderWaitTimeout = DEFAULT_REBALANCE_LEADER_WAIT_TIMEOUT;

  public boolean isEnablePriorityElection() {
    return enablePriorityElection;
  }

  public void setEnablePriorityElection(final boolean enablePriorityElection) {
    this.enablePriorityElection = enablePriorityElection;
  }

  public FlushConfig getFlush() {
    return flush;
  }

  public void setFlush(final FlushConfig flush) {
    this.flush = flush;
  }

  public DataSize getRebalanceReplicationLagThreshold() {
    return rebalanceReplicationLagThreshold;
  }

  public void setRebalanceReplicationLagThreshold(final DataSize rebalanceReplicationLagThreshold) {
    this.rebalanceReplicationLagThreshold = rebalanceReplicationLagThreshold;
  }

  public Duration getRebalanceReplicationTimeout() {
    return rebalanceReplicationTimeout;
  }

  public void setRebalanceReplicationTimeout(final Duration rebalanceReplicationTimeout) {
    this.rebalanceReplicationTimeout = rebalanceReplicationTimeout;
  }

  public int getRebalanceMaxTransferAttempts() {
    return rebalanceMaxTransferAttempts;
  }

  public void setRebalanceMaxTransferAttempts(final int rebalanceMaxTransferAttempts) {
    this.rebalanceMaxTransferAttempts = rebalanceMaxTransferAttempts;
  }

  public Duration getRebalanceLeaderWaitTimeout() {
    return rebalanceLeaderWaitTimeout;
  }

  public void setRebalanceLeaderWaitTimeout(final Duration rebalanceLeaderWaitTimeout) {
    this.rebalanceLeaderWaitTimeout = rebalanceLeaderWaitTimeout;
  }

  @Override
  public String toString() {
    return "RaftCfg{"
        + "enablePriorityElection="
        + enablePriorityElection
        + ", flushConfig="
        + flush
        + ", rebalanceReplicationLagThreshold="
        + rebalanceReplicationLagThreshold
        + ", rebalanceReplicationTimeout="
        + rebalanceReplicationTimeout
        + ", rebalanceMaxTransferAttempts="
        + rebalanceMaxTransferAttempts
        + ", rebalanceLeaderWaitTimeout="
        + rebalanceLeaderWaitTimeout
        + '}';
  }

  public record FlushConfig(boolean enabled, Duration delayTime) {
    public FlushConfig(final boolean enabled, final Duration delayTime) {
      this.enabled = enabled;
      this.delayTime = delayTime == null ? Duration.ZERO : delayTime;
    }
  }
}
