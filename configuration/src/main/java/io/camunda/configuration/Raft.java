/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.configuration;

import java.time.Duration;
import java.util.Set;

/**
 * Configuration for the Raft consensus protocol in the cluster. This class provides settings for
 * Raft algorithm parameters including timing, elections, and log flushing.
 */
public class Raft {
  private static final String PREFIX = "camunda.cluster.raft";

  private static final String LEGACY_HEARTBEAT_INTERVAL = "zeebe.broker.cluster.heartbeatInterval";
  private static final String LEGACY_ELECTION_TIMEOUT = "zeebe.broker.cluster.electionTimeout";
  private static final String LEGACY_PRIORITY_ELECTION_ENABLED =
      "zeebe.broker.cluster.raft.enablePriorityElection";
  private static final String LEGACY_FLUSH_ENABLED = "zeebe.broker.cluster.raft.flush.enabled";
  private static final String LEGACY_FLUSH_DELAY = "zeebe.broker.cluster.raft.flush.delay";

  /**
   * The heartbeat interval for Raft. The leader sends a heartbeat to a follower every
   * heartbeatInterval. This is an advanced setting.
   */
  private Duration heartbeatInterval = Duration.ofMillis(250);

  /**
   * The election timeout for Raft. If a follower does not receive a heartbeat from the leader
   * within an election timeout, it can start a new leader election. electionTimeout should be
   * greater than configured heartbeatInterval. When the electionTimeout is large, there will be
   * delay in detecting a leader failure. When the electionTimeout is small, it can lead to false
   * positives when detecting leader failures and thus leading to unnecessary leader changes. If the
   * network latency between the nodes is high, it is recommended to have a higher election timeout.
   * This is an advanced setting.
   */
  private Duration electionTimeout = Duration.ofMillis(2500);

  /**
   * When this flag is enabled, the leader election algorithm attempts to elect the leaders based on
   * a pre-defined priority. As a result, it tries to distribute the leaders uniformly across the
   * brokers. Note that it is only a best-effort strategy. It is not guaranteed to be a strictly
   * uniform distribution.
   */
  private boolean priorityElectionEnabled = true;

  /**
   * If false, explicit flushing of the Raft log is disabled, and flushing only occurs right before
   * a snapshot is taken. You should only disable explicit flushing if you are willing to accept
   * potential data loss at the expense of performance. Before disabling it, try the delayed
   * options, which provide a trade-off between safety and performance.
   *
   * <p>By default, for a given partition, data is flushed on every leader commit, and every
   * follower append. This is to ensure consistency across all replicas. Disabling this can cause
   * inconsistencies, and at worst, data corruption or data loss scenarios.
   */
  private boolean flushEnabled = true;

  /**
   * If the delay is > 0, then flush requests are delayed by at least the given period. It is
   * recommended that you find the smallest delay here with which you achieve your performance
   * goals. It's also likely that anything above 30s is not useful, as this is the typical default
   * flush interval for the Linux OS.
   *
   * <p>The default behavior is optimized for safety, and flushing occurs on every leader commit and
   * follower append in a synchronous fashion.
   */
  private Duration flushDelay = Duration.ZERO;

  public Duration getHeartbeatInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".heartbeat-interval",
        heartbeatInterval,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_HEARTBEAT_INTERVAL));
  }

  public void setHeartbeatInterval(final Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  public Duration getElectionTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".election-timeout",
        electionTimeout,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_ELECTION_TIMEOUT));
  }

  public void setElectionTimeout(final Duration electionTimeout) {
    this.electionTimeout = electionTimeout;
  }

  public boolean isPriorityElectionEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".priority-election-enabled",
        priorityElectionEnabled,
        Boolean.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_PRIORITY_ELECTION_ENABLED));
  }

  public void setPriorityElectionEnabled(final boolean priorityElectionEnabled) {
    this.priorityElectionEnabled = priorityElectionEnabled;
  }

  public boolean isFlushEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".flush-enabled",
        flushEnabled,
        Boolean.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_FLUSH_ENABLED));
  }

  public void setFlushEnabled(final boolean flushEnabled) {
    this.flushEnabled = flushEnabled;
  }

  public Duration getFlushDelay() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".flush-delay",
        flushDelay,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_FLUSH_DELAY));
  }

  public void setFlushDelay(final Duration flushDelay) {
    this.flushDelay = flushDelay;
  }
}
