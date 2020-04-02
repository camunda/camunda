/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.multiinstance;

import io.zeebe.el.Expression;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.BpmnStepHandler;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public abstract class AbstractMultiInstanceBodyHandler
    extends AbstractHandler<ExecutableMultiInstanceBody> {

  private static final DirectBuffer NIL_VALUE = new UnsafeBuffer(MsgPackHelper.NIL);
  private static final DirectBuffer LOOP_COUNTER_VARIABLE = BufferUtil.wrapString("loopCounter");
  protected final ExpressionProcessor expressionProcessor;

  private final Function<BpmnStep, BpmnStepHandler> innerHandlerLookup;

  private final MutableDirectBuffer loopCounterVariableBuffer =
      new UnsafeBuffer(new byte[Long.BYTES + 1]);
  private final DirectBuffer loopCounterVariableView = new UnsafeBuffer(0, 0);
  private final MsgPackWriter variableWriter = new MsgPackWriter();

  public AbstractMultiInstanceBodyHandler(
      final WorkflowInstanceIntent nextState,
      final Function<BpmnStep, BpmnStepHandler> innerHandlerLookup,
      final ExpressionProcessor expressionProcessor) {
    super(nextState);
    this.innerHandlerLookup = innerHandlerLookup;
    this.expressionProcessor = expressionProcessor;
  }

  @Override
  protected boolean handleState(final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    boolean transitionToNextState = false;

    if (isInnerActivity(context)) {
      handleInnerActivity(context);
    } else {
      transitionToNextState = handleMultiInstanceBody(context);
    }

    return transitionToNextState;
  }

  @Override
  protected boolean shouldHandleState(final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    return super.shouldHandleState(context)
        && isStateSameAsElementState(context)
        && (isRootScope(context) || isElementActive(context.getFlowScopeInstance()));
  }

  private boolean isInnerActivity(final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    final DirectBuffer elementId = context.getElement().getId();
    final DirectBuffer flowScopeElementId =
        context.getFlowScopeInstance().getValue().getElementIdBuffer();
    return elementId.equals(flowScopeElementId);
  }

  protected void handleInnerActivity(final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    final ExecutableActivity innerActivity = context.getElement().getInnerActivity();
    final BpmnStep innerStep = innerActivity.getStep(context.getState());

    context.setElement(innerActivity);

    innerHandlerLookup.apply(innerStep).handle(context);
  }

  protected abstract boolean handleMultiInstanceBody(
      BpmnStepContext<ExecutableMultiInstanceBody> context);

  protected Optional<List<DirectBuffer>> readInputCollectionVariable(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    final Expression inputCollection =
        context.getElement().getLoopCharacteristics().getInputCollection();
    return expressionProcessor.evaluateArrayExpression(inputCollection, context);
  }

  protected void createInnerInstance(
      final BpmnStepContext<ExecutableMultiInstanceBody> context,
      final long bodyInstanceKey,
      final DirectBuffer item) {

    final var elementInstanceState = context.getElementInstanceState();
    final var variablesState = elementInstanceState.getVariablesState();

    final var multiInstanceBody = context.getElement();
    final var innerActivityRecord = context.getValue().setFlowScopeKey(bodyInstanceKey);

    final long elementInstanceKey =
        context
            .getOutput()
            .appendNewEvent(
                WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                innerActivityRecord,
                multiInstanceBody.getInnerActivity());

    // update loop counters
    final var bodyInstance = elementInstanceState.getInstance(bodyInstanceKey);
    bodyInstance.spawnToken();
    bodyInstance.incrementMultiInstanceLoopCounter();
    elementInstanceState.updateInstance(bodyInstance);

    final var innerInstance = elementInstanceState.getInstance(elementInstanceKey);
    innerInstance.setMultiInstanceLoopCounter(bodyInstance.getMultiInstanceLoopCounter());
    elementInstanceState.updateInstance(innerInstance);

    // set instance variables
    final var workflowKey = innerActivityRecord.getWorkflowKey();
    final var loopCharacteristics = multiInstanceBody.getLoopCharacteristics();

    loopCharacteristics
        .getInputElement()
        .ifPresent(
            variableName ->
                variablesState.setVariableLocal(
                    elementInstanceKey, workflowKey, variableName, item));

    // Output element expressions that are just a variable or nested variable need to be initialised
    // with a nil-value. This makes sure that they are not written at a non-local scope.
    loopCharacteristics
        .getOutputElement()
        .map(Expression::getExpression)
        .map(expression -> expression.split("\\.")[0]) // only take the main variable name
        // TODO #4100 (@korthout/@saig0)
        // Filter out all non-variable expressions without this ugly regex :)
        .ifPresent(
            variableName ->
                variablesState.setVariableLocal(
                    elementInstanceKey,
                    workflowKey,
                    BufferUtil.wrapString(variableName),
                    NIL_VALUE));

    variablesState.setVariableLocal(
        elementInstanceKey,
        workflowKey,
        LOOP_COUNTER_VARIABLE,
        wrapLoopCounter(innerInstance.getMultiInstanceLoopCounter()));
  }

  private DirectBuffer wrapLoopCounter(final int loopCounter) {
    variableWriter.wrap(loopCounterVariableBuffer, 0);

    variableWriter.writeInteger(loopCounter);
    final var length = variableWriter.getOffset();

    loopCounterVariableView.wrap(loopCounterVariableBuffer, 0, length);
    return loopCounterVariableView;
  }
}
