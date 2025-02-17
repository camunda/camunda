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
package io.camunda.client.api.worker.metrics;

import io.camunda.client.api.worker.JobWorkerMetrics;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

/**
 * Builder interface for the {@link JobWorkerMetrics} backed by Micrometer. This is an optional
 * feature which requires you to add <a href="https://micrometer.io">Micrometer</a> to your
 * classpath (see <a href="https://micrometer.io/docs/installing">the installation guide</a> for
 * more).
 *
 * <p>This will create a {@link JobWorkerMetrics} implementation which will track the following
 * metrics:
 *
 * <ul>
 *   <li>A counter for the jobs activated count
 *   <li>A counter for the jobs handled count
 * </ul>
 *
 * From these counters you can derive the rate of jobs activated, the rate of jobs handled, and
 * subtract both to estimate the count/rate of jobs queued in a given worker.
 *
 * <p>NOTE: the names may be changed depending on the registry backing Micrometer (e.g. Prometheus
 * names will replace the periods with underscore, etc.)
 */
public interface MicrometerJobWorkerMetricsBuilder {

  /**
   * Specifies where the worker metrics will be registered. If null, {@link
   * io.micrometer.core.instrument.Metrics#globalRegistry} is used.
   *
   * @param meterRegistry the meter registry to use
   * @return this builder for chaining
   */
  MicrometerJobWorkerMetricsBuilder withMeterRegistry(final MeterRegistry meterRegistry);

  /**
   * Tags which will be applied to all worker metrics. Can be null.
   *
   * @param tags the tags to apply to all metrics
   * @return this builder for chaining
   */
  MicrometerJobWorkerMetricsBuilder withTags(final Iterable<Tag> tags);

  JobWorkerMetrics build();

  /** Set of possible metrics/metric names. */
  @SuppressWarnings("NullableProblems")
  enum Names implements KeyName {
    /** Counter backing the {@link JobWorkerMetrics#jobActivated(int)} count. */
    JOB_ACTIVATED {
      @Override
      public String asString() {
        return "zeebe.client.worker.job.activated";
      }
    },

    /** Counter backing the {@link JobWorkerMetrics#jobHandled(int)} count. */
    JOB_HANDLED {
      @Override
      public String asString() {
        return "zeebe.client.worker.job.handled";
      }
    }
  }
}
