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
package io.camunda.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.ProcessInstanceStartMeter.AvailabilityChecker;
import io.camunda.zeebe.protocol.Protocol;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessInstanceStartMeterTest {

  private ProcessInstanceStartMeter processInstanceStartMeter;
  private SimpleMeterRegistry meterRegistry;
  private AvailabilityChecker availabilityChecker;

  @BeforeEach
  public void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    availabilityChecker = CompletableFuture::completedFuture;
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
            meterRegistry,
            Executors.newScheduledThreadPool(1),
            Duration.ofMillis(1),
            (list) -> availabilityChecker.findAvailableInstances(list));
  }

  @AfterEach
  public void tearDown() {
    processInstanceStartMeter.stop();
    meterRegistry.close();
  }

  @Test
  public void shouldRecordInstanceAvailability() {
    // given

    // when
    processInstanceStartMeter.start();
    processInstanceStartMeter.recordProcessInstanceStart(
        Protocol.encodePartitionId(1, 1), System.nanoTime());

    // then
    await()
        .ignoreExceptions()
        .until(
            () ->
                meterRegistry
                    .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                    .timer()
                    .count(),
            (count) -> count == 1);

    assertThat(
            meterRegistry
                .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isGreaterThan(1);
  }

  @Test
  public void shouldNotRecordInstanceAvailability() throws InterruptedException {
    // given
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    availabilityChecker =
        (list) -> {
          countDownLatch.countDown();
          return CompletableFuture.completedFuture(list);
        };

    // when
    processInstanceStartMeter.start();

    // then
    countDownLatch.await(1, TimeUnit.SECONDS);

    assertThatThrownBy(
            () ->
                meterRegistry
                    .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                    .timer())
        .hasMessageContaining("No meter with name 'starter.data.availability.latency' was found.");
  }

  @Test
  public void shouldNotRecordInstanceAvailabilityWhenNotAvailable() throws InterruptedException {
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
    countDownLatch.await(1, TimeUnit.SECONDS);

    assertThatThrownBy(
            () ->
                meterRegistry
                    .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                    .timer())
        .hasMessageContaining("No meter with name 'starter.data.availability.latency' was found.");
  }

  @Test
  public void shouldNotRecordInstanceAvailabilityWhenNotStarted() {
    // given
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
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
  public void shouldOnlyRecordAvailableInstances() throws InterruptedException {
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
    countDownLatch.await(1, TimeUnit.SECONDS);
    await()
        .ignoreExceptions()
        .until(
            () ->
                meterRegistry
                    .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                    .timer()
                    .count(),
            (count) -> count == 1);

    assertThat(
            meterRegistry
                .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isGreaterThan(1);
  }
}
