/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.job;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;

public final class FailProcessor implements CommandProcessor<JobRecord> {

  private final JobState state;
  private final DefaultJobCommandProcessor<JobRecord> defaultProcessor;
  private final ActorControl actorControl;

  public FailProcessor(final JobState state, final ActorControl actorControl) {
    this.state = state;
    this.actorControl = actorControl;

    defaultProcessor = new DefaultJobCommandProcessor<>("fail", this.state, this::acceptCommand);
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    return defaultProcessor.onCommand(command, commandControl);
  }

  private void acceptCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long key = command.getKey();
    final JobRecord failedJob = state.getJob(key);
    final long retryBackOff = command.getValue().getRetryBackOff();
    final int retries = command.getValue().getRetries();
    failedJob.setRetries(retries);
    failedJob.setRetryBackOff(retryBackOff);
    failedJob.setErrorMessage(command.getValue().getErrorMessageBuffer());
    if (retryBackOff != 0) {
      state.failForBackoff(key, failedJob);
      actorControl.runDelayed(
          Duration.ofMillis(retryBackOff),
          () -> {
            state.activable(key, failedJob);
          });
    } else {
      state.fail(key, failedJob);
    }
    commandControl.accept(JobIntent.FAILED, failedJob);
  }
}
