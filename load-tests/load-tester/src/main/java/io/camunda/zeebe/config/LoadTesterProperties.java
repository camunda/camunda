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
package io.camunda.zeebe.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "load-tester")
public class LoadTesterProperties {

  private boolean monitorDataAvailability = true;
  private Duration monitorDataAvailabilityInterval = Duration.ofMillis(250);

  @NestedConfigurationProperty private StarterProperties starter = new StarterProperties();

  @NestedConfigurationProperty private WorkerProperties worker = new WorkerProperties();

  public boolean isMonitorDataAvailability() {
    return monitorDataAvailability;
  }

  public void setMonitorDataAvailability(final boolean monitorDataAvailability) {
    this.monitorDataAvailability = monitorDataAvailability;
  }

  public Duration getMonitorDataAvailabilityInterval() {
    return monitorDataAvailabilityInterval;
  }

  public void setMonitorDataAvailabilityInterval(final Duration monitorDataAvailabilityInterval) {
    this.monitorDataAvailabilityInterval = monitorDataAvailabilityInterval;
  }

  public StarterProperties getStarter() {
    return starter;
  }

  public void setStarter(final StarterProperties starter) {
    this.starter = starter;
  }

  public WorkerProperties getWorker() {
    return worker;
  }

  public void setWorker(final WorkerProperties worker) {
    this.worker = worker;
  }
}
