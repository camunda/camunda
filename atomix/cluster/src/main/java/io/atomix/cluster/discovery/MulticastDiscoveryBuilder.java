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

import java.time.Duration;

/** Multicast discovery provider builder. */
public class MulticastDiscoveryBuilder extends NodeDiscoveryBuilder {
  private final MulticastDiscoveryConfig config = new MulticastDiscoveryConfig();

  protected MulticastDiscoveryBuilder() {}

  /**
   * Sets the broadcast interval.
   *
   * @param broadcastInterval the broadcast interval
   * @return the location provider builder
   */
  public MulticastDiscoveryBuilder withBroadcastInterval(final Duration broadcastInterval) {
    config.setBroadcastInterval(broadcastInterval);
    return this;
  }

  /**
   * Sets the phi accrual failure threshold.
   *
   * @param failureThreshold the phi accrual failure threshold
   * @return the location provider builder
   */
  public MulticastDiscoveryBuilder withFailureThreshold(final int failureThreshold) {
    config.setFailureThreshold(failureThreshold);
    return this;
  }

  /**
   * Sets the failure timeout to use prior to phi failure detectors being populated.
   *
   * @param failureTimeout the failure timeout
   * @return the location provider builder
   */
  public MulticastDiscoveryBuilder withFailureTimeout(final Duration failureTimeout) {
    config.setFailureTimeout(failureTimeout);
    return this;
  }

  @Override
  public NodeDiscoveryProvider build() {
    return new MulticastDiscoveryProvider(config);
  }
}
