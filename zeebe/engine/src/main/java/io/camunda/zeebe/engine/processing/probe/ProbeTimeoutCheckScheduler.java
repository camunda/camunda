/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.zeebe.engine.processing.scheduled.DueDateCheckScheduler;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;
import java.time.InstantSource;
import org.jspecify.annotations.NullMarked;

/**
 * Times out probes at their deadline. It runs at the next deadline instead of polling, as {@code
 * JobBackoffCheckScheduler} does for backed-off jobs, and stops when no probe is pending.
 *
 * <p>Unlike for jobs, a client waits for the time-out, and the gateway gives up 500 ms after the
 * deadline. The schedule service runs a task due at least half its check interval away only at the
 * next check after it is due, up to a whole interval late. So while a probe is pending, the checker
 * runs at most {@link #hop} ahead, below half the check interval, which the schedule service times
 * exactly.
 */
@NullMarked
public final class ProbeTimeoutCheckScheduler implements StreamProcessorLifecycleAware {

  static final long TIMEOUT_RESOLUTION = Duration.ofMillis(100).toMillis();
  static final long MAX_HOP = Duration.ofMillis(400).toMillis();

  private final DueDateCheckScheduler dueDateChecker;
  private final InstantSource clock;
  private long hop = MAX_HOP;

  public ProbeTimeoutCheckScheduler(final ProbeState probeState, final InstantSource clock) {
    this.clock = clock;
    dueDateChecker =
        new DueDateCheckScheduler(
            TIMEOUT_RESOLUTION,
            false,
            taskResultBuilder -> {
              final long nextDeadline =
                  probeState.forEachTimedOutProbe(
                      clock.millis(),
                      (key, probe) ->
                          taskResultBuilder.appendCommandRecord(
                              key, ProbeIntent.TIME_OUT, timeOutCommand(probe)));
              return nextDeadline > 0 ? hopTowards(nextDeadline) : nextDeadline;
            },
            clock);
  }

  /** Makes sure the checker runs at the deadline, or sooner. */
  public void schedule(final long deadline) {
    dueDateChecker.schedule(hopTowards(deadline));
  }

  private long hopTowards(final long deadline) {
    return Math.min(deadline, clock.millis() + hop);
  }

  private static ProbeRecord timeOutCommand(final ProbeRecord probe) {
    return new ProbeRecord()
        .setProbeId(probe.getProbeId())
        .setTenantId(probe.getTenantId())
        .setDeadline(probe.getDeadline());
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // leave a resolution of headroom for the time between scheduling and the schedule service
    // measuring the delay
    final long halfInterval = context.getScheduledTaskCheckInterval().toMillis() / 2;
    hop = Math.max(TIMEOUT_RESOLUTION, Math.min(MAX_HOP, halfInterval - TIMEOUT_RESOLUTION));
    // on a new leader, this also expires the probes whose deadline passed during the leader change
    dueDateChecker.onRecovered(context);
  }

  @Override
  public void onClose() {
    dueDateChecker.onClose();
  }

  @Override
  public void onFailed() {
    dueDateChecker.onFailed();
  }

  @Override
  public void onPaused() {
    dueDateChecker.onPaused();
  }

  @Override
  public void onResumed() {
    dueDateChecker.onResumed();
  }
}
