/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;
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
                meterRegistry
                    .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                    .timer()
                    .count(),
            (count) -> assertThat(count).isOne());

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
                meterRegistry
                    .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_QUERY_DURATION.getName())
                    .timer()
                    .count(),
            (count) -> assertThat(count).isGreaterThanOrEqualTo(1));

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
        .atMost(Duration.ofSeconds(2))
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
        .atMost(Duration.ofSeconds(2))
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
                meterRegistry
                    .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_QUERY_DURATION.getName())
                    .timer()
                    .count(),
            (count) -> assertThat(count).isGreaterThan(1));

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
                meterRegistry
                    .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                    .timer()
                    .count(),
            (count) -> assertThat(count).isOne());
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
                meterRegistry
                    .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                    .timer()
                    .count(),
            (count) -> assertThat(count).isOne());

    assertThat(
            meterRegistry
                .get(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isGreaterThan(1);
  }

  @Test
  void shouldQueryAtMostBatchSizeOldestInstances() {
    // given - more pending instances than fit on a single availability-check batch
    final int batchSize = ProcessInstanceStartMeter.AVAILABILITY_CHECK_BATCH_SIZE;
    final int pendingCount = batchSize + 100;
    final AtomicReference<List<Long>> queriedKeys = new AtomicReference<>();
    availabilityChecker =
        (list) -> {
          queriedKeys.compareAndSet(null, List.copyOf(list));
          return CompletableFuture.completedFuture(List.of());
        };

    // the oldest instances get the smallest start time; newer instances get larger start times
    for (int i = 0; i < pendingCount; i++) {
      processInstanceStartMeter.recordProcessInstanceStart(Protocol.encodePartitionId(1, i + 1), i);
    }

    // when
    processInstanceStartMeter.start();

    // then - only the oldest batchSize keys are queried
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(queriedKeys.get()).isNotNull());

    final var expectedOldestKeys =
        LongStream.range(0, batchSize)
            .mapToObj(i -> Protocol.encodePartitionId(1, (int) i + 1))
            .toList();
    assertThat(queriedKeys.get())
        .hasSize(batchSize)
        .containsExactlyInAnyOrderElementsOf(expectedOldestKeys);
  }

  @Test
  void shouldRunNextCheckImmediatelyWhenBatchIsFull() throws InterruptedException {
    // given - an interval long enough that waiting it out between checks would be observable
    final Duration interval = Duration.ofMillis(500);
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
            System::nanoTime,
            meterRegistry,
            Executors.newScheduledThreadPool(1),
            interval,
            (list) -> availabilityChecker.findAvailableInstances(list));

    final int batchSize = ProcessInstanceStartMeter.AVAILABILITY_CHECK_BATCH_SIZE;
    // five checks cannot all complete within one second if each waited a full 500ms interval
    final CountDownLatch invocations = new CountDownLatch(5);
    availabilityChecker =
        (list) -> {
          invocations.countDown();
          return CompletableFuture.completedFuture(List.of());
        };

    // more than one full batch stays pending, so every check sees a full page; recent start times
    // keep the instances from being cleaned up as stale between checks
    final long now = System.nanoTime();
    for (int i = 0; i <= batchSize; i++) {
      processInstanceStartMeter.recordProcessInstanceStart(
          Protocol.encodePartitionId(1, i + 1), now + i);
    }

    // when
    processInstanceStartMeter.start();

    // then - the checks run back-to-back instead of once per interval
    assertThat(invocations.await(1, TimeUnit.SECONDS))
        .describedAs("a full batch must trigger the next check immediately")
        .isTrue();
  }
}
