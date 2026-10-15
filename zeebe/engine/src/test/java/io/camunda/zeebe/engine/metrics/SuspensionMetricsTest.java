/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Recorder-level unit tests: verifies that every recording method on {@link SuspensionMetrics}
 * registers the documented meter under the expected name and tags and increments/records it.
 */
final class SuspensionMetricsTest {

  private static final String SUSPENSION_EVENTS_METRIC =
      "zeebe.process.instance.suspension.events.total";
  private static final String JOB_SUSPENSION_EVENTS_METRIC = "zeebe.job.suspension.events.total";
  private static final String BUFFERED_COMMAND_EVENTS_METRIC =
      "zeebe.buffered.commands.events.total";
  private static final String RESUME_DURATION_METRIC = "zeebe.process.instance.resume.duration";
  private static final String SUSPEND_DURATION_METRIC = "zeebe.process.instance.suspend.duration";
  private static final String USER_TASK_SUSPENSION_EVENTS_METRIC =
      "zeebe.user.task.suspension.events.total";

  private final AtomicLong monotonicNanos = new AtomicLong();
  private SimpleMeterRegistry registry;
  private SuspensionMetrics metrics;

  @BeforeEach
  void setUp() {
    final Clock clock =
        new Clock() {
          @Override
          public long wallTime() {
            return System.currentTimeMillis();
          }

          @Override
          public long monotonicTime() {
            return monotonicNanos.get();
          }
        };
    registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    metrics = new SuspensionMetrics(registry);
  }

  @Test
  void shouldIncrementSuspensionEventCounterOnSuspend() {
    // when
    metrics.instanceSuspended();
    metrics.instanceSuspended();

    // then
    assertThat(suspensionEventCount("suspended")).isEqualTo(2.0);
  }

  @Test
  void shouldIncrementSuspensionEventCounterOnResume() {
    // when
    metrics.instanceResumed();

    // then
    assertThat(suspensionEventCount("resumed")).isEqualTo(1.0);
  }

  @Test
  void shouldIncrementJobSuspensionCounters() {
    // when
    metrics.jobSuspended();
    metrics.jobSuspended();
    metrics.jobResumed();

    // then
    assertThat(jobSuspensionEventCount("suspended")).isEqualTo(2.0);
    assertThat(jobSuspensionEventCount("resumed")).isEqualTo(1.0);
  }

  @Test
  void shouldTrackBufferedCommandCounters() {
    // given
    for (int i = 0; i < 5; i++) {
      metrics.commandBuffered();
    }

    // when
    metrics.commandDrained();
    metrics.commandDrained();
    metrics.commandsDropped(1);

    // then
    assertThat(bufferedCommandEventCount("buffered")).isEqualTo(5.0);
    assertThat(bufferedCommandEventCount("drained")).isEqualTo(2.0);
    assertThat(bufferedCommandEventCount("dropped")).isEqualTo(1.0);
  }

  @Test
  void shouldCountDroppedCommandsByAmount() {
    // when
    metrics.commandsDropped(10);

    // then
    assertThat(bufferedCommandEventCount("dropped")).isEqualTo(10.0);
  }

  @Test
  void shouldRecordResumeDuration() {
    // given
    metrics.startResumeDuration(1234L);

    // when
    advanceClock(Duration.ofMillis(50));
    metrics.stopResumeDuration(1234L);

    // then
    final var timer = registry.find(RESUME_DURATION_METRIC).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(50.0);
  }

  @Test
  void shouldRecordSuspendDuration() {
    // given
    metrics.startSuspendDuration(1234L, metrics.suspendStartTime());

    // when
    advanceClock(Duration.ofMillis(50));
    metrics.stopSuspendDuration(1234L);

    // then
    final var timer = registry.find(SUSPEND_DURATION_METRIC).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(50.0);
  }

  @Test
  void shouldMeasureSuspendDurationFromStartTimeTakenBeforeProcessing() {
    // given — start time taken before the SUSPEND command's own work
    final long startTime = metrics.suspendStartTime();
    advanceClock(Duration.ofMillis(30));
    metrics.startSuspendDuration(1234L, startTime);

    // when
    advanceClock(Duration.ofMillis(20));
    metrics.stopSuspendDuration(1234L);

    // then
    final var timer = registry.find(SUSPEND_DURATION_METRIC).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(50.0);
  }

  @Test
  void shouldNotMeasureFromRejectedSuspendAttempt() {
    // given — a rejected SUSPEND takes a start time but never starts the measurement
    metrics.suspendStartTime();
    advanceClock(Duration.ofMillis(100));

    // when — a later SUSPEND succeeds
    metrics.startSuspendDuration(1234L, metrics.suspendStartTime());
    advanceClock(Duration.ofMillis(50));
    metrics.stopSuspendDuration(1234L);

    // then — only the successful attempt is measured
    final var timer = registry.find(SUSPEND_DURATION_METRIC).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(50.0);
  }

