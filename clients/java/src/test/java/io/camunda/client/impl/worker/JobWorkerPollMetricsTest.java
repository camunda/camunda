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
package io.camunda.client.impl.worker;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.client.api.worker.metrics.MicrometerJobWorkerMetricsBuilder.Names;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class JobWorkerPollMetricsTest {

  private static final Duration POLL_INTERVAL = Duration.ofSeconds(30);

  private final DeterministicScheduler executor = new DeterministicScheduler();
  private final CapturingJobPoller poller = new CapturingJobPoller();

  @Test
  void shouldReportAPollAsStartedWhileItHasNoResponse() {
    // given
    final CountingJobWorkerMetrics metrics = new CountingJobWorkerMetrics();

    try (final JobWorkerImpl ignored = createWorker(metrics)) {
      // when the worker sends its first poll and gets no answer
      executor.tick(POLL_INTERVAL.getSeconds(), TimeUnit.SECONDS);

      // then the poll counts as in flight, instead of the worker looking merely idle
      assertThat(metrics.pollsStarted).hasValue(1);
      assertThat(metrics.pollsCompleted).hasValue(0);
    }
  }

  @Test
  void shouldReportAPollAsCompletedWhenItSucceeds() {
    // given
    final CountingJobWorkerMetrics metrics = new CountingJobWorkerMetrics();

    try (final JobWorkerImpl ignored = createWorker(metrics)) {
      executor.tick(POLL_INTERVAL.getSeconds(), TimeUnit.SECONDS);

      // when the poll gets an empty response
      poller.done.accept(0);

      // then
      assertThat(metrics.pollsCompleted).hasValue(1);
    }
  }

  @Test
  void shouldReportAPollAsCompletedWhenItFails() {
    // given
    final CountingJobWorkerMetrics metrics = new CountingJobWorkerMetrics();

    try (final JobWorkerImpl ignored = createWorker(metrics)) {
      executor.tick(POLL_INTERVAL.getSeconds(), TimeUnit.SECONDS);

      // when the poll fails
      poller.error.accept(new IllegalStateException("The gateway is gone"));

      // then a failed poll is not mistaken for a stalled one
      assertThat(metrics.pollsCompleted).hasValue(1);
    }
  }

  @Test
  void shouldReportThePollInFlightAsCompletedWhenTheWorkerCloses() {
    // given a worker with a poll that never gets an answer
    final CountingJobWorkerMetrics metrics = new CountingJobWorkerMetrics();
    final JobWorkerImpl worker = createWorker(metrics);
    executor.tick(POLL_INTERVAL.getSeconds(), TimeUnit.SECONDS);

    // when the worker is closed, after which the poll never reports back
    worker.close();

    // then the closed worker is not reported as stalled
    assertThat(metrics.pollsCompleted).hasValue(1);
  }

  @Test
  void shouldKeepPollingWhenTheMetricsThrow() {
    // given metrics that fail on every poll report
    final JobWorkerMetrics metrics =
        new JobWorkerMetrics() {
          @Override
          public void pollStarted() {
            throw new IllegalStateException("The metrics are broken");
          }

          @Override
          public void pollCompleted() {
            throw new IllegalStateException("The metrics are broken");
          }
        };

    try (final JobWorkerImpl ignored = createWorker(metrics)) {
      executor.tick(POLL_INTERVAL.getSeconds(), TimeUnit.SECONDS);
      assertThat(poller.polls).hasValue(1);

      // when the poll gets its answer
      poller.done.accept(0);

      // then the worker still releases its poller and polls again
      executor.tick(POLL_INTERVAL.getSeconds(), TimeUnit.SECONDS);
      assertThat(poller.polls).hasValue(2);
    }
  }

  @Test
  void shouldKeepAgeingAPollWithoutResponseThroughMicrometer() {
    // given a worker on Micrometer metrics whose first poll is never answered
    final MockClock clock = new MockClock();
    final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    final Tags tags = Tags.of("type", "payment");
    final JobWorkerMetrics metrics =
        JobWorkerMetrics.micrometer().withMeterRegistry(meterRegistry).withTags(tags).build();
    final LongTaskTimer pollInFlight =
        meterRegistry.more().longTaskTimer(Names.POLL_IN_FLIGHT.asString(), tags);

    try (final JobWorkerImpl ignored = createWorker(metrics)) {
      executor.tick(POLL_INTERVAL.getSeconds(), TimeUnit.SECONDS);

      // when time passes without an answer
      clock.add(Duration.ofMinutes(10));

      // then the age of the poll in flight grows, which is the signal to alert on
      assertThat(pollInFlight.activeTasks()).isEqualTo(1);
      assertThat(pollInFlight.max(TimeUnit.MINUTES)).isGreaterThanOrEqualTo(10);

      // and it goes back to zero once the poll is answered
      poller.done.accept(0);
      assertThat(pollInFlight.activeTasks()).isZero();
    }
  }

  private JobWorkerImpl createWorker(final JobWorkerMetrics metrics) {
    return new JobWorkerImpl(
        32,
        executor,
        POLL_INTERVAL,
        Mockito.mock(JobClient.class),
        (job, doneCallback) -> doneCallback,
        poller,
        JobStreamer.noop(),
        delay -> delay,
        delay -> delay,
        metrics,
        executor::execute,
        () -> 0L,
        Duration.ofSeconds(30));
  }

  private static final class CountingJobWorkerMetrics implements JobWorkerMetrics {
    private final AtomicInteger pollsStarted = new AtomicInteger();
    private final AtomicInteger pollsCompleted = new AtomicInteger();

    @Override
    public void pollStarted() {
      pollsStarted.incrementAndGet();
    }

    @Override
    public void pollCompleted() {
      pollsCompleted.incrementAndGet();
    }
  }

  /** A poller that never answers by itself, so a test decides how and when a poll ends. */
  private static final class CapturingJobPoller implements JobPoller {
    private final AtomicInteger polls = new AtomicInteger();
    private volatile IntConsumer done;
    private volatile Consumer<Throwable> error;

    @Override
    public void poll(
        final int maxJobsToActivate,
        final Consumer<ActivatedJob> jobConsumer,
        final IntConsumer doneCallback,
        final Consumer<Throwable> errorCallback,
        final BooleanSupplier openSupplier) {
      polls.incrementAndGet();
      done = doneCallback;
      error = errorCallback;
    }
  }
}
