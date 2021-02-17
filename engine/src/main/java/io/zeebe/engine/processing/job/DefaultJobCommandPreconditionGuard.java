/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.job;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor.CommandControl;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.immutable.JobState.State;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.RejectionType;
import java.util.function.BiConsumer;

/**
 * Default implementation to process JobCommands to reduce duplication in CommandProcessor
 * implementations.
 */
final class DefaultJobCommandPreconditionGuard<J extends JobRecord> {

  private static final String NO_JOB_FOUND_MESSAGE =
      "Expected to %s job with key '%d', but no such job was found";
  private static final String INVALID_JOB_STATE_MESSAGE =
      "Expected to %s job with key '%d', but it is in state '%s'";

  private final String intent;
  private final JobState state;
  private final BiConsumer<TypedRecord<J>, CommandControl<J>> acceptCommand;

  public DefaultJobCommandPreconditionGuard(
      final String intent,
      final JobState state,
      final BiConsumer<TypedRecord<J>, CommandControl<J>> acceptCommand) {
    this.intent = intent;
    this.state = state;
    this.acceptCommand = acceptCommand;
  }

  public boolean onCommand(final TypedRecord<J> command, final CommandControl<J> commandControl) {
    final long jobKey = command.getKey();
    final State jobState = state.getState(jobKey);

    if (jobState == State.ACTIVATABLE || jobState == State.ACTIVATED) {
      acceptCommand.accept(command, commandControl);

    } else if (jobState == State.NOT_FOUND) {
      final String message = String.format(NO_JOB_FOUND_MESSAGE, intent, jobKey);
      commandControl.reject(RejectionType.NOT_FOUND, message);

    } else {
      final String message = String.format(INVALID_JOB_STATE_MESSAGE, intent, jobKey, jobState);
      commandControl.reject(RejectionType.INVALID_STATE, message);
    }

    return true;
  }
}