  @Test
  void shouldIgnoreDuplicateSuspendStartForSameKey() {
    // given
    metrics.startSuspendDuration(1234L, metrics.suspendStartTime());
    advanceClock(Duration.ofMillis(50));

    // when — a restarted suspension keeps the original start
    metrics.startSuspendDuration(1234L, metrics.suspendStartTime());
    metrics.stopSuspendDuration(1234L);

    // then
    final var timer = registry.find(SUSPEND_DURATION_METRIC).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(50.0);
  }

  @Test
  void shouldNotFailWhenStoppingNonExistentSuspendTimer() {
    // when / then - no start was ever called for this key
    assertThatCode(() -> metrics.stopSuspendDuration(9999L)).doesNotThrowAnyException();
  }

  @Test
  void shouldClearSuspendTimerSamplesOnRecovery() {
    // given
    metrics.startSuspendDuration(1234L, metrics.suspendStartTime());

    // when - recovery clears the in-flight sample, so the later stop has nothing to record
    metrics.onRecovered(null);
    metrics.stopSuspendDuration(1234L);

    // then
    final var timer = registry.find(SUSPEND_DURATION_METRIC).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isZero();
  }

  @Test
  void shouldDiscardSuspendDurationSampleOnCancel() {
    // given
    metrics.startSuspendDuration(42L, metrics.suspendStartTime());
    advanceClock(Duration.ofMillis(100));

    // when — instance terminates mid-suspension; sample discarded without recording
    metrics.cancelSuspendDuration(42L);
    metrics.stopSuspendDuration(42L);

    // then
    final var timer = registry.find(SUSPEND_DURATION_METRIC).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isZero();
  }

  @Test
  void shouldIgnoreDuplicateStartForSameKey() {
    // given
    metrics.startResumeDuration(1234L);
    advanceClock(Duration.ofMillis(50));

    // when — second start for the same key is ignored (computeIfAbsent guard)
    metrics.startResumeDuration(1234L);
    metrics.stopResumeDuration(1234L);

    // then — the timer recorded the original sample's duration, not a near-zero one
    final var timer = registry.find(RESUME_DURATION_METRIC).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(50.0);
  }

  @Test
  void shouldTrackMultipleKeysIndependently() {
    // given
    metrics.startResumeDuration(1L);
    advanceClock(Duration.ofMillis(100));
    metrics.startResumeDuration(2L);

    // when
    advanceClock(Duration.ofMillis(50));
    metrics.stopResumeDuration(1L);
    metrics.stopResumeDuration(2L);

    // then
    final var timer = registry.find(RESUME_DURATION_METRIC).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(2L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(200.0);
  }

  @Test
  void shouldNotFailWhenStoppingNonExistentTimer() {
    // when / then - no start was ever called for this key
    assertThatCode(() -> metrics.stopResumeDuration(9999L)).doesNotThrowAnyException();
  }

  @Test
  void shouldClearTimerSamplesOnRecovery() {
    // given
    metrics.startResumeDuration(1234L);

    // when - recovery clears the in-flight sample, so the later stop has nothing to record
    metrics.onRecovered(null);
    metrics.stopResumeDuration(1234L);

    // then
    final var timer = registry.find(RESUME_DURATION_METRIC).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isZero();
  }

  @Test
  void shouldDiscardResumeDurationSampleOnCancel() {
    // given
    metrics.startResumeDuration(42L);
    advanceClock(Duration.ofMillis(100));

    // when — instance terminates mid-resume; sample discarded without recording
    metrics.cancelResumeDuration(42L);

    // then — timer exists (eager registration) but has no recordings
    final var timer = registry.find(RESUME_DURATION_METRIC).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isZero();
  }

  @Test
  void shouldIncrementJobSuspendedCounterByBatch() {
    // when
    metrics.jobsSuspended(7);

    // then
    assertThat(jobSuspensionEventCount("suspended")).isEqualTo(7.0);
  }

  @Test
  void shouldIncrementUserTaskSuspensionCounters() {
    // when
    metrics.userTaskSuspended();
    metrics.userTaskSuspended();
    metrics.userTaskResumed();

    // then
    assertThat(taggedCounter(USER_TASK_SUSPENSION_EVENTS_METRIC, "suspended")).isEqualTo(2.0);
    assertThat(taggedCounter(USER_TASK_SUSPENSION_EVENTS_METRIC, "resumed")).isEqualTo(1.0);
  }

  private void advanceClock(final Duration duration) {
    monotonicNanos.addAndGet(duration.toNanos());
  }

  private double suspensionEventCount(final String action) {
    return taggedCounter(SUSPENSION_EVENTS_METRIC, action);
  }

  private double jobSuspensionEventCount(final String action) {
    return taggedCounter(JOB_SUSPENSION_EVENTS_METRIC, action);
  }

  private double bufferedCommandEventCount(final String action) {
    return taggedCounter(BUFFERED_COMMAND_EVENTS_METRIC, action);
  }

  private double taggedCounter(final String name, final String action) {
    final var counter = registry.get(name).tag("action", action).counter();
    return counter.count();
  }
}
