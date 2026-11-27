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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.client.api.worker.metrics.MicrometerJobWorkerMetricsBuilder.Names;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class MicrometerJobWorkerPollMetricsTest {
  private final MockClock clock = new MockClock();
  private final SimpleMeterRegistry meterRegistry =
      new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
  private final Iterable<Tag> tags = Tags.of("type", "payment");

  @Test
  void shouldIgnoreACompletionWithoutAStart() {
    // given a worker whose poll failed before it was sent
    final JobWorkerMetrics metrics = newMetrics();

    // when
    metrics.pollCompleted();

    // then
    assertThat(pollInFlight().activeTasks()).isZero();
  }

  @Test
  void shouldNotLeakThePreviousPollWhenANewOneStarts() {
    // given a poll that was never completed
    final JobWorkerMetrics metrics = newMetrics();
    metrics.pollStarted();
    clock.add(Duration.ofMinutes(10));

    // when the worker starts its next poll
    metrics.pollStarted();

    // then only the new poll is in flight
    assertThat(pollInFlight().activeTasks()).isEqualTo(1);
    assertThat(pollInFlight().max(TimeUnit.SECONDS)).isZero();
  }

  @Test
  void shouldReportTheOldestPollOfWorkersSharingTheSameTags() {
    // given two workers of the same job type, which share one timer
    final JobWorkerMetrics stalled = newMetrics();
    final JobWorkerMetrics healthy = newMetrics();
    stalled.pollStarted();
    clock.add(Duration.ofMinutes(10));
    healthy.pollStarted();

    // when the healthy worker completes its poll
    healthy.pollCompleted();

    // then the stalled worker is still visible, not hidden by the healthy one
    assertThat(pollInFlight().activeTasks()).isEqualTo(1);
    assertThat(pollInFlight().max(TimeUnit.MINUTES)).isGreaterThanOrEqualTo(10);
  }

  @Test
  void shouldReleaseTheStalledPollOnlyThroughItsOwnWorker() {
    // given two workers of the same job type, both with a poll in flight
    final JobWorkerMetrics first = newMetrics();
    final JobWorkerMetrics second = newMetrics();
    first.pollStarted();
    second.pollStarted();

    // when one of them completes
    first.pollCompleted();

    // then the other one's poll is still in flight
    assertThat(pollInFlight().activeTasks()).isEqualTo(1);
  }

  @Test
  void shouldNotTrackPollsWithoutATimer() {
    // given metrics built the way they were before the poll timer existed
    final MicrometerJobWorkerMetrics metrics =
        new MicrometerJobWorkerMetrics(
            meterRegistry.counter("activated"),
            meterRegistry.counter("handled"),
            meterRegistry.counter("refused"),
            meterRegistry.counter("expired"),
            meterRegistry.counter("recreated"));

    // when
    metrics.pollStarted();
    metrics.pollCompleted();

    // then nothing fails and no poll timer was registered
    assertThat(meterRegistry.find(Names.POLL_IN_FLIGHT.asString()).longTaskTimer()).isNull();
  }

  private JobWorkerMetrics newMetrics() {
    return JobWorkerMetrics.micrometer().withMeterRegistry(meterRegistry).withTags(tags).build();
  }

  private LongTaskTimer pollInFlight() {
    return meterRegistry.more().longTaskTimer(Names.POLL_IN_FLIGHT.asString(), tags);
  }
}
