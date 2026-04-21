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
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.metrics.ProcessInstanceStartMeter.AvailabilityChecker;
import io.camunda.zeebe.protocol.Protocol;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessInstanceStartMeterTest {

  private ProcessInstanceStartMeter processInstanceStartMeter;
  private SimpleMeterRegistry meterRegistry;
  private AvailabilityChecker availabilityChecker;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    availabilityChecker = CompletableFuture::completedFuture;
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
            System::nanoTime,
            meterRegistry,
            Executors.newScheduledThreadPool(1),
            Duration.ofMillis(1),
            (list) -> availabilityChecker.findAvailableInstances(list));
  }

  @AfterEach
  void tearDown() {
    processInstanceStartMeter.stop();
    meterRegistry.close();
  }

  @Test
  void shouldRecordInstanceAvailability() {
    // given

    // when
    processInstanceStartMeter.start();
    processInstanceStartMeter.recordProcessInstanceStart(
        Protocol.encodePartitionId(1, 1), System.nanoTime());

    // then
    await()
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        meterRegistry
                            .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                            .timer()
                            .count())
                    .isOne());

    assertThat(
            meterRegistry
                .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isNotZero();
  }

  @Test
  void shouldRecordQueryDurationForAvailabilityMeasurement() {
    // given

    // when
    processInstanceStartMeter.start();
    processInstanceStartMeter.recordProcessInstanceStart(
        Protocol.encodePartitionId(1, 1), System.nanoTime());

    // then
    await()
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        meterRegistry
                            .get(
                                StarterLatencyMetricsDoc.DATA_AVAILABILITY_QUERY_DURATION.getName())
                            .timer()
                            .count())
                    .isGreaterThanOrEqualTo(1));

    assertThat(
            meterRegistry
                .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_QUERY_DURATION.getName())
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isNotZero();
  }

  @Test
  void shouldNotRecordInstanceAvailability() {
    // given - no instances are recorded

    // when - the meter starts but has nothing to check
    processInstanceStartMeter.start();

    // then - over a sustained window, the availability metric must never be created
    await()
        .during(Duration.ofMillis(250))
        .atMost(Duration.ofMillis(500))
        .untilAsserted(
            () ->
                assertThatThrownBy(
                        () ->
                            meterRegistry
                                .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                                .timer())
                    .hasMessageContaining(
                        "No meter with name 'starter.data.availability.latency' was found."));
  }

  @Test
  void shouldNotRecordQueryDurationWhenNoInstancesStarted() {
    // given - no instances are recorded

    // when - the meter starts but has nothing to check
    processInstanceStartMeter.start();

    // then - the query-duration timer may be registered by Micrometer but its count
    // must stay at zero over a sustained window because the checker never runs
    await()
        .during(Duration.ofMillis(250))
        .atMost(Duration.ofMillis(500))
        .untilAsserted(
            () ->
                assertThat(
                        meterRegistry
                            .get(
                                StarterLatencyMetricsDoc.DATA_AVAILABILITY_QUERY_DURATION.getName())
                            .timer()
                            .count())
                    .isZero());
  }

  @Test
  void shouldNotRecordInstanceAvailabilityWhenNotAvailable() throws InterruptedException {
    // given
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    availabilityChecker =
        (list) -> {
          countDownLatch.countDown();
          // return empty list to simulate no available instances
          return CompletableFuture.completedFuture(List.of());
        };

    // when
    processInstanceStartMeter.start();
    processInstanceStartMeter.recordProcessInstanceStart(
        Protocol.encodePartitionId(1, 1), System.nanoTime());

    // then
    assertThat(countDownLatch.await(1, TimeUnit.SECONDS))
        .describedAs("availability checker should have been invoked at least once")
        .isTrue();

    assertThatThrownBy(
            () ->
                meterRegistry
                    .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                    .timer())
        .hasMessageContaining("No meter with name 'starter.data.availability.latency' was found.");
  }

  @Test
  void shouldRecordQueryDurationEvenWhenPIsNotAvailable() throws InterruptedException {
    // given
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    availabilityChecker =
        (list) -> {
          countDownLatch.countDown();
          // return empty list to simulate no available instances
          return CompletableFuture.completedFuture(List.of());
        };

    // when
    processInstanceStartMeter.start();
    processInstanceStartMeter.recordProcessInstanceStart(
        Protocol.encodePartitionId(1, 1), System.nanoTime());

    // then
    assertThat(countDownLatch.await(1, TimeUnit.SECONDS))
        .describedAs("availability checker should have been invoked at least once")
        .isTrue();

    await()
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        meterRegistry
                            .get(
                                StarterLatencyMetricsDoc.DATA_AVAILABILITY_QUERY_DURATION.getName())
                            .timer()
                            .count())
                    .isGreaterThan(1));

    assertThat(
            meterRegistry
                .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_QUERY_DURATION.getName())
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isNotZero();
  }

  @Test
  void shouldNotRecordInstanceAvailabilityWhenNotStarted() {
    // given
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
            System::nanoTime,
            meterRegistry,
            Executors.newScheduledThreadPool(1),
            Duration.ZERO,
            CompletableFuture::completedFuture);

    // when
    processInstanceStartMeter.recordProcessInstanceStart(
        Protocol.encodePartitionId(1, 1), System.nanoTime());

    // then
    assertThatThrownBy(
            () ->
                meterRegistry
                    .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                    .timer())
        .hasMessageContaining("No meter with name 'starter.data.availability.latency' was found.");
  }

  @Test
  void shouldNotRecordQueryDurationWhenNotStarted() {
    // given
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
            System::nanoTime,
            meterRegistry,
            Executors.newScheduledThreadPool(1),
            Duration.ZERO,
            CompletableFuture::completedFuture);

    // when
    processInstanceStartMeter.recordProcessInstanceStart(
        Protocol.encodePartitionId(1, 1), System.nanoTime());

    // then
    assertThat(
            meterRegistry
                .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_QUERY_DURATION.getName())
                .timer()
                .count())
        .isZero();
  }

  @Test
  void shouldRecordMetricAfterMaxDuration() {
    // given
    final AtomicLong time = new AtomicLong();
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
            time::get,
            meterRegistry,
            Executors.newScheduledThreadPool(1),
            Duration.ofMillis(1),
            CompletableFuture::completedFuture);
    processInstanceStartMeter.start();
    processInstanceStartMeter.recordProcessInstanceStart(
        Protocol.encodePartitionId(1, 1), System.nanoTime());

    // when
    time.set(System.nanoTime() + Duration.ofSeconds(90).toNanos());

    // then
    await()
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        meterRegistry
                            .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                            .timer()
                            .count())
                    .isOne());
    assertThat(
            meterRegistry
                .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isGreaterThan(Duration.ofSeconds(90).toMillis());
  }

  @Test
  void shouldOnlyRecordAvailableInstances() throws InterruptedException {
    // given
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    availabilityChecker =
        (list) -> {
          if (countDownLatch.getCount() == 1) {
            countDownLatch.countDown();
            return CompletableFuture.completedFuture(List.of(list.getFirst()));
          }
          return CompletableFuture.completedFuture(List.of());
        };

    // when
    processInstanceStartMeter.recordProcessInstanceStart(
        Protocol.encodePartitionId(1, 1), System.nanoTime());
    processInstanceStartMeter.recordProcessInstanceStart(
        Protocol.encodePartitionId(1, 2), System.nanoTime());
    processInstanceStartMeter.recordProcessInstanceStart(
        Protocol.encodePartitionId(1, 3), System.nanoTime());
    processInstanceStartMeter.start();

    // then
    assertThat(countDownLatch.await(1, TimeUnit.SECONDS))
        .describedAs("availability checker should have been invoked at least once")
        .isTrue();
    await()
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        meterRegistry
                            .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                            .timer()
                            .count())
                    .isOne());

    assertThat(
            meterRegistry
                .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isGreaterThan(1);
  }
}
