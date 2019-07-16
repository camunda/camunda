/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.handlers.AbstractTerminalStateHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

/**
 * Delegates completion logic to sub class, and if successful and it is the last active element in
 * its flow scope, will terminate its flow scope.
 *
 * @param <T>
 */
public class ElementCompletedHandler<T extends ExecutableFlowNode>
    extends AbstractTerminalStateHandler<T> {
  public ElementCompletedHandler() {
    super();
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context)
        && isStateSameAsElementState(context)
        && (isRootScope(context) || isElementActive(context.getFlowScopeInstance()));
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (isLastActiveExecutionPathInScope(context)) {
      completeFlowScope(context);
    }

    return super.handleState(context);
  }

  protected void completeFlowScope(BpmnStepContext<T> context) {
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    final WorkflowInstanceRecord flowScopeInstanceValue = flowScopeInstance.getValue();

    context
        .getOutput()
        .appendFollowUpEvent(
            flowScopeInstance.getKey(),
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            flowScopeInstanceValue);
  }
}
