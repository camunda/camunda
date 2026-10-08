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
package io.camunda.client.metrics;

import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.client.event.CamundaClientCreatedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

public class MicrometerJobWorkerMetricsFactory implements JobWorkerMetricsFactory {
  private final MeterRegistry meterRegistry;

  public MicrometerJobWorkerMetricsFactory(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public JobWorkerMetrics createJobWorkerMetrics(final JobWorkerMetricsFactoryContext context) {
    return createJobWorkerMetrics(context, null);
  }

  @Override
  public JobWorkerMetrics createJobWorkerMetrics(
      final JobWorkerMetricsFactoryContext context, final String physicalTenantId) {
    // always tagged, so that every meter of the same name has the same tag keys
    final String tenantId =
        physicalTenantId != null ? physicalTenantId : CamundaClientCreatedEvent.DEFAULT_CLIENT_NAME;
    final Tags tags = Tags.of("type", context.type(), "physicalTenantId", tenantId);
    return JobWorkerMetrics.micrometer().withMeterRegistry(meterRegistry).withTags(tags).build();
  }
}
