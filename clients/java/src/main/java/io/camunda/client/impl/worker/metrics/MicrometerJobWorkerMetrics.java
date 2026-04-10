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
package io.camunda.client.impl.worker.metrics;

import io.camunda.client.api.worker.JobWorkerMetrics;
import io.micrometer.core.instrument.Counter;
import java.util.Objects;

public final class MicrometerJobWorkerMetrics implements JobWorkerMetrics {

  private final Counter jobActivatedCounter;
  private final Counter jobHandledCounter;

  public MicrometerJobWorkerMetrics(
      final Counter jobActivatedCounter, final Counter jobHandledCounter) {
    this.jobActivatedCounter =
        Objects.requireNonNull(jobActivatedCounter, "must specify a job activated counter");
    this.jobHandledCounter =
        Objects.requireNonNull(jobHandledCounter, "must specify a job handled counter");
  }

  @Override
  public void jobActivated(final int count) {
    jobActivatedCounter.increment(count);
  }

  @Override
  public void jobHandled(final int count) {
    jobHandledCounter.increment(count);
  }
}
