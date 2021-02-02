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
import io.zeebe.engine.state.mutable.MutableJobState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.JobIntent;

public final class CompleteProcessor implements CommandProcessor<JobRecord> {

  private final MutableJobState state;
  private final DefaultJobCommandProcessor<JobRecord> defaultProcessor;

  public CompleteProcessor(final MutableJobState state) {
    this.state = state;
    defaultProcessor =
        new DefaultJobCommandProcessor<>("complete", this.state, this::acceptCommand);
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    return defaultProcessor.onCommand(command, commandControl);
  }

  private void acceptCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final JobRecord job = state.getJob(jobKey);
    job.setVariables(command.getValue().getVariablesBuffer());
    state.complete(jobKey, job);
    commandControl.accept(JobIntent.COMPLETED, job);
  }
}
