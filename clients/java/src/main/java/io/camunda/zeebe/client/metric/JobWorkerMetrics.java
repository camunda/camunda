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
package io.camunda.zeebe.client.metric;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.util.concurrent.ScheduledExecutorService;

public interface JobWorkerMetrics {

  /**
   * Possible implementation in the Micrometer's JobWorkerMetrics: {@link
   * io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics#monitor(io.micrometer.core.instrument.MeterRegistry,
   * ScheduledExecutorService, String, String, Iterable) }
   *
   * @param scheduledExecutorService to monitor
   * @return monitorable {@link ScheduledExecutorService}
   */
  ScheduledExecutorService wrapMonitorableScheduledExecutorService(
      final ScheduledExecutorService scheduledExecutorService);

  void currentRemainingJobs(final int remainingJobs);

  void currentPollInterval(final long pollInterval);

  void currentIsPollScheduled(final boolean isPollScheduled);

  void currentAcquiringJobs(final boolean acquiringJobs);

  void jobCreated(final ActivatedJob activatedJob);

  void jobFinished(final ActivatedJob activatedJob);
}
