/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.instance;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.workflow.EventOutput;
import io.zeebe.engine.processor.workflow.WorkflowInstanceCommandContext;
import io.zeebe.engine.processor.workflow.WorkflowInstanceCommandHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class CancelWorkflowInstanceHandler implements WorkflowInstanceCommandHandler {

  private static final String WORKFLOW_NOT_FOUND_MESSAGE =
      "Expected to cancel a workflow instance with key '%d', but no such workflow was found";

  @Override
  public void handle(WorkflowInstanceCommandContext commandContext) {

    final TypedRecord<WorkflowInstanceRecord> command = commandContext.getRecord();
    final ElementInstance elementInstance = commandContext.getElementInstance();

    final boolean canCancel =
        elementInstance != null
            && elementInstance.canTerminate()
            && elementInstance.getParentKey() < 0;

    if (canCancel) {
      final EventOutput output = commandContext.getOutput();
      final WorkflowInstanceRecord value = elementInstance.getValue();

      output.appendFollowUpEvent(
          command.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, value);

      commandContext
          .getResponseWriter()
          .writeEventOnCommand(
              command.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, value, command);
    } else {
      commandContext.reject(
          RejectionType.NOT_FOUND, String.format(WORKFLOW_NOT_FOUND_MESSAGE, command.getKey()));
    }
  }
}
