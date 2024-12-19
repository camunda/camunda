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

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.response.ActivatedJobImpl;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class JobWorkerMetricsTest {

  private static final int AUTO_COMPLETE_ALL_JOBS = 0;
  private final DeterministicScheduler executor = new DeterministicScheduler();

  private JobWorkerImpl createWorker(
      final int autoCompleteCount,
      final TestJobStreamer streamer,
      final TestJobWorkerMetrics metrics) {
    return createWorker(autoCompleteCount, createNoopJobPoller(), streamer, metrics);
  }

  private JobWorkerImpl createWorker(
      final int autoCompleteCount, final TestJobPoller poller, final TestJobWorkerMetrics metrics) {
    return createWorker(autoCompleteCount, poller, JobStreamer.noop(), metrics);
  }

  private JobWorkerImpl createWorker(
      final int autoCompleteCount,
      final JobPoller poller,
      final JobStreamer streamer,
      final JobWorkerMetrics metrics) {
    return new JobWorkerImpl(
        32,
        executor,
        Duration.ofSeconds(30),
        new TestJobRunnableFactory(autoCompleteCount),
        poller,
        streamer,
        delay -> delay,
        metrics,
        executor);
  }

  private JobPoller createNoopJobPoller() {
    return (maxJobsToActivate, jobConsumer, doneCallback, errorCallback, openSupplier) -> {};
  }

  private static final class TestJobWorkerMetrics implements JobWorkerMetrics {
    private final AtomicInteger jobsActivated = new AtomicInteger();
    private final AtomicInteger jobsHandled = new AtomicInteger();

    @Override
    public void jobActivated(final int count) {
      jobsActivated.addAndGet(count);
    }

    @Override
    public void jobHandled(final int count) {
      jobsHandled.addAndGet(count);
    }
  }

  private static final class TestJobPoller implements JobPoller {
    private final AtomicReference<Consumer<ActivatedJob>> consumerRef = new AtomicReference<>();
    private final JsonMapper mapper = new CamundaObjectMapper();

    @Override
    public void poll(
        final int maxJobsToActivate,
        final Consumer<ActivatedJob> jobConsumer,
        final IntConsumer doneCallback,
        final Consumer<Throwable> errorCallback,
        final BooleanSupplier openSupplier) {
      consumerRef.set(jobConsumer);
    }

    private void produceJob() {
      final ActivatedJobImpl job = new ActivatedJobImpl(mapper, TestData.job());
      Optional.ofNullable(consumerRef.get()).ifPresent(c -> c.accept(job));
    }
  }

  private static final class TestJobRunnableFactory implements JobRunnableFactory {

    private final AtomicInteger counter = new AtomicInteger();
    private final int autoCompleteCount;

    private TestJobRunnableFactory(final int autoCompleteCount) {
      this.autoCompleteCount = autoCompleteCount;
    }

    @Override
    public Runnable create(final ActivatedJob job, final Runnable doneCallback) {
      if (autoCompleteCount <= AUTO_COMPLETE_ALL_JOBS || counter.get() < autoCompleteCount) {
        counter.incrementAndGet();
        return doneCallback;
      }

      return () -> {};
    }
  }

  private static final class TestJobStreamer implements JobStreamer {
    private final AtomicReference<Consumer<ActivatedJob>> consumerRef = new AtomicReference<>();
    private final JsonMapper mapper = new CamundaObjectMapper();

    @Override
    public void close() {
      consumerRef.set(null);
    }

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public void openStreamer(final Consumer<ActivatedJob> jobConsumer) {
      consumerRef.set(jobConsumer);
    }

    private void streamJob() {
      final ActivatedJobImpl job = new ActivatedJobImpl(mapper, TestData.job());
      Optional.ofNullable(consumerRef.get()).ifPresent(c -> c.accept(job));
    }
  }

  @Nested
  final class StreamingTest {
    @Test
    void shouldCountActivatedJobs() {
      // given
      final TestJobStreamer streamer = new TestJobStreamer();
      final TestJobWorkerMetrics metrics = new TestJobWorkerMetrics();

      try (final JobWorkerImpl ignored = createWorker(AUTO_COMPLETE_ALL_JOBS, streamer, metrics)) {
        // when
        streamer.streamJob();
        streamer.streamJob();

        // then
        executor.runUntilIdle();
        assertThat(metrics.jobsActivated).hasValue(2);
      }
    }

    @Test
    void shouldCountHandledJobs() {
      // given
      final TestJobStreamer streamer = new TestJobStreamer();
      final TestJobWorkerMetrics metrics = new TestJobWorkerMetrics();

      try (final JobWorkerImpl ignored = createWorker(2, streamer, metrics)) {
        // when
        streamer.streamJob();
        streamer.streamJob();
        streamer.streamJob();

        // then
        executor.runUntilIdle();
        assertThat(metrics.jobsHandled).hasValue(2);
      }
    }
  }

  @Nested
  final class PollingTest {
    @Test
    void shouldCountActivatedJobs() {
      // given
      final TestJobPoller poller = new TestJobPoller();
      final TestJobWorkerMetrics metrics = new TestJobWorkerMetrics();

      try (final JobWorkerImpl ignored = createWorker(AUTO_COMPLETE_ALL_JOBS, poller, metrics)) {
        // when
        executor.tick(1, TimeUnit.MINUTES);
        poller.produceJob();
        poller.produceJob();

        // then
        executor.runUntilIdle();
        assertThat(metrics.jobsActivated).hasValue(2);
      }
    }

    @Test
    void shouldCountHandledJobs() {
      // given
      final TestJobPoller poller = new TestJobPoller();
      final TestJobWorkerMetrics metrics = new TestJobWorkerMetrics();

      try (final JobWorkerImpl ignored = createWorker(3, poller, metrics)) {
        // when
        executor.tick(1, TimeUnit.MINUTES);
        poller.produceJob();
        poller.produceJob();
        poller.produceJob();
        poller.produceJob();

        // then
        executor.runUntilIdle();
        assertThat(metrics.jobsHandled).hasValue(3);
      }
    }
  }
}
