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
package io.camunda.zeebe.client.impl.worker.metrics;

import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import io.camunda.zeebe.client.api.worker.metrics.MicrometerJobWorkerMetricsBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

public final class MicrometerJobWorkerMetricsBuilderImpl
    implements MicrometerJobWorkerMetricsBuilder {
  private MeterRegistry meterRegistry = Metrics.globalRegistry;
  private Iterable<Tag> tags = Tags.empty();

  @Override
  public MicrometerJobWorkerMetricsBuilder withMeterRegistry(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry == null ? Metrics.globalRegistry : meterRegistry;
    return this;
  }

  @Override
  public MicrometerJobWorkerMetricsBuilder withTags(final Iterable<Tag> tags) {
    this.tags = tags == null ? Tags.empty() : tags;
    return this;
  }

  @Override
  public JobWorkerMetrics build() {
    final Counter jobActivatedCounter = meterRegistry.counter(Names.JOB_ACTIVATED.asString(), tags);
    final Counter jobHandledCounter = meterRegistry.counter(Names.JOB_HANDLED.asString(), tags);
    return new MicrometerJobWorkerMetrics(jobActivatedCounter, jobHandledCounter);
  }
}
