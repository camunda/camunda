/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.job;

import static io.zeebe.util.sched.clock.ActorClock.currentTimeMillis;

import io.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.util.sched.ScheduledTimer;
import java.time.Duration;

public final class JobTimeoutTrigger implements StreamProcessorLifecycleAware {
  public static final Duration TIME_OUT_POLLING_INTERVAL = Duration.ofSeconds(30);
  private final JobState state;

  private ScheduledTimer timer;
  private TypedCommandWriter writer;
  private ReadonlyProcessingContext processingContext;

  public JobTimeoutTrigger(final JobState state) {
    this.state = state;
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext processingContext) {
    this.processingContext = processingContext;
    timer =
        this.processingContext
            .getActor()
            .runAtFixedRate(TIME_OUT_POLLING_INTERVAL, this::deactivateTimedOutJobs);
    writer = processingContext.getLogStreamWriter();
  }

  @Override
  public void onClose() {
    cancelTimer();
  }

  @Override
  public void onFailed() {
    cancelTimer();
  }

  @Override
  public void onPaused() {
    cancelTimer();
  }

  @Override
  public void onResumed() {
    if (timer == null) {
      timer =
          processingContext
              .getActor()
              .runAtFixedRate(TIME_OUT_POLLING_INTERVAL, this::deactivateTimedOutJobs);
    }
  }

  private void cancelTimer() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }

  void deactivateTimedOutJobs() {
    final long now = currentTimeMillis();
    state.forEachTimedOutEntry(
        now,
        (key, record) -> {
          writer.appendFollowUpCommand(key, JobIntent.TIME_OUT, record);

          final boolean flushed = writer.flush() >= 0;
          if (!flushed) {
            writer.reset();
          }
          return flushed;
        });
  }
}
