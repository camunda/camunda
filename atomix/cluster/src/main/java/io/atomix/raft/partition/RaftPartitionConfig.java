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
  private static final boolean DEFAULT_PRIORITY_ELECTION = false;
  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private Duration electionTimeout = DEFAULT_ELECTION_TIMEOUT;
  private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
  private int maxAppendsPerFollower = 2;
  private int maxAppendBatchSize = 32 * 1024;
  private boolean priorityElectionEnabled = DEFAULT_PRIORITY_ELECTION;
  private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

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

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }
}
