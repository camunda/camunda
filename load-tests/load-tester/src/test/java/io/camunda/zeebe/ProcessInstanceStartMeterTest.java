/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  public void shouldRejectRecordingWhenMaxObservedInstancesReached() throws InterruptedException {
    // given
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    availabilityChecker =
        (list) -> {
          countDownLatch.countDown();
          return CompletableFuture.completedFuture(List.of());
        };

    processInstanceStartMeter.start();

    // when - record MAX_OBSERVED_INSTANCES process instances
    for (int i = 1; i <= ProcessInstanceStartMeter.MAX_OBSERVED_INSTANCES; i++) {
      final boolean recorded =
          processInstanceStartMeter.recordProcessInstanceStart(
              Protocol.encodePartitionId(1, i), System.nanoTime());
      assertThat(recorded).isTrue();
    }

    // then - attempting to record another instance should be rejected
    final boolean rejected =
        processInstanceStartMeter.recordProcessInstanceStart(
            Protocol.encodePartitionId(1, 101), System.nanoTime());
    assertThat(rejected).isFalse();
  }
}
