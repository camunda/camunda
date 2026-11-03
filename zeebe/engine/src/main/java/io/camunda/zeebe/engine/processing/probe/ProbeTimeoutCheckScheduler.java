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
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;
import java.time.InstantSource;

/**
 * Times out probes at their deadline. A probe lives for seconds, so the checker runs at the next
 * deadline rather than polling, as {@code JobBackoffCheckScheduler} does for backed-off jobs.
 *
 * <p>Unlike for jobs, a client waits for the time-out, and the gateway gives up shortly after the
 * deadline. The schedule service runs a task due at least half its check interval away only at the
 * next check after it is due, up to a whole interval late; so while probes are pending, the checker
 * runs at most {@link #hop} ahead, which the schedule service times exactly.
 */
public final class ProbeTimeoutCheckScheduler implements StreamProcessorLifecycleAware {

  static final long TIMEOUT_RESOLUTION = Duration.ofMillis(100).toMillis();
  private static final long MAX_HOP = Duration.ofMillis(400).toMillis();

  private final DueDateCheckScheduler dueDateChecker;
  private final InstantSource clock;
  private long hop = MAX_HOP;

  public ProbeTimeoutCheckScheduler(final InstantSource clock, final ProbeState probeState) {
    this.clock = clock;
    dueDateChecker =
        new DueDateCheckScheduler(
            TIMEOUT_RESOLUTION,
            false,
            taskResultBuilder -> {
              final long nextDeadline =
                  probeState.findTimedOutProbes(
                      clock.millis(),
                      (key, probe) ->
                          taskResultBuilder.appendCommandRecord(
                              key, ProbeIntent.TIME_OUT, ProbeBehavior.withoutInput(probe)));
              return nextDeadline > 0 ? hopTowards(nextDeadline) : nextDeadline;
            },
            clock);
  }

  public void schedule(final long deadline) {
    dueDateChecker.schedule(hopTowards(deadline));
  }

  private long hopTowards(final long deadline) {
    return Math.min(deadline, clock.millis() + hop);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // below half the check interval, with headroom for the time between scheduling and the schedule
    // service measuring the delay
    final long halfInterval = context.getScheduledTaskCheckInterval().toMillis() / 2;
    hop = Math.max(TIMEOUT_RESOLUTION, Math.min(MAX_HOP, halfInterval - TIMEOUT_RESOLUTION));
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
