/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.metrics;

import org.springframework.boot.actuate.availability.ReadinessStateHealthIndicator;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.stereotype.Component;

/**
 * Readiness indicator that reports {@link ReadinessState#ACCEPTING_TRAFFIC} once the {@link
 * ConnectionMonitor} has retrieved the cluster topology.
 */
@Component
public class ConnectionReadyHealthIndicator extends ReadinessStateHealthIndicator {

  private final ConnectionMonitor connectionMonitor;

  public ConnectionReadyHealthIndicator(
      final ApplicationAvailability availability, final ConnectionMonitor connectionMonitor) {
    super(availability);
    this.connectionMonitor = connectionMonitor;
  }

  @Override
  protected AvailabilityState getState(final ApplicationAvailability applicationAvailability) {
    return connectionMonitor.isConnected()
        ? ReadinessState.ACCEPTING_TRAFFIC
        : ReadinessState.REFUSING_TRAFFIC;
  }
}
