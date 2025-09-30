/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.usertask.processing.processors;

import io.camunda.zeebe.engine.common.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.common.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.common.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.HandlesIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
@HandlesIntent(intent = UserTaskIntent.class, type = "CANCEL")
public class UserTaskCancelProcessor implements UserTaskCommandProcessor {

  private final ElementInstanceState elementInstanceState;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;

  public UserTaskCancelProcessor(final ProcessingState state, final Writers writers) {
    elementInstanceState = state.getElementInstanceState();
    stateWriter = writers.state();
    commandWriter = writers.command();
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CANCELED, userTaskRecord);

    final var userTaskElementInstanceKey = userTaskRecord.getElementInstanceKey();
    final var userTaskElementInstance =
        elementInstanceState.getInstance(userTaskElementInstanceKey);
    if (userTaskElementInstance != null && userTaskElementInstance.isTerminating()) {
      commandWriter.appendFollowUpCommand(
          userTaskElementInstanceKey,
          ProcessInstanceIntent.CONTINUE_TERMINATING_ELEMENT,
          userTaskElementInstance.getValue());
    }
  }
}
