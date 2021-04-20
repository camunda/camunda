/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.processinstance;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;

public final class CancelProcessInstanceHandler implements ProcessInstanceCommandHandler {

  private static final String MESSAGE_PREFIX =
      "Expected to cancel a process instance with key '%d', but ";

  private static final String PROCESS_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such process was found";

  private static final String PROCESS_NOT_ROOT_MESSAGE =
      MESSAGE_PREFIX
          + "it is created by a parent process instance. Cancel the root process instance '%d' instead.";

  @Override
  public void handle(final ProcessInstanceCommandContext commandContext) {

    final TypedRecord<ProcessInstanceRecord> command = commandContext.getRecord();
    final ElementInstance elementInstance = commandContext.getElementInstance();

    if (!validateCommand(commandContext, command, elementInstance)) {
      return;
    }

    final ProcessInstanceRecord value = elementInstance.getValue();

    commandContext
        .getStreamWriter()
        .appendFollowUpCommand(command.getKey(), ProcessInstanceIntent.TERMINATE_ELEMENT, value);

    commandContext
        .getResponseWriter()
        .writeEventOnCommand(
            command.getKey(), ProcessInstanceIntent.ELEMENT_TERMINATING, value, command);
  }

  private boolean validateCommand(
      final ProcessInstanceCommandContext commandContext,
      final TypedRecord<ProcessInstanceRecord> command,
      final ElementInstance elementInstance) {

    if (elementInstance == null
        || !elementInstance.canTerminate()
        || elementInstance.getParentKey() > 0) {

      commandContext.reject(
          RejectionType.NOT_FOUND, String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey()));
      return false;
    }

    final var parentProcessInstanceKey = elementInstance.getValue().getParentProcessInstanceKey();
    if (parentProcessInstanceKey > 0) {

      final var rootProcessInstanceKey =
          getRootProcessInstanceKey(commandContext, parentProcessInstanceKey);
      commandContext.reject(
          RejectionType.INVALID_STATE,
          String.format(PROCESS_NOT_ROOT_MESSAGE, command.getKey(), rootProcessInstanceKey));
      return false;
    }

    return true;
  }

  private long getRootProcessInstanceKey(
      final ProcessInstanceCommandContext context, final long instanceKey) {

    final var instance = context.getElementInstanceState().getInstance(instanceKey);
    if (instance != null) {

      final var parentProcessInstanceKey = instance.getValue().getParentProcessInstanceKey();
      if (parentProcessInstanceKey > 0) {

        return getRootProcessInstanceKey(context, parentProcessInstanceKey);
      }
    }
    return instanceKey;
  }
}
