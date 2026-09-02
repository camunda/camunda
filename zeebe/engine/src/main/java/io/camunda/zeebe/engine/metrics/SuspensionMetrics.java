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
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.HashMap;
import org.jspecify.annotations.Nullable;

public final class SuspensionMetrics implements StreamProcessorLifecycleAware {
  private final MeterRegistry registry;
  @Nullable private final SuspensionState suspensionState;

  private final Counter suspendedCounter;
  private final Counter resumedCounter;
  private final Counter jobSuspendedCounter;
  private final Counter jobResumedCounter;
  private final Counter commandBufferedCounter;
  private final Counter commandDrainedCounter;
  private final Counter commandDroppedCounter;

  private final StatefulGauge suspendedInstances;
  private final StatefulGauge bufferedCommands;

  /**
   * In-flight resume timers keyed by process instance key. All calls happen on the stream processor
   * actor thread, so a plain {@link HashMap} is safe here; see {@link MessageCorrelationMetrics}
   * for the same reasoning applied to its per-key samples.
   */
  private final HashMap<Long, Timer.ResourceSample> resumeDurationSamples = new HashMap<>();

  SuspensionMetrics(final MeterRegistry meterRegistry) {
    this(meterRegistry, null);
  }

  public SuspensionMetrics(
      final MeterRegistry meterRegistry, @Nullable final SuspensionState suspensionState) {
    registry = meterRegistry;
    this.suspensionState = suspensionState;

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

    suspendedInstances =
        StatefulGauge.builder(SuspensionMetricsDoc.SUSPENDED_INSTANCES.getName())
            .description(SuspensionMetricsDoc.SUSPENDED_INSTANCES.getDescription())
            .register(meterRegistry);
    bufferedCommands =
        StatefulGauge.builder(SuspensionMetricsDoc.BUFFERED_COMMANDS.getName())
            .description(SuspensionMetricsDoc.BUFFERED_COMMANDS.getDescription())
            .register(meterRegistry);

    final var resumeDurationDoc = SuspensionMetricsDoc.RESUME_DURATION;
    Timer.builder(resumeDurationDoc.getName())
        .description(resumeDurationDoc.getDescription())
        .serviceLevelObjectives(resumeDurationDoc.getTimerSLOs())
        .minimumExpectedValue(Duration.ofMillis(10))
        .register(meterRegistry);
  }

  public void instanceSuspended() {
    suspendedCounter.increment();
    suspendedInstances.increment();
  }

  public void instanceResumed() {
    resumedCounter.increment();
    suspendedInstances.decrement();
  }

  public void jobSuspended() {
    jobSuspendedCounter.increment();
  }

  public void jobsSuspended(final int count) {
    jobSuspendedCounter.increment(count);
  }

  public void jobResumed() {
    jobResumedCounter.increment();
  }

  public void commandBuffered() {
    commandBufferedCounter.increment();
    bufferedCommands.increment();
  }

  public void commandDrained() {
    commandDrainedCounter.increment();
    bufferedCommands.decrement();
  }

  public void commandsDropped(final int count) {
    commandDroppedCounter.increment(count);
    bufferedCommands.decrement(count);
  }

  public void startResumeDuration(final long processInstanceKey) {
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
   * Adjusts metrics when a process instance terminates while suspended or mid-resume, without going
   * through the normal resume path. Decrements the gauge so it does not inflate over time.
   */
  public void instanceTerminatedWhileSuspended() {
    suspendedInstances.decrement();
  }

  /**
   * Discards the in-flight resume-duration sample for the given key without recording it. Called
   * when a resuming instance terminates before the resume completes, so the sample does not leak.
   */
  public void cancelResumeDuration(final long processInstanceKey) {
    resumeDurationSamples.remove(processInstanceKey);
  }

  public void setSuspendedInstances(final long count) {
    suspendedInstances.set(count);
  }

  public void setBufferedCommands(final long count) {
    bufferedCommands.set(count);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    resumeDurationSamples.clear();
    if (suspensionState != null) {
      setSuspendedInstances(suspensionState.countSuspensionMarkers());
      setBufferedCommands(suspensionState.countBufferedCommands());
    }
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
