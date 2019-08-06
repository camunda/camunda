/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.seqflow;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class SequenceFlowTakenHandler<T extends ExecutableSequenceFlow> extends AbstractHandler<T> {
  public SequenceFlowTakenHandler() {
    super(null);
  }

  public SequenceFlowTakenHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final ExecutableSequenceFlow sequenceFlow = context.getElement();
    final ExecutableFlowNode targetNode = sequenceFlow.getTarget();

    final WorkflowInstanceRecord value = context.getValue();
    context
        .getOutput()
        .appendNewEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING, value, targetNode);

    return true;
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context) && isElementActive(context.getFlowScopeInstance());
  }
}
