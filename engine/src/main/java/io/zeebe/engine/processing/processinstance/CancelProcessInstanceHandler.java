/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.workflowinstance;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class CancelWorkflowInstanceHandler implements WorkflowInstanceCommandHandler {

  private static final String MESSAGE_PREFIX =
      "Expected to cancel a workflow instance with key '%d', but ";

  private static final String WORKFLOW_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such workflow was found";

  private static final String WORKFLOW_NOT_ROOT_MESSAGE =
      MESSAGE_PREFIX
          + "it is created by a parent workflow instance. Cancel the root workflow instance '%d' instead.";

  @Override
  public void handle(final WorkflowInstanceCommandContext commandContext) {

    final TypedRecord<WorkflowInstanceRecord> command = commandContext.getRecord();
    final ElementInstance elementInstance = commandContext.getElementInstance();

    if (!validateCommand(commandContext, command, elementInstance)) {
      return;
    }

    final WorkflowInstanceRecord value = elementInstance.getValue();

    commandContext
        .getStreamWriter()
        .appendFollowUpEvent(command.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, value);

    elementInstance.setState(WorkflowInstanceIntent.ELEMENT_TERMINATING);
    commandContext.getElementInstanceState().updateInstance(elementInstance);

    commandContext
        .getResponseWriter()
        .writeEventOnCommand(
            command.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, value, command);
  }

  private boolean validateCommand(
      final WorkflowInstanceCommandContext commandContext,
      final TypedRecord<WorkflowInstanceRecord> command,
      final ElementInstance elementInstance) {

    if (elementInstance == null
        || !elementInstance.canTerminate()
        || elementInstance.getParentKey() > 0) {

      commandContext.reject(
          RejectionType.NOT_FOUND, String.format(WORKFLOW_NOT_FOUND_MESSAGE, command.getKey()));
      return false;
    }

    final var parentWorkflowInstanceKey = elementInstance.getValue().getParentWorkflowInstanceKey();
    if (parentWorkflowInstanceKey > 0) {

      final var rootWorkflowInstanceKey =
          getRootWorkflowInstanceKey(commandContext, parentWorkflowInstanceKey);
      commandContext.reject(
          RejectionType.INVALID_STATE,
          String.format(WORKFLOW_NOT_ROOT_MESSAGE, command.getKey(), rootWorkflowInstanceKey));
      return false;
    }

    return true;
  }

  private long getRootWorkflowInstanceKey(
      final WorkflowInstanceCommandContext context, final long instanceKey) {

    final var instance = context.getElementInstanceState().getInstance(instanceKey);
    if (instance != null) {

      final var parentWorkflowInstanceKey = instance.getValue().getParentWorkflowInstanceKey();
      if (parentWorkflowInstanceKey > 0) {

        return getRootWorkflowInstanceKey(context, parentWorkflowInstanceKey);
      }
    }
    return instanceKey;
  }
}
