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
package io.camunda.client.impl.metrics;

import io.camunda.client.api.metrics.CamundaClientMetrics;
import io.camunda.client.api.metrics.MicrometerCamundaClientMetricsBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;

public final class MicrometerCamundaClientMetricsBuilderImpl
    implements MicrometerCamundaClientMetricsBuilder {

  private MeterRegistry meterRegistry = Metrics.globalRegistry;

  @Override
  public MicrometerCamundaClientMetricsBuilder withMeterRegistry(
      final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry == null ? Metrics.globalRegistry : meterRegistry;
    return this;
  }

  @Override
  public CamundaClientMetrics build() {
    final MeterRegistry registry = meterRegistry;
    return (ClientMetricsFactory)
        physicalTenantId -> new MicrometerClientMetrics(registry, physicalTenantId);
  }
}
