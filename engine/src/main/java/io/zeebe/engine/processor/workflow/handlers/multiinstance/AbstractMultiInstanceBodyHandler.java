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
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.Collections;
import java.util.function.Function;
import org.agrona.DirectBuffer;

public abstract class AbstractMultiInstanceBodyHandler
    extends AbstractHandler<ExecutableMultiInstanceBody> {

  private final MsgPackQueryProcessor queryProcessor = new MsgPackQueryProcessor();

  private final Function<BpmnStep, BpmnStepHandler> innerHandlerLookup;

  public AbstractMultiInstanceBodyHandler(
      WorkflowInstanceIntent nextState, Function<BpmnStep, BpmnStepHandler> innerHandlerLookup) {
    super(nextState);
    this.innerHandlerLookup = innerHandlerLookup;
  }

  @Override
  protected boolean handleState(BpmnStepContext<ExecutableMultiInstanceBody> context) {
    boolean transitionToNextState = false;

    if (isInnerActivity(context)) {
      handleInnerActivity(context);
    } else {
      transitionToNextState = handleMultiInstanceBody(context);
    }

    return transitionToNextState;
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<ExecutableMultiInstanceBody> context) {
    return super.shouldHandleState(context)
        && isStateSameAsElementState(context)
        && (isRootScope(context) || isElementActive(context.getFlowScopeInstance()));
  }

  private boolean isInnerActivity(BpmnStepContext<ExecutableMultiInstanceBody> context) {
    final DirectBuffer elementId = context.getElement().getId();
    final DirectBuffer flowScopeElementId =
        context.getFlowScopeInstance().getValue().getElementIdBuffer();
    return elementId.equals(flowScopeElementId);
  }

  private void handleInnerActivity(BpmnStepContext<ExecutableMultiInstanceBody> context) {
    final ExecutableActivity innerActivity = context.getElement().getInnerActivity();
    final BpmnStep innerStep = innerActivity.getStep(context.getState());

    context.setElement(innerActivity);

    innerHandlerLookup.apply(innerStep).handle(context);
  }

  protected abstract boolean handleMultiInstanceBody(
      BpmnStepContext<ExecutableMultiInstanceBody> context);

  protected MsgPackQueryProcessor.QueryResults readInputCollectionVariable(
      BpmnStepContext<ExecutableMultiInstanceBody> context) {

    final JsonPathQuery inputCollection =
        context.getElement().getLoopCharacteristics().getInputCollection();
    final DirectBuffer variableName = inputCollection.getVariableName();

    final VariablesState variablesState = context.getElementInstanceState().getVariablesState();
    final DirectBuffer variableAsDocument =
        variablesState.getVariablesAsDocument(
            context.getKey(), Collections.singleton(variableName));

    return queryProcessor.process(inputCollection, variableAsDocument);
  }
}
