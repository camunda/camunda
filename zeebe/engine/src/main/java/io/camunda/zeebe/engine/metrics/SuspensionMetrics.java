/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.metrics.SuspensionMetricsDoc.BufferedCommandAction;
import io.camunda.zeebe.engine.metrics.SuspensionMetricsDoc.SuspensionAction;
import io.camunda.zeebe.engine.metrics.SuspensionMetricsDoc.SuspensionKeyNames;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public final class SuspensionMetrics implements StreamProcessorLifecycleAware {
  /**
   * Upper bound of in-flight suspend and resume measurements each. When full, new measurements are
   * dropped, so the running ones still complete and get recorded; losing some data points is fine.
   */
  static final int MAX_IN_FLIGHT_DURATIONS = 1000;

  private final MeterRegistry registry;

  private final Counter suspendedCounter;
  private final Counter resumedCounter;
  private final Counter jobSuspendedCounter;
  private final Counter jobResumedCounter;
  private final Counter userTaskSuspendedCounter;
  private final Counter userTaskResumedCounter;
  private final Counter commandBufferedCounter;
  private final Counter commandDrainedCounter;
  private final Counter commandDroppedCounter;

  /**
   * In-flight resume timers keyed by process instance key. All calls happen on the stream processor
   * actor thread, so a plain {@link HashMap} is safe here; see {@link MessageCorrelationMetrics}
   * for the same reasoning applied to its per-key samples.
   */
  private final HashMap<Long, Timer.ResourceSample> resumeDurationSamples = new HashMap<>();

  private final Timer suspendDurationTimer;

  /**
   * Start times of in-flight suspensions keyed by process instance key; same threading as above.
   */
  private final HashMap<Long, Long> suspendStartTimes = new HashMap<>();

  public SuspensionMetrics(final MeterRegistry meterRegistry) {
    registry = meterRegistry;

    suspendedCounter =
        registerCounter(
            meterRegistry, SuspensionMetricsDoc.SUSPENSION_EVENTS, SuspensionAction.SUSPENDED);
    resumedCounter =
        registerCounter(
            meterRegistry, SuspensionMetricsDoc.SUSPENSION_EVENTS, SuspensionAction.RESUMED);
    jobSuspendedCounter =
        registerCounter(
            meterRegistry, SuspensionMetricsDoc.JOB_SUSPENSION_EVENTS, SuspensionAction.SUSPENDED);
    jobResumedCounter =
        registerCounter(
            meterRegistry, SuspensionMetricsDoc.JOB_SUSPENSION_EVENTS, SuspensionAction.RESUMED);
    userTaskSuspendedCounter =
        registerCounter(
            meterRegistry,
            SuspensionMetricsDoc.USER_TASK_SUSPENSION_EVENTS,
            SuspensionAction.SUSPENDED);
    userTaskResumedCounter =
        registerCounter(
            meterRegistry,
            SuspensionMetricsDoc.USER_TASK_SUSPENSION_EVENTS,
            SuspensionAction.RESUMED);
    commandBufferedCounter =
        registerCounter(
            meterRegistry,
            SuspensionMetricsDoc.BUFFERED_COMMAND_EVENTS,
            BufferedCommandAction.BUFFERED);
    commandDrainedCounter =
        registerCounter(
            meterRegistry,
            SuspensionMetricsDoc.BUFFERED_COMMAND_EVENTS,
            BufferedCommandAction.DRAINED);
    commandDroppedCounter =
        registerCounter(
            meterRegistry,
            SuspensionMetricsDoc.BUFFERED_COMMAND_EVENTS,
            BufferedCommandAction.DROPPED);

    suspendDurationTimer =
        MicrometerUtil.buildTimer(SuspensionMetricsDoc.SUSPEND_DURATION)
            .minimumExpectedValue(Duration.ofMillis(10))
            .register(meterRegistry);
    MicrometerUtil.buildTimer(SuspensionMetricsDoc.RESUME_DURATION)
        .minimumExpectedValue(Duration.ofMillis(10))
        .register(meterRegistry);
  }

  public void instanceSuspended() {
    suspendedCounter.increment();
  }

  public void instanceResumed() {
    resumedCounter.increment();
  }

  public void jobSuspended() {
    jobSuspendedCounter.increment();
  }

  public void jobsSuspended(final int count) {
    if (count > 0) {
      jobSuspendedCounter.increment(count);
    }
  }

  public void jobResumed() {
    jobResumedCounter.increment();
  }

  public void userTaskSuspended() {
    userTaskSuspendedCounter.increment();
  }

  public void userTaskResumed() {
    userTaskResumedCounter.increment();
  }

  public void commandBuffered() {
    commandBufferedCounter.increment();
  }

  public void commandDrained() {
    commandDrainedCounter.increment();
  }

  public void commandsDropped(final int count) {
    commandDroppedCounter.increment(count);
  }

  /**
   * Returns the current monotonic time, to pass to {@link #startSuspendDuration} once the SUSPEND
   * command was processed. Taking it before processing includes the command's own work in the
   * duration, while a command that is rejected or fails never starts a measurement.
   */
  public long takeSuspendStartTime() {
    return now();
  }

  /**
   * Starts measuring the suspension of the given process instance from {@code startTime}. An
   * already running measurement is kept, so a restarted suspension is timed from its first start.
   */
  public void startSuspendDuration(final long processInstanceKey, final long startTime) {
    if (suspendStartTimes.size() >= MAX_IN_FLIGHT_DURATIONS) {
      return;
    }
    suspendStartTimes.putIfAbsent(processInstanceKey, startTime);
  }

  public void stopSuspendDuration(final long processInstanceKey) {
    final Long startTime = suspendStartTimes.remove(processInstanceKey);
    if (startTime != null) {
      suspendDurationTimer.record(now() - startTime, TimeUnit.NANOSECONDS);
    }
  }

  public void startResumeDuration(final long processInstanceKey) {
    if (resumeDurationSamples.size() >= MAX_IN_FLIGHT_DURATIONS) {
      return;
    }
    resumeDurationSamples.computeIfAbsent(
        processInstanceKey,
        key -> Timer.resource(registry, SuspensionMetricsDoc.RESUME_DURATION.getName()));
  }

  public void stopResumeDuration(final long processInstanceKey) {
    final var sample = resumeDurationSamples.remove(processInstanceKey);
    if (sample != null) {
      sample.close();
    }
  }

  /**
   * Discards the in-flight suspend and resume measurements for the given key without recording
   * them. Called when an instance completes or terminates before its suspension or resume finishes,
   * so the entries do not leak.
   */
  public void discardDurations(final long processInstanceKey) {
    suspendStartTimes.remove(processInstanceKey);
    resumeDurationSamples.remove(processInstanceKey);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    resumeDurationSamples.clear();
    suspendStartTimes.clear();
  }

  private long now() {
    return registry.config().clock().monotonicTime();
  }

  private static Counter registerCounter(
      final MeterRegistry meterRegistry,
      final ExtendedMeterDocumentation meterDoc,
      final Enum<?> action) {
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(SuspensionKeyNames.ACTION.asString(), action.toString())
        .register(meterRegistry);
  }
}
