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
package io.atomix.cluster.discovery;

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.NodeConfig;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;

/** Bootstrap discovery configuration. */
public class BootstrapDiscoveryConfig extends NodeDiscoveryConfig {
  private static final int DEFAULT_HEARTBEAT_INTERVAL = 1000;
  private static final int DEFAULT_FAILURE_TIMEOUT = 10000;
  private static final int DEFAULT_PHI_FAILURE_THRESHOLD = 10;

  private Duration heartbeatInterval = Duration.ofMillis(DEFAULT_HEARTBEAT_INTERVAL);
  private int failureThreshold = DEFAULT_PHI_FAILURE_THRESHOLD;
  private Duration failureTimeout = Duration.ofMillis(DEFAULT_FAILURE_TIMEOUT);
  private Collection<NodeConfig> nodes = Collections.emptySet();

  @Override
  public NodeDiscoveryProvider.Type getType() {
    return BootstrapDiscoveryProvider.TYPE;
  }

  /**
   * Returns the configured bootstrap nodes.
   *
   * @return the configured bootstrap nodes
   */
  public Collection<NodeConfig> getNodes() {
    return nodes;
  }

  /**
   * Sets the bootstrap nodes.
   *
   * @param nodes the bootstrap nodes
   * @return the bootstrap provider configuration
   */
  public BootstrapDiscoveryConfig setNodes(final Collection<NodeConfig> nodes) {
    this.nodes = nodes;
    return this;
  }

  /**
   * Returns the heartbeat interval.
   *
   * @return the heartbeat interval
   */
  @Deprecated
  public Duration getHeartbeatInterval() {
    return heartbeatInterval;
  }

  /**
   * Sets the heartbeat interval.
   *
   * @param heartbeatInterval the heartbeat interval
   * @return the group membership configuration
   */
  @Deprecated
  public BootstrapDiscoveryConfig setHeartbeatInterval(final Duration heartbeatInterval) {
    this.heartbeatInterval = checkNotNull(heartbeatInterval);
    return this;
  }

  /**
   * Returns the failure detector threshold.
   *
   * @return the failure detector threshold
   */
  @Deprecated
  public int getFailureThreshold() {
    return failureThreshold;
  }

  /**
   * Sets the failure detector threshold.
   *
   * @param failureThreshold the failure detector threshold
   * @return the group membership configuration
   */
  @Deprecated
  public BootstrapDiscoveryConfig setFailureThreshold(final int failureThreshold) {
    this.failureThreshold = failureThreshold;
    return this;
  }

  /**
   * Returns the base failure timeout.
   *
   * @return the base failure timeout
   */
  @Deprecated
  public Duration getFailureTimeout() {
    return failureTimeout;
  }

  /**
   * Sets the base failure timeout.
   *
   * @param failureTimeout the base failure timeout
   * @return the group membership configuration
   */
  @Deprecated
  public BootstrapDiscoveryConfig setFailureTimeout(final Duration failureTimeout) {
    this.failureTimeout = checkNotNull(failureTimeout);
    return this;
  }
}
