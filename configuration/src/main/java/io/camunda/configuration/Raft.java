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
import org.springframework.util.unit.DataSize;

/**
 * Configuration for the Raft consensus protocol in the cluster. This class provides settings for
 * Raft algorithm parameters including timing, elections, and log flushing.
 */
public class Raft {
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
    return heartbeatInterval;
  }

  public void setHeartbeatInterval(final Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  public Duration getElectionTimeout() {
    return electionTimeout;
  }

  public void setElectionTimeout(final Duration electionTimeout) {
    this.electionTimeout = electionTimeout;
  }

  public boolean isPriorityElectionEnabled() {
    return priorityElectionEnabled;
  }

  public void setPriorityElectionEnabled(final boolean priorityElectionEnabled) {
    this.priorityElectionEnabled = priorityElectionEnabled;
  }

  public boolean isFlushEnabled() {
    return flushEnabled;
  }

  public void setFlushEnabled(final boolean flushEnabled) {
    this.flushEnabled = flushEnabled;
  }

  public Duration getFlushDelay() {
    return flushDelay;
  }

  public void setFlushDelay(final Duration flushDelay) {
    this.flushDelay = flushDelay;
  }

  public int getMaxAppendsPerFollower() {
    return maxAppendsPerFollower;
  }

  public void setMaxAppendsPerFollower(final int maxAppendsPerFollower) {
    this.maxAppendsPerFollower = maxAppendsPerFollower;
  }

  public DataSize getMaxAppendBatchSize() {
    return maxAppendBatchSize;
  }

  public void setMaxAppendBatchSize(final DataSize maxAppendBatchSize) {
    this.maxAppendBatchSize = maxAppendBatchSize;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public Duration getSnapshotRequestTimeout() {
    return snapshotRequestTimeout;
  }

  public void setSnapshotRequestTimeout(final Duration snapshotRequestTimeout) {
    this.snapshotRequestTimeout = snapshotRequestTimeout;
  }

  public DataSize getSnapshotChunkSize() {
    return snapshotChunkSize;
  }

  public void setSnapshotChunkSize(final DataSize snapshotChunkSize) {
    this.snapshotChunkSize = snapshotChunkSize;
  }

  public Duration getConfigurationChangeTimeout() {
    return configurationChangeTimeout;
  }

  public void setConfigurationChangeTimeout(final Duration configurationChangeTimeout) {
    this.configurationChangeTimeout = configurationChangeTimeout;
  }

  public Duration getMaxQuorumResponseTimeout() {
    return maxQuorumResponseTimeout;
  }

  public void setMaxQuorumResponseTimeout(final Duration maxQuorumResponseTimeout) {
    this.maxQuorumResponseTimeout = maxQuorumResponseTimeout;
  }

  public int getMinStepDownFailureCount() {
    return minStepDownFailureCount;
  }

  public void setMinStepDownFailureCount(final int minStepDownFailureCount) {
    this.minStepDownFailureCount = minStepDownFailureCount;
  }

  public int getPreferSnapshotReplicationThreshold() {
    return preferSnapshotReplicationThreshold;
  }

  public void setPreferSnapshotReplicationThreshold(final int preferSnapshotReplicationThreshold) {
    this.preferSnapshotReplicationThreshold = preferSnapshotReplicationThreshold;
  }

  public boolean isPreallocateSegmentFiles() {
    return preallocateSegmentFiles;
  }

  public void setPreallocateSegmentFiles(final boolean preallocateSegmentFiles) {
    this.preallocateSegmentFiles = preallocateSegmentFiles;
  }
}
