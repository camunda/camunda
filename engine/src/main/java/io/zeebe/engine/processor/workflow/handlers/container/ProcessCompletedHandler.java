/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.container;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processor.workflow.handlers.element.ElementCompletedHandler;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class ProcessCompletedHandler
    extends ElementCompletedHandler<ExecutableFlowElementContainer> {

  @Override
  protected boolean handleState(BpmnStepContext<ExecutableFlowElementContainer> context) {

    final var record = context.getValue();
    final var parentWorkflowInstanceKey = record.getParentWorkflowInstanceKey();
    final var parentElementInstanceKey = record.getParentElementInstanceKey();

    if (parentWorkflowInstanceKey > 0) {
      // workflow instance is created by a call activity

      final var parentElementInstance =
          context.getStateDb().getElementInstanceState().getInstance(parentElementInstanceKey);

      if (parentElementInstance != null && parentElementInstance.isActive()) {
        // complete the corresponding call activity

        context
            .getOutput()
            .appendFollowUpEvent(
                parentElementInstanceKey,
                WorkflowInstanceIntent.ELEMENT_COMPLETING,
                parentElementInstance.getValue());
      }
    }

    return super.handleState(context);
  }
}
