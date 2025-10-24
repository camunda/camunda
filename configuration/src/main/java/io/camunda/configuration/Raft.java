/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.configuration;

import static io.camunda.zeebe.broker.system.configuration.ClusterCfg.DEFAULT_ELECTION_TIMEOUT;
import static io.camunda.zeebe.broker.system.configuration.ExperimentalCfg.DEFAULT_MAX_APPENDS_PER_FOLLOWER;
import static io.camunda.zeebe.broker.system.configuration.ExperimentalCfg.DEFAULT_MAX_APPEND_BATCH_SIZE;
import static io.camunda.zeebe.broker.system.configuration.ExperimentalRaftCfg.DEFAULT_SNAPSHOT_CHUNK_SIZE;
import static io.camunda.zeebe.broker.system.configuration.ExperimentalRaftCfg.DEFAULT_SNAPSHOT_REQUEST_TIMEOUT;

import java.time.Duration;
import java.util.Set;
import org.springframework.util.unit.DataSize;

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
  private static final String LEGACY_MAX_APPENDS_PER_FOLLOWER =
      "zeebe.broker.experimental.maxAppendsPerFollower";
  private static final String LEGACY_MAX_APPEND_BATCH_SIZE =
      "zeebe.broker.experimental.maxAppendBatchSize";
  private static final String LEGACY_REQUEST_TIMEOUT =
      "zeebe.broker.experimental.raft.requestTimeout";
  private static final String LEGACY_SNAPSHOT_REQUEST_TIMEOUT =
      "zeebe.broker.experimental.raft.snapshotRequestTimeout";
  private static final String LEGACY_SNAPSHOT_CHUNK_SIZE =
      "zeebe.broker.experimental.raft.snapshotChunkSize";
  private static final String LEGACY_CONFIGURATION_CHANGE_TIMEOUT =
      "zeebe.broker.experimental.raft.configurationChangeTimeout";
  private static final String LEGACY_MAX_QUORUM_RESPONSE_TIMEOUT =
      "zeebe.broker.experimental.raft.maxQuorumResponseTimeout";
  private static final String LEGACY_MIN_STEP_DOWN_FAILURE_COUNT =
      "zeebe.broker.experimental.raft.minStepDownFailureCount";
  private static final String LEGACY_PREFER_SNAPSHOT_REPLICATION_THRESHOLD =
      "zeebe.broker.experimental.raft.preferSnapshotReplicationThreshold";
  private static final String LEGACY_PREALLOCATE_SEGMENT_FILES =
      "zeebe.broker.experimental.raft.preallocateSegmentFiles";

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

  /** Sets the maximum of appends which are send per follower. */
  private int maxAppendsPerFollower = DEFAULT_MAX_APPENDS_PER_FOLLOWER;

  /** Sets the maximum batch size, which is send per append request to a follower. */
  private DataSize maxAppendBatchSize = DEFAULT_MAX_APPEND_BATCH_SIZE;

  /**
   * Sets the timeout for all requests send by raft leaders and followers.When modifying the values
   * for requestTimeout, it might also be useful to update snapshotTimeout.
   */
  private Duration requestTimeout = DEFAULT_ELECTION_TIMEOUT;

  /**
   * Sets the timeout for all snapshot requests sent by raft leaders to the followers. If the
   * network latency between brokers is high, it would help to set a higher timeout here.
   */
  private Duration snapshotRequestTimeout = DEFAULT_SNAPSHOT_REQUEST_TIMEOUT;

  /** Sets the maximum size of snapshot chunks sent by raft leaders to the followers. */
  private DataSize snapshotChunkSize = DEFAULT_SNAPSHOT_CHUNK_SIZE;

  /**
   * Sets the timeout for configuration change requests such as joining or leaving. Since changes
   * are usually a multi-step process with multiple commits, a higher timeout than the default
   * requestTimeout is recommended.
   */
  private Duration configurationChangeTimeout = Duration.ofSeconds(10);

  /**
   * If the leader is not able to reach the quorum, the leader may step down. This is triggered if
   * the leader is not able to reach the quorum of the followers for maxQuorumResponseTimeout. The
   * minStepDownFailureCount also influences when the leader step down. Higher the timeout, slower
   * the leader reacts to a partial network partition. When the timeout is lower, there might be
   * false positives, and the leader might step down too quickly. When this value is 0, it will use
   * a default value of electionTimeout * 2.
   */
  private Duration maxQuorumResponseTimeout = Duration.ofSeconds(0);

  /**
   * If the leader is not able to reach the quorum, the leader may step down. This is triggered
   * after a number of requests, to a quorum of followers, has failed, and the number of failures
   * reached minStepDownFailureCount. The maxQuorumResponseTime also influences when the leader step
   * down.
   */
  private int minStepDownFailureCount = 3;

  /**
   * Threshold used by the leader to decide between replicating a snapshot or records. The unit is
   * number of records by which the follower may lag behind before the leader prefers replicating
   * snapshots instead of records.
   */
  private int preferSnapshotReplicationThreshold = 100;

  /**
   * Defines whether segment files are pre-allocated to their full size on creation or not. If true,
   * when a new segment is created on demand, disk space will be reserved for its full maximum size.
   * This helps avoid potential out of disk space errors which can be fatal when using memory mapped
   * files, especially when running on network storage. In the best cases, it will also allocate
   * contiguous blocks, giving a small performance boost.
   *
   * <p>You may want to turn this off if your system does not support efficient file allocation via
   * system calls, or if you notice an I/O penalty when creating segments.
   */
  private boolean preallocateSegmentFiles = true;

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

  public int getMaxAppendsPerFollower() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-appends-per-follower",
        maxAppendsPerFollower,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_MAX_APPENDS_PER_FOLLOWER));
  }

  public void setMaxAppendsPerFollower(final int maxAppendsPerFollower) {
    this.maxAppendsPerFollower = maxAppendsPerFollower;
  }

  public DataSize getMaxAppendBatchSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-append-batch-size",
        maxAppendBatchSize,
        DataSize.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_MAX_APPEND_BATCH_SIZE));
  }

  public void setMaxAppendBatchSize(final DataSize maxAppendBatchSize) {
    this.maxAppendBatchSize = maxAppendBatchSize;
  }

  public Duration getRequestTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".request-timeout",
        requestTimeout,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_REQUEST_TIMEOUT));
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public Duration getSnapshotRequestTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".snapshot-request-timeout",
        snapshotRequestTimeout,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_SNAPSHOT_REQUEST_TIMEOUT));
  }

  public void setSnapshotRequestTimeout(final Duration snapshotRequestTimeout) {
    this.snapshotRequestTimeout = snapshotRequestTimeout;
  }

  public DataSize getSnapshotChunkSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".snapshot-chunk-size",
        snapshotChunkSize,
        DataSize.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_SNAPSHOT_CHUNK_SIZE));
  }

  public void setSnapshotChunkSize(final DataSize snapshotChunkSize) {
    this.snapshotChunkSize = snapshotChunkSize;
  }

  public Duration getConfigurationChangeTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".configuration-change-timeout",
        configurationChangeTimeout,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_CONFIGURATION_CHANGE_TIMEOUT));
  }

  public void setConfigurationChangeTimeout(final Duration configurationChangeTimeout) {
    this.configurationChangeTimeout = configurationChangeTimeout;
  }

  public Duration getMaxQuorumResponseTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-quorum-response-timeout",
        maxQuorumResponseTimeout,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_MAX_QUORUM_RESPONSE_TIMEOUT));
  }

  public void setMaxQuorumResponseTimeout(final Duration maxQuorumResponseTimeout) {
    this.maxQuorumResponseTimeout = maxQuorumResponseTimeout;
  }

  public int getMinStepDownFailureCount() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".min-step-down-failure-count",
        minStepDownFailureCount,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_MIN_STEP_DOWN_FAILURE_COUNT));
  }

  public void setMinStepDownFailureCount(final int minStepDownFailureCount) {
    this.minStepDownFailureCount = minStepDownFailureCount;
  }

  public int getPreferSnapshotReplicationThreshold() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".prefer-snapshot-replication-threshold",
        preferSnapshotReplicationThreshold,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_PREFER_SNAPSHOT_REPLICATION_THRESHOLD));
  }

  public void setPreferSnapshotReplicationThreshold(final int preferSnapshotReplicationThreshold) {
    this.preferSnapshotReplicationThreshold = preferSnapshotReplicationThreshold;
  }

  public boolean isPreallocateSegmentFiles() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".preallocate-segment-files",
        preallocateSegmentFiles,
        Boolean.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_PREALLOCATE_SEGMENT_FILES));
  }

  public void setPreallocateSegmentFiles(final boolean preallocateSegmentFiles) {
    this.preallocateSegmentFiles = preallocateSegmentFiles;
  }
}
