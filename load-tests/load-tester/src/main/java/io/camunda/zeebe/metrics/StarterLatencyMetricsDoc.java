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

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

public enum StarterLatencyMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * The response latency when starting process instances. It measures the time from sending the
   * request to receiving the response.
   */
  RESPONSE_LATENCY {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(10),
      Duration.ofMillis(25),
      Duration.ofMillis(50),
      Duration.ofMillis(75),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(2500),
      Duration.ofSeconds(5)
    };

    @Override
    public String getDescription() {
      return "The response latency when starting process instances. It measures the time from sending the request to receiving the response.";
    }

    @Override
    public String getName() {
      return "starter.response.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }
  }
}
