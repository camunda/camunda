/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.util.sched.clock.ActorClock.currentTimeMillis;

import io.camunda.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.time.Duration;

public final class JobTimeoutTrigger extends ScheduledTrigger {
  public static final Duration TIME_OUT_POLLING_INTERVAL = Duration.ofSeconds(30);
  private final JobState state;

  private TypedCommandWriter writer;

  public JobTimeoutTrigger(final JobState state) {
    this.state = state;
  }

  @Override
  protected Duration getPollingInterval() {
    return TIME_OUT_POLLING_INTERVAL;
  }

  @Override
  protected void executeOnTrigger() {
    deactivateTimedOutJobs();
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext processingContext) {
    super.onRecovered(processingContext);
    writer = processingContext.getLogStreamWriter();
  }

  void deactivateTimedOutJobs() {
    final long now = currentTimeMillis();
    state.forEachTimedOutEntry(
        now,
        (key, record) -> {
          writer.reset();
          writer.appendFollowUpCommand(key, JobIntent.TIME_OUT, record);

          return writer.flush() >= 0;
        });
  }
}
