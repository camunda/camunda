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
package io.atomix.cluster.protocol;

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Duration;

/** Gossip group membership protocol configuration. */
public class HeartbeatMembershipProtocolConfig extends GroupMembershipProtocolConfig {
  private static final int DEFAULT_HEARTBEAT_INTERVAL = 1000;
  private static final int DEFAULT_FAILURE_TIMEOUT = 10000;
  private static final int DEFAULT_PHI_FAILURE_THRESHOLD = 10;

  private Duration heartbeatInterval = Duration.ofMillis(DEFAULT_HEARTBEAT_INTERVAL);
  private int phiFailureThreshold = DEFAULT_PHI_FAILURE_THRESHOLD;
  private Duration failureTimeout = Duration.ofMillis(DEFAULT_FAILURE_TIMEOUT);

  @Override
  public GroupMembershipProtocol.Type getType() {
    return HeartbeatMembershipProtocol.TYPE;
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
   * @return the group membership configuration
   */
  public HeartbeatMembershipProtocolConfig setHeartbeatInterval(final Duration heartbeatInterval) {
    this.heartbeatInterval = checkNotNull(heartbeatInterval);
    return this;
  }

  /**
   * Returns the failure detector threshold.
   *
   * @return the failure detector threshold
   */
  public int getPhiFailureThreshold() {
    return phiFailureThreshold;
  }

  /**
   * Sets the failure detector threshold.
   *
   * @param phiFailureThreshold the failure detector threshold
   * @return the group membership configuration
   */
  public HeartbeatMembershipProtocolConfig setPhiFailureThreshold(final int phiFailureThreshold) {
    this.phiFailureThreshold = phiFailureThreshold;
    return this;
  }

  /**
   * Returns the base failure timeout.
   *
   * @return the base failure timeout
   */
  public Duration getFailureTimeout() {
    return failureTimeout;
  }

  /**
   * Sets the base failure timeout.
   *
   * @param failureTimeout the base failure timeout
   * @return the group membership configuration
   */
  public HeartbeatMembershipProtocolConfig setFailureTimeout(final Duration failureTimeout) {
    this.failureTimeout = checkNotNull(failureTimeout);
    return this;
  }
}
