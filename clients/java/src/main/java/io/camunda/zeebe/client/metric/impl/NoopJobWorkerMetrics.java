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
package io.camunda.zeebe.client.metric.impl;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.metric.JobWorkerMetrics;
import java.util.concurrent.ScheduledExecutorService;

public class NoopJobWorkerMetrics implements JobWorkerMetrics {

  private final String workerType;
  private final String workerName;

  public NoopJobWorkerMetrics(final String workerType, final String workerName) {
    this.workerType = workerType;
    this.workerName = workerName;
  }

  @Override
  public ScheduledExecutorService wrapMonitorableScheduledExecutorService(
      final ScheduledExecutorService scheduledExecutorService) {
    return scheduledExecutorService;
  }

  @Override
  public void currentRemainingJobs(final int remainingJobs) {}

  @Override
  public void currentPollInterval(final long pollInterval) {}

  @Override
  public void currentIsPollScheduled(final boolean isPollScheduled) {}

  @Override
  public void currentAcquiringJobs(final boolean acquiringJobs) {}

  @Override
  public void jobCreated(final ActivatedJob activatedJob) {}

  @Override
  public void jobFinished(final ActivatedJob activatedJob) {}
}
