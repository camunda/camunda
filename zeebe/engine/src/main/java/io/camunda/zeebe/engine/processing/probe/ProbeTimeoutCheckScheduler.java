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
 */
public final class ProbeTimeoutCheckScheduler implements StreamProcessorLifecycleAware {

  static final long TIMEOUT_RESOLUTION = Duration.ofMillis(100).toMillis();

  private final DueDateCheckScheduler dueDateChecker;

  public ProbeTimeoutCheckScheduler(final InstantSource clock, final ProbeState probeState) {
    dueDateChecker =
        new DueDateCheckScheduler(
            TIMEOUT_RESOLUTION,
            false,
            taskResultBuilder ->
                probeState.findTimedOutProbes(
                    clock.millis(),
                    (key, probe) ->
                        taskResultBuilder.appendCommandRecord(
                            key, ProbeIntent.TIME_OUT, ProbeBehavior.withoutInput(probe))),
            clock);
  }

  public void schedule(final long deadline) {
    dueDateChecker.schedule(deadline);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
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
