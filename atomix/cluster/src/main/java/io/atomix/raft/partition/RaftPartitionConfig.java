/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.partition;

import java.time.Duration;

/** Configurations for a single partition. */
public class RaftPartitionConfig {

  private static final Duration DEFAULT_ELECTION_TIMEOUT = Duration.ofMillis(2500);
  private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofMillis(250);
  private static final boolean DEFAULT_PRIORITY_ELECTION = true;
  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final int DEFAULT_MIN_STEP_DOWN_FAILURE_COUNT = 3;
  private static final Duration DEFAULT_MAX_QUORUM_RESPONSE_TIMEOUT = Duration.ofSeconds(0);
  private static final RoundRobinPartitionDistributor DEFAULT_PARTITION_DISTRIBUTOR =
      new RoundRobinPartitionDistributor();
  private static final int DEFAULT_SNAPSHOT_REPLICATION_THRESHOLD = 100;

  private Duration electionTimeout = DEFAULT_ELECTION_TIMEOUT;
  private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
  private int maxAppendsPerFollower = 2;
  private int maxAppendBatchSize = 32 * 1024;
  private boolean priorityElectionEnabled = DEFAULT_PRIORITY_ELECTION;
  private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
  private Duration snapshotRequestTimeout = DEFAULT_REQUEST_TIMEOUT;
  private int minStepDownFailureCount = DEFAULT_MIN_STEP_DOWN_FAILURE_COUNT;
  private Duration maxQuorumResponseTimeout = DEFAULT_MAX_QUORUM_RESPONSE_TIMEOUT;
  private PartitionDistributor partitionDistributor = DEFAULT_PARTITION_DISTRIBUTOR;
  private int preferSnapshotReplicationThreshold = DEFAULT_SNAPSHOT_REPLICATION_THRESHOLD;

  /**
   * Returns the Raft leader election timeout.
   *
   * @return the Raft leader election timeout
   */
  public Duration getElectionTimeout() {
    return electionTimeout;
  }

  /**
   * Sets the leader election timeout.
   *
   * @param electionTimeout the leader election timeout
   * @return the Raft partition group configuration
   */
  public RaftPartitionConfig setElectionTimeout(final Duration electionTimeout) {
    this.electionTimeout = electionTimeout;
    return this;
  }

  /**
   * Returns the heartbeat interval.
   *
   * @return the heartbeat interval
   */
  public Duration getHeartbeatInterval() {
    return heartbeatInterval;
  }

  /**
   * Sets the heartbeat interval.
   *
   * @param heartbeatInterval the heartbeat interval
   * @return the Raft partition group configuration
   */
  public RaftPartitionConfig setHeartbeatInterval(final Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
    return this;
  }

  public int getMaxAppendsPerFollower() {
    return maxAppendsPerFollower;
  }

  public void setMaxAppendsPerFollower(final int maxAppendsPerFollower) {
    this.maxAppendsPerFollower = maxAppendsPerFollower;
  }

  public int getMaxAppendBatchSize() {
    return maxAppendBatchSize;
  }

  public void setMaxAppendBatchSize(final int maxAppendBatchSize) {
    this.maxAppendBatchSize = maxAppendBatchSize;
  }

  public boolean isPriorityElectionEnabled() {
    return priorityElectionEnabled;
  }

  public void setPriorityElectionEnabled(final boolean enable) {
    priorityElectionEnabled = enable;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  /**
   * Sets the timeout for every requests send between the replicas.
   *
   * @param requestTimeout the request timeout
   */
  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public Duration getSnapshotRequestTimeout() {
    return snapshotRequestTimeout;
  }

  /**
   * Sets the timeout for every snapshot request sent by raft leaders to the followers.
   *
   * @param snapshotRequestTimeout the request timeout
   */
  public void setSnapshotRequestTimeout(final Duration snapshotRequestTimeout) {
    this.snapshotRequestTimeout = snapshotRequestTimeout;
  }

  public int getMinStepDownFailureCount() {
    return minStepDownFailureCount;
  }

  /**
   * If the leader is not able to reach the quorum, the leader may step down. This is triggered
   * after minStepDownFailureCount number of requests fails to get a response from the quorum of
   * followers as well as if the last response was received before maxQuorumResponseTime.
   *
   * @param minStepDownFailureCount The number of failures after which a leader considers stepping
   *     down.
   */
  public void setMinStepDownFailureCount(final int minStepDownFailureCount) {
    this.minStepDownFailureCount = minStepDownFailureCount;
  }

  public Duration getMaxQuorumResponseTimeout() {
    return maxQuorumResponseTimeout;
  }

  /**
   * If the leader is not able to reach the quorum, the leader may step down. This is triggered
   * after minStepDownFailureCount number of requests fails to get a response from the quorum of
   * followers as well as if the last response was received before maxQuorumResponseTime.
   *
   * <p>When this value is zero, it uses a default value of electionTimeout * 2
   *
   * @param maxQuorumResponseTimeout the quorum response time out to trigger leader step down
   */
  public void setMaxQuorumResponseTimeout(final Duration maxQuorumResponseTimeout) {
    this.maxQuorumResponseTimeout = maxQuorumResponseTimeout;
  }

  public PartitionDistributor getPartitionDistributor() {
    return partitionDistributor;
  }

  public void setPartitionDistributor(final PartitionDistributor partitionDistributor) {
    this.partitionDistributor = partitionDistributor;
  }

  public int getPreferSnapshotReplicationThreshold() {
    return preferSnapshotReplicationThreshold;
  }

  public void setPreferSnapshotReplicationThreshold(final int preferSnapshotReplicationThreshold) {
    this.preferSnapshotReplicationThreshold = preferSnapshotReplicationThreshold;
  }
}
