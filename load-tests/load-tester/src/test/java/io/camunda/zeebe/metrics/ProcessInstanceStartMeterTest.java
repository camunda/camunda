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
import java.util.concurrent.atomic.AtomicInteger;
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

    // instances are recorded oldest first; recent start times keep them from being cleaned up
    final long now = System.nanoTime();
    for (int i = 0; i < pendingCount; i++) {
      processInstanceStartMeter.recordProcessInstanceStart(
          Protocol.encodePartitionId(1, i + 1), now + i);
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
  void shouldNotLetLaggingPartitionCrowdOutOtherPartitions() {
    // given - a lagging partition 2 whose pending instances were queued first and exceed a batch,
    // followed by a few instances of partition 1
    final int batchSize = ProcessInstanceStartMeter.AVAILABILITY_CHECK_BATCH_SIZE;
    final int healthyCount = 100;
    final AtomicReference<List<Long>> queriedKeys = new AtomicReference<>();
    availabilityChecker =
        (list) -> {
          queriedKeys.compareAndSet(null, List.copyOf(list));
          return CompletableFuture.completedFuture(List.of());
        };

    final long now = System.nanoTime();
    for (int i = 0; i < 2 * batchSize; i++) {
      processInstanceStartMeter.recordProcessInstanceStart(
          Protocol.encodePartitionId(2, i + 1), now + i);
    }
    for (int i = 0; i < healthyCount; i++) {
      processInstanceStartMeter.recordProcessInstanceStart(
          Protocol.encodePartitionId(1, i + 1), now + 2L * batchSize + i);
    }

    // when
    processInstanceStartMeter.start();

    // then - all healthy instances are queried, the lagging partition fills the rest of the batch
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(queriedKeys.get()).isNotNull());

    final var queriedHealthyKeys =
        queriedKeys.get().stream().filter(key -> Protocol.decodePartitionId(key) == 1).toList();
    final var queriedLaggingKeys =
        queriedKeys.get().stream().filter(key -> Protocol.decodePartitionId(key) == 2).toList();
    assertThat(queriedHealthyKeys).hasSize(healthyCount);
    assertThat(queriedLaggingKeys)
        .containsExactlyElementsOf(
            LongStream.range(0, batchSize - healthyCount)
                .mapToObj(i -> Protocol.encodePartitionId(2, (int) i + 1))
                .toList());
  }

  @Test
  void shouldRunNextCheckImmediatelyWhenResultPageIsFull() throws InterruptedException {
    // given - an interval long enough that waiting it out between checks would be observable
    final Duration interval = Duration.ofSeconds(2);
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
            System::nanoTime,
            meterRegistry,
            Executors.newScheduledThreadPool(1),
            interval,
            (list) -> availabilityChecker.findAvailableInstances(list));

    final int batchSize = ProcessInstanceStartMeter.AVAILABILITY_CHECK_BATCH_SIZE;
    // the first check runs after one interval, so five checks cannot complete within four
    // seconds if each of the following ones waited a full interval as well
    final CountDownLatch invocations = new CountDownLatch(5);
    availabilityChecker =
        (list) -> {
          invocations.countDown();
          // every queried instance is available, so the result page is full
          return CompletableFuture.completedFuture(List.copyOf(list));
        };

    // enough pending instances for five full batches; recent start times keep the instances from
    // being cleaned up as stale between checks
    final long now = System.nanoTime();
    for (int i = 0; i <= 5 * batchSize; i++) {
      processInstanceStartMeter.recordProcessInstanceStart(
          Protocol.encodePartitionId(1, i + 1), now + i);
    }

    // when
    processInstanceStartMeter.start();

    // then - the checks run back-to-back instead of once per interval
    assertThat(invocations.await(4, TimeUnit.SECONDS))
        .describedAs("a full result page must trigger the next check immediately")
        .isTrue();
  }

  @Test
  void shouldWaitForIntervalWhenResultPageIsNotFull() {
    // given - a full batch whose instances are never reported as available, as when the data is
    // not yet searchable
    final Duration interval = Duration.ofSeconds(1);
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
            System::nanoTime,
            meterRegistry,
            Executors.newScheduledThreadPool(1),
            interval,
            (list) -> availabilityChecker.findAvailableInstances(list));

    final AtomicInteger invocations = new AtomicInteger();
    availabilityChecker =
        (list) -> {
          invocations.incrementAndGet();
          return CompletableFuture.completedFuture(List.of());
        };

    final int batchSize = ProcessInstanceStartMeter.AVAILABILITY_CHECK_BATCH_SIZE;
    final long now = System.nanoTime();
    for (int i = 0; i <= batchSize; i++) {
      processInstanceStartMeter.recordProcessInstanceStart(
          Protocol.encodePartitionId(1, i + 1), now + i);
    }

    // when
    processInstanceStartMeter.start();

    // then - after the first check the next one is not retried back-to-back
    await().atMost(Duration.ofSeconds(3)).until(() -> invocations.get() == 1);
    await()
        .during(Duration.ofMillis(300))
        .atMost(Duration.ofSeconds(1))
        .until(() -> invocations.get() == 1);
  }

  @Test
  void shouldCleanUpStaleInstancesRegardlessOfQueueOrder() {
    // given - a newer instance queued before an older one, as when responses arrive out of order
    final var clockNanos = new AtomicLong(TimeUnit.SECONDS.toNanos(1000));
    final AtomicReference<List<Long>> lastQueriedKeys = new AtomicReference<>();
    processInstanceStartMeter =
        new ProcessInstanceStartMeter(
            clockNanos::get,
            meterRegistry,
            Executors.newScheduledThreadPool(1),
            Duration.ofMillis(50),
            (list) -> {
              lastQueriedKeys.set(List.copyOf(list));
              return CompletableFuture.completedFuture(List.of());
            });
    final long freshKey = Protocol.encodePartitionId(1, 1);
    final long staleKey = Protocol.encodePartitionId(1, 2);
    processInstanceStartMeter.recordProcessInstanceStart(freshKey, clockNanos.get());
    processInstanceStartMeter.recordProcessInstanceStart(
        staleKey, clockNanos.get() - TimeUnit.SECONDS.toNanos(120));

    // when
    processInstanceStartMeter.start();

    // then - the stale instance is dropped although it is behind a fresh one, so later checks
    // query only the fresh one
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(lastQueriedKeys.get()).containsExactly(freshKey));
  }
}
