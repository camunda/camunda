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

import io.atomix.raft.zeebe.EntryValidator;
import java.time.Duration;

/** Configurations for a single partition. */
public class RaftPartitionConfig {

  private static final Duration DEFAULT_ELECTION_TIMEOUT = Duration.ofMillis(2500);
  private static final Duration DEFAULT_SNAPSHOT_REQUEST_TIMEOUT = Duration.ofMillis(2500);
  private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofMillis(250);
  private static final boolean DEFAULT_PRIORITY_ELECTION = true;
  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final int DEFAULT_MIN_STEP_DOWN_FAILURE_COUNT = 3;
  private static final Duration DEFAULT_MAX_QUORUM_RESPONSE_TIMEOUT = Duration.ofSeconds(0);
  private static final int DEFAULT_SNAPSHOT_REPLICATION_THRESHOLD = 100;
  private static final String DEFAULT_ENGINE_NAME = "default";
  private static final boolean DEFAULT_RECEIVE_ON_LEGACY_SUBJECT = true;
  private static final boolean DEFAULT_SEND_ON_LEGACY_SUBJECT = true;

  private Duration electionTimeout = DEFAULT_ELECTION_TIMEOUT;
  private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
  private int maxAppendsPerFollower = 2;
  private int maxAppendBatchSize = 32 * 1024;
  private boolean priorityElectionEnabled = DEFAULT_PRIORITY_ELECTION;
  private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
  private Duration snapshotRequestTimeout = DEFAULT_SNAPSHOT_REQUEST_TIMEOUT;
  private int minStepDownFailureCount = DEFAULT_MIN_STEP_DOWN_FAILURE_COUNT;
  private Duration maxQuorumResponseTimeout = DEFAULT_MAX_QUORUM_RESPONSE_TIMEOUT;
  private int preferSnapshotReplicationThreshold = DEFAULT_SNAPSHOT_REPLICATION_THRESHOLD;
  private RaftStorageConfig storageConfig;
  private EntryValidator entryValidator;
  private Duration configurationChangeTimeout;
  private int snapshotChunkSize;
  private String engineName = DEFAULT_ENGINE_NAME;
  private boolean receiveOnLegacySubject = DEFAULT_RECEIVE_ON_LEGACY_SUBJECT;
  private boolean sendOnLegacySubject = DEFAULT_SEND_ON_LEGACY_SUBJECT;

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

  public int getSnapshotChunkSize() {
    return snapshotChunkSize;
  }

  public void setSnapshotChunkSize(final int snapshotChunkSize) {
    this.snapshotChunkSize = snapshotChunkSize;
  }

  public Duration getConfigurationChangeTimeout() {
    return configurationChangeTimeout;
  }

  public void setConfigurationChangeTimeout(final Duration configurationChangeTimeout) {
    this.configurationChangeTimeout = configurationChangeTimeout;
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

  public int getPreferSnapshotReplicationThreshold() {
    return preferSnapshotReplicationThreshold;
  }

  public void setPreferSnapshotReplicationThreshold(final int preferSnapshotReplicationThreshold) {
    this.preferSnapshotReplicationThreshold = preferSnapshotReplicationThreshold;
  }

  public RaftStorageConfig getStorageConfig() {
    return storageConfig;
  }

  public void setStorageConfig(final RaftStorageConfig storageConfig) {
    this.storageConfig = storageConfig;
  }

  public EntryValidator getEntryValidator() {
    return entryValidator;
  }

  public void setEntryValidator(final EntryValidator entryValidator) {
    this.entryValidator = entryValidator;
  }

  public String getEngineName() {
    return engineName;
  }

  public void setEngineName(final String engineName) {
    this.engineName = engineName;
  }

  public boolean isReceiveOnLegacySubject() {
    return receiveOnLegacySubject;
  }

  public void setReceiveOnLegacySubject(final boolean receiveOnLegacySubject) {
    this.receiveOnLegacySubject = receiveOnLegacySubject;
  }

  public boolean isSendOnLegacySubject() {
    return sendOnLegacySubject;
  }

  public void setSendOnLegacySubject(final boolean sendOnLegacySubject) {
    this.sendOnLegacySubject = sendOnLegacySubject;
  }

  @Override
  public String toString() {
    return "RaftPartitionConfig{"
        + "electionTimeout="
        + electionTimeout
        + ", heartbeatInterval="
        + heartbeatInterval
        + ", maxAppendsPerFollower="
        + maxAppendsPerFollower
        + ", maxAppendBatchSize="
        + maxAppendBatchSize
        + ", priorityElectionEnabled="
        + priorityElectionEnabled
        + ", requestTimeout="
        + requestTimeout
        + ", snapshotRequestTimeout="
        + snapshotRequestTimeout
        + ", snapshotChunkSize="
        + snapshotChunkSize
        + ", configurationChangeTimeout="
        + configurationChangeTimeout
        + ", minStepDownFailureCount="
        + minStepDownFailureCount
        + ", maxQuorumResponseTimeout="
        + maxQuorumResponseTimeout
        + ", preferSnapshotReplicationThreshold="
        + preferSnapshotReplicationThreshold
        + ", engineName="
        + engineName
        + ", receiveOnLegacySubject="
        + receiveOnLegacySubject
        + ", sendOnLegacySubject="
        + sendOnLegacySubject
        + '}';
  }
}
