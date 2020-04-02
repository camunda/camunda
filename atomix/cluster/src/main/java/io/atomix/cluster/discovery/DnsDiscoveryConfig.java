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

import java.time.Duration;

/** DNS discovery configuration. */
public class DnsDiscoveryConfig extends NodeDiscoveryConfig {
  private String service;
  private Duration resolutionInterval = Duration.ofSeconds(15);

  @Override
  public NodeDiscoveryProvider.Type getType() {
    return DnsDiscoveryProvider.TYPE;
  }

  /**
   * Returns the discovery service.
   *
   * @return the DNS service to use for discovery
   */
  public String getService() {
    return service;
  }

  /**
   * Sets the DNS service name.
   *
   * @param service the DNS service name
   * @return the DNS configuration
   */
  public DnsDiscoveryConfig setService(final String service) {
    this.service = checkNotNull(service);
    return this;
  }

  /**
   * Returns the DNS resolution interval.
   *
   * @return the DNS resolution interval
   */
  public Duration getResolutionInterval() {
    return resolutionInterval;
  }

  /**
   * Sets the DNS resolution interval.
   *
   * @param resolutionInterval the DNS resolution interval
   * @return the DNS configuration
   */
  public DnsDiscoveryConfig setResolutionInterval(final Duration resolutionInterval) {
    this.resolutionInterval = checkNotNull(resolutionInterval, "resolutionInterval cannot be null");
    return this;
  }
}
