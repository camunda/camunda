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
package io.atomix.cluster;

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.utils.config.Config;
import java.time.Duration;

/** Cluster membership configuration. */
public class MembershipConfig implements Config {
  private static final int DEFAULT_BROADCAST_INTERVAL = 100;
  private static final int DEFAULT_REACHABILITY_TIMEOUT = 10000;
  private static final int DEFAULT_REACHABILITY_THRESHOLD = 10;

  private Duration broadcastInterval = Duration.ofMillis(DEFAULT_BROADCAST_INTERVAL);
  private int reachabilityThreshold = DEFAULT_REACHABILITY_THRESHOLD;
  private Duration reachabilityTimeout = Duration.ofMillis(DEFAULT_REACHABILITY_TIMEOUT);

  /**
   * Returns the reachability broadcast interval.
   *
   * @return the reachability broadcast interval
   */
  public Duration getBroadcastInterval() {
    return broadcastInterval;
  }

  /**
   * Sets the reachability broadcast interval.
   *
   * @param broadcastInterval the reachability broadcast interval
   * @return the membership configuration
   */
  public MembershipConfig setBroadcastInterval(final Duration broadcastInterval) {
    this.broadcastInterval = checkNotNull(broadcastInterval);
    return this;
  }

  /**
   * Returns the reachability failure detection threshold.
   *
   * @return the reachability failure detection threshold
   */
  public int getReachabilityThreshold() {
    return reachabilityThreshold;
  }

  /**
   * Sets the reachability failure detection threshold.
   *
   * @param reachabilityThreshold the reachability failure detection threshold
   * @return the membership configuration
   */
  public MembershipConfig setReachabilityThreshold(final int reachabilityThreshold) {
    this.reachabilityThreshold = reachabilityThreshold;
    return this;
  }

  /**
   * Returns the reachability failure timeout.
   *
   * @return the reachability failure timeout
   */
  public Duration getReachabilityTimeout() {
    return reachabilityTimeout;
  }

  /**
   * Sets the reachability failure timeout.
   *
   * @param reachabilityTimeout the reachability failure timeout
   * @return the membership configuration
   */
  public MembershipConfig setReachabilityTimeout(final Duration reachabilityTimeout) {
    this.reachabilityTimeout = checkNotNull(reachabilityTimeout);
    return this;
  }
}
