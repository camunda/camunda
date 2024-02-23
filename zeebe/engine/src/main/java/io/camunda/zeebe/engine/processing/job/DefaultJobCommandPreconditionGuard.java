/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor.CommandControl;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

/**
 * Default implementation to process JobCommands to reduce duplication in CommandProcessor
 * implementations.
 */
final class DefaultJobCommandPreconditionGuard {
  private final JobState state;
  private final JobAcceptFunction acceptCommand;
  private final JobCommandPreconditionChecker preconditionChecker;

  public DefaultJobCommandPreconditionGuard(
      final String intent, final JobState state, final JobAcceptFunction acceptCommand) {
    this.state = state;
    this.acceptCommand = acceptCommand;
    preconditionChecker =
        new JobCommandPreconditionChecker(intent, List.of(State.ACTIVATABLE, State.ACTIVATED));
  }

  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final State jobState = state.getState(jobKey);

    preconditionChecker
        .check(jobState, jobKey)
        .ifRightOrLeft(
            ok -> acceptCommand.accept(command, commandControl),
            violation -> commandControl.reject(violation.getLeft(), violation.getRight()));

    return true;
  }
}
