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
  private static final String SUSPENDED_INSTANCES_METRIC = "zeebe.suspended.instances.count";
  private static final String BUFFERED_COMMANDS_METRIC = "zeebe.buffered.commands.count";
  private static final String RESUME_DURATION_METRIC = "zeebe.process.instance.resume.duration";

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
  void shouldTrackSuspendedInstancesGauge() {
    // when
    metrics.instanceSuspended();
    metrics.instanceSuspended();
    metrics.instanceSuspended();
    metrics.instanceResumed();

    // then
    assertThat(suspendedInstancesGauge()).isEqualTo(2.0);
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
  void shouldTrackBufferedCommandsGaugeAndCounters() {
    // given
    for (int i = 0; i < 5; i++) {
      metrics.commandBuffered();
    }

    // when
    metrics.commandDrained();
    metrics.commandDrained();
    metrics.commandsDropped(1);

    // then
    assertThat(bufferedCommandsGauge()).isEqualTo(2.0);
    assertThat(bufferedCommandEventCount("buffered")).isEqualTo(5.0);
    assertThat(bufferedCommandEventCount("drained")).isEqualTo(2.0);
    assertThat(bufferedCommandEventCount("dropped")).isEqualTo(1.0);
  }

  @Test
  void shouldCountDroppedCommandsByAmount() {
    // given - buffer enough commands first so the gauge does not go negative
    for (int i = 0; i < 15; i++) {
      metrics.commandBuffered();
    }

    // when
    metrics.commandsDropped(10);

    // then
    assertThat(bufferedCommandEventCount("dropped")).isEqualTo(10.0);
    assertThat(bufferedCommandsGauge()).isEqualTo(5.0);
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
  void shouldDecrementGaugeOnTerminationWhileSuspended() {
    // given
    metrics.instanceSuspended();
    metrics.instanceSuspended();
    metrics.instanceSuspended();

    // when — one instance terminates without going through resume
    metrics.instanceTerminatedWhileSuspended();

    // then — gauge decremented but no "resumed" counter increment
    assertThat(suspendedInstancesGauge()).isEqualTo(2.0);
    assertThat(suspensionEventCount("resumed")).isZero();
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
  void shouldSetGaugesAbsoluteOnRecovery() {
    // when
    metrics.setSuspendedInstances(42);

    // then
    assertThat(suspendedInstancesGauge()).isEqualTo(42.0);

    // when
    metrics.setBufferedCommands(100);

    // then
    assertThat(bufferedCommandsGauge()).isEqualTo(100.0);
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

  private double suspendedInstancesGauge() {
    return registry.get(SUSPENDED_INSTANCES_METRIC).gauge().value();
  }

  private double bufferedCommandsGauge() {
    return registry.get(BUFFERED_COMMANDS_METRIC).gauge().value();
  }
}
