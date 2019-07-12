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
import io.zeebe.engine.processor.workflow.handlers.element.ElementCompletedHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;

/**
 * ELEMENT_COMPLETED handler for elements which should take outgoing sequence flows without
 * prejudice.
 *
 * @param <T>
 */
public class FlowOutElementCompletedHandler<T extends ExecutableFlowNode>
    extends ElementCompletedHandler<T> {
  public FlowOutElementCompletedHandler() {
    super();
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final List<ExecutableSequenceFlow> outgoing = context.getElement().getOutgoing();

    for (final ExecutableSequenceFlow flow : outgoing) {
      takeSequenceFlow(context, flow);
    }

    return super.handleState(context);
  }

  private void takeSequenceFlow(BpmnStepContext<T> context, ExecutableSequenceFlow flow) {
    context
        .getOutput()
        .appendNewEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, context.getValue(), flow);
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    flowScopeInstance.spawnToken();
    context.getStateDb().getElementInstanceState().updateInstance(flowScopeInstance);
  }
}
