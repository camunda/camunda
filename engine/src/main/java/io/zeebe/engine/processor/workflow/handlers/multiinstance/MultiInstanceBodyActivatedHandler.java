/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.multiinstance;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.BpmnStepHandler;
import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.function.Function;
import org.agrona.DirectBuffer;

public class MultiInstanceBodyActivatedHandler extends AbstractMultiInstanceBodyHandler {

  public MultiInstanceBodyActivatedHandler(
      final Function<BpmnStep, BpmnStepHandler> innerHandlerLookup) {
    super(WorkflowInstanceIntent.ELEMENT_COMPLETING, innerHandlerLookup);
  }

  @Override
  protected boolean handleMultiInstanceBody(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    final MsgPackQueryProcessor.QueryResult result =
        readInputCollectionVariable(context).getSingleResult();

    final int createdInstances = result.readArray(item -> createInnerInstance(context, item));

    final boolean transitionToCompleting = createdInstances == 0;
    return transitionToCompleting;
  }

  private void createInnerInstance(
      final BpmnStepContext<ExecutableMultiInstanceBody> context, final DirectBuffer item) {

    final WorkflowInstanceRecord instanceRecord = context.getValue();
    instanceRecord.setFlowScopeKey(context.getKey());

    final long elementInstanceKey =
        context
            .getOutput()
            .appendNewEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING, instanceRecord);

    // need to spawn token for child instance
    context.getElementInstanceState().spawnToken(context.getKey());

    // set instance variable
    final VariablesState variablesState = context.getElementInstanceState().getVariablesState();

    context
        .getElement()
        .getLoopCharacteristics()
        .getInputElement()
        .ifPresent(
            inputElement ->
                variablesState.setVariableLocal(
                    elementInstanceKey, instanceRecord.getWorkflowKey(), inputElement, item));
  }
}
