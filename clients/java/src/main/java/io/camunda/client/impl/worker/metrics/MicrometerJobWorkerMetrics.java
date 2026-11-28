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
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MicrometerJobWorkerMetrics implements JobWorkerMetrics {

  private final Counter jobActivatedCounter;
  private final Counter jobHandledCounter;
  private final Counter jobRefusedCounter;
  private final Counter jobExpiredCounter;
  private final Counter streamInactivityRecreatedCounter;
  private final LongTaskTimer pollInFlightTimer;
  // The sample of this worker's poll in flight. A worker has at most one, while the timer is shared
  // by every worker with the same tags, so each worker keeps its own sample to stop.
  private final AtomicReference<Sample> pollInFlight = new AtomicReference<>();

  /** Creates metrics that do not track the polls in flight. */
  public MicrometerJobWorkerMetrics(
      final Counter jobActivatedCounter,
      final Counter jobHandledCounter,
      final Counter jobRefusedCounter,
      final Counter jobExpiredCounter,
      final Counter streamInactivityRecreatedCounter) {
    this(
        jobActivatedCounter,
        jobHandledCounter,
        jobRefusedCounter,
        jobExpiredCounter,
        streamInactivityRecreatedCounter,
        null);
  }

  public MicrometerJobWorkerMetrics(
      final Counter jobActivatedCounter,
      final Counter jobHandledCounter,
      final Counter jobRefusedCounter,
      final Counter jobExpiredCounter,
      final Counter streamInactivityRecreatedCounter,
      final LongTaskTimer pollInFlightTimer) {
    this.pollInFlightTimer = pollInFlightTimer;
    this.jobActivatedCounter =
        Objects.requireNonNull(jobActivatedCounter, "must specify a job activated counter");
    this.jobHandledCounter =
        Objects.requireNonNull(jobHandledCounter, "must specify a job handled counter");
    this.jobRefusedCounter =
        Objects.requireNonNull(jobRefusedCounter, "must specify a job refused counter");
    this.jobExpiredCounter =
        Objects.requireNonNull(jobExpiredCounter, "must specify a job expired counter");
    this.streamInactivityRecreatedCounter =
        Objects.requireNonNull(
            streamInactivityRecreatedCounter, "must specify a stream inactivity recreated counter");
  }

  @Override
  public void jobActivated(final int count) {
    jobActivatedCounter.increment(count);
  }

  @Override
  public void jobHandled(final int count) {
    jobHandledCounter.increment(count);
  }

  @Override
  public void jobRefused(final int count) {
    jobRefusedCounter.increment(count);
  }

  @Override
  public void jobExpired(final int count) {
    jobExpiredCounter.increment(count);
  }

  @Override
  public void streamInactivityRecreated() {
    streamInactivityRecreatedCounter.increment();
  }

  @Override
  public void pollStarted() {
    if (pollInFlightTimer == null) {
      return;
    }
    // A poll still in flight here would be left running forever if it were just replaced
    stop(pollInFlight.getAndSet(pollInFlightTimer.start()));
  }

  @Override
  public void pollCompleted() {
    stop(pollInFlight.getAndSet(null));
  }

  private static void stop(final Sample sample) {
    if (sample != null) {
      sample.stop();
    }
  }
}
