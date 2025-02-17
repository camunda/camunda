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
package io.camunda.zeebe.client.api.worker;

import io.camunda.zeebe.client.api.worker.metrics.MicrometerJobWorkerMetricsBuilder;
import io.camunda.zeebe.client.impl.worker.metrics.MicrometerJobWorkerMetricsBuilderImpl;

/**
 * Worker metrics API. Allows basic instrumenting of job activation and handling.
 *
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.worker.JobWorkerMetrics}
 */
@Deprecated
public interface JobWorkerMetrics {

  /**
   * Called every time one or more jobs are activated.
   *
   * <p>NOTE: this is called <em>before</em> the job is worked on.
   *
   * @param count the amount of jobs that were activated
   */
  default void jobActivated(final int count) {}

  /**
   * Called every time one or more jobs are handled.
   *
   * <p>NOTE: this is called <em>after</em> a job has been worked on, successfully or not.
   *
   * @param count the amount of jobs that were handled
   */
  default void jobHandled(final int count) {}

  /**
   * Returns a new builder for the Micrometer bridge.
   *
   * @throws UnsupportedOperationException if Micrometer is not found in the class path
   */
  static MicrometerJobWorkerMetricsBuilder micrometer() {
    try {
      Class.forName("io.micrometer.core.instrument.MeterRegistry");
    } catch (final ClassNotFoundException e) {
      throw new UnsupportedOperationException(
          "Expected to create Micrometer worker metrics, but it seems Micrometer is not in your classpath",
          e);
    }

    return new MicrometerJobWorkerMetricsBuilderImpl();
  }

  /** Returns an implementation which does nothing. */
  static JobWorkerMetrics noop() {
    return new JobWorkerMetrics() {};
  }
}
