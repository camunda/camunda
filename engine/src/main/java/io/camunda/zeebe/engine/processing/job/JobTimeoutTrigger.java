/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.util.sched.clock.ActorClock.currentTimeMillis;

import io.camunda.zeebe.engine.processing.streamprocessor.ProcessingResult;
import io.camunda.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor.ProcessingSchedulingServiceImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandsBuilder;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.time.Duration;

public final class JobTimeoutTrigger implements StreamProcessorLifecycleAware {
  public static final Duration TIME_OUT_POLLING_INTERVAL = Duration.ofSeconds(30);
  private final JobState state;

  private ProcessingSchedulingServiceImpl processingSchedulingService;
  private CommandsBuilder commandsBuilder;

  public JobTimeoutTrigger(final JobState state) {
    this.state = state;
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext processingContext) {
    processingSchedulingService = processingContext.getProcessingSchedulingService();
    commandsBuilder = processingContext.getWriters().command();
    processingSchedulingService.runWithDelay(
        TIME_OUT_POLLING_INTERVAL, this::deactivateTimedOutJobs);
  }

  ProcessingResult deactivateTimedOutJobs() {
    commandsBuilder.reset();
    final long now = currentTimeMillis();
    state.forEachTimedOutEntry(
        now,
        (key, record) -> {
          commandsBuilder.appendFollowUpCommand(key, JobIntent.TIME_OUT, record);

          // todo here we could limit this to write not too much
          return true;
        });

    // reschedule
    processingSchedulingService.runWithDelay(
        TIME_OUT_POLLING_INTERVAL, this::deactivateTimedOutJobs);
    return new ProcessingResult(commandsBuilder);
  }
}
