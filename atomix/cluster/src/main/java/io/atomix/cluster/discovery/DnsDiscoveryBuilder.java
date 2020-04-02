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

/** DNS discovery builder. */
public class DnsDiscoveryBuilder extends NodeDiscoveryBuilder {
  private final DnsDiscoveryConfig config = new DnsDiscoveryConfig();

  /**
   * Sets the DNS service name.
   *
   * @param service the DNS service name
   * @return the DNS discovery builder
   */
  public DnsDiscoveryBuilder withService(final String service) {
    config.setService(service);
    return this;
  }

  /**
   * Sets the DNS resolution interval.
   *
   * @param resolutionInterval the DNS resolution interval
   * @return the DNS configuration
   */
  public DnsDiscoveryBuilder withResolutionInterval(final Duration resolutionInterval) {
    config.setResolutionInterval(resolutionInterval);
    return this;
  }

  @Override
  public NodeDiscoveryProvider build() {
    return new DnsDiscoveryProvider(config);
  }
}
