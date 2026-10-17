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
package io.camunda.client.api.metrics;

import io.micrometer.core.instrument.MeterRegistry;

/** Builds {@link CamundaClientMetrics} recorded to a Micrometer registry. */
public interface MicrometerCamundaClientMetricsBuilder {

  /**
   * Specifies where the client metrics will be registered. If null, {@link
   * io.micrometer.core.instrument.Metrics#globalRegistry} is used.
   *
   * @param meterRegistry the meter registry to use
   * @return this builder for chaining
   */
  MicrometerCamundaClientMetricsBuilder withMeterRegistry(MeterRegistry meterRegistry);

  CamundaClientMetrics build();
}
