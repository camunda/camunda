/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.nwe.container;

import io.zeebe.el.Expression;
import io.zeebe.engine.nwe.BpmnElementContainerProcessor;
import io.zeebe.engine.nwe.BpmnElementContext;
import io.zeebe.engine.nwe.behavior.BpmnBehaviors;
import io.zeebe.engine.nwe.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.nwe.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.nwe.behavior.BpmnStateBehavior;
import io.zeebe.engine.nwe.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MultiInstanceBodyProcessor
    implements BpmnElementContainerProcessor<ExecutableMultiInstanceBody> {

  private static final DirectBuffer NIL_VALUE = new UnsafeBuffer(MsgPackHelper.NIL);
  private static final DirectBuffer LOOP_COUNTER_VARIABLE = BufferUtil.wrapString("loopCounter");

  private final MutableDirectBuffer loopCounterVariableBuffer =
      new UnsafeBuffer(new byte[Long.BYTES + 1]);
  private final DirectBuffer loopCounterVariableView = new UnsafeBuffer(0, 0);

  private final MsgPackReader variableReader = new MsgPackReader();
  private final MsgPackWriter variableWriter = new MsgPackWriter();
  private final ExpandableArrayBuffer variableBuffer = new ExpandableArrayBuffer();
  private final DirectBuffer resultBuffer = new UnsafeBuffer(0, 0);

  private final ExpressionProcessor expressionBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final VariablesState variablesState;

  public MultiInstanceBodyProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    variablesState = stateBehavior.getVariablesState();
    expressionBehavior = bpmnBehaviors.expressionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
  }

  @Override
  public Class<ExecutableMultiInstanceBody> getType() {
    return ExecutableMultiInstanceBody.class;
  }

  @Override
  public void onActivating(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    // verify that the input collection variable is present and valid
    final Optional<List<DirectBuffer>> results = readInputCollectionVariable(element, context);
    if (results.isEmpty()) {
      return;
    }

    eventSubscriptionBehavior
        .subscribeToEvents(element, context)
        .ifRightOrLeft(
            ok -> stateTransitionBehavior.transitionToActivated(context),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onActivated(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    final var loopCharacteristics = element.getLoopCharacteristics();
    final Optional<List<DirectBuffer>> inputCollection =
        readInputCollectionVariable(element, context);
    if (inputCollection.isEmpty()) {
      return;
    }

    final var array = inputCollection.get();
    loopCharacteristics
        .getOutputCollection()
        .ifPresent(variableName -> initializeOutputCollection(context, variableName, array.size()));

    if (array.isEmpty()) {
      // complete the multi-instance body immediately
      stateTransitionBehavior.transitionToCompleting(context);
      return;
    }

    if (loopCharacteristics.isSequential()) {
      final var firstItem = array.get(0);
      createInnerInstance(element, context, firstItem);

    } else {
      array.forEach(item -> createInnerInstance(element, context, item));
    }
  }

  @Override
  public void onCompleting(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);

    element
        .getLoopCharacteristics()
        .getOutputCollection()
        .ifPresent(variableName -> stateBehavior.propagateVariable(context, variableName));

    stateTransitionBehavior.transitionToCompleted(context);
  }

  @Override
  public void onCompleted(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    stateTransitionBehavior.takeOutgoingSequenceFlows(element, context);

    stateBehavior.consumeToken(context);
    stateBehavior.removeInstance(context);
  }

  @Override
  public void onTerminating(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {}

  @Override
  public void onTerminated(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {}

  @Override
  public void onEventOccurred(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {}

  private Optional<List<DirectBuffer>> readInputCollectionVariable(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    final Expression inputCollection = element.getLoopCharacteristics().getInputCollection();
    return expressionBehavior.evaluateArrayExpression(inputCollection, context.toStepContext());
  }

  private void createInnerInstance(
      final ExecutableMultiInstanceBody multiInstanceBody,
      final BpmnElementContext context,
      final DirectBuffer item) {

    final var innerInstance =
        stateTransitionBehavior.activateChildInstance(
            context, multiInstanceBody.getInnerActivity());

    // update loop counters
    final var bodyInstance = stateBehavior.getElementInstance(context);
    bodyInstance.incrementMultiInstanceLoopCounter();
    stateBehavior.updateElementInstance(bodyInstance);

    innerInstance.setMultiInstanceLoopCounter(bodyInstance.getMultiInstanceLoopCounter());
    stateBehavior.updateElementInstance(innerInstance);

    // set instance variables
    final var loopCharacteristics = multiInstanceBody.getLoopCharacteristics();

    loopCharacteristics
        .getInputElement()
        .ifPresent(
            variableName ->
                variablesState.setVariableLocal(
                    innerInstance.getKey(), context.getWorkflowKey(), variableName, item));

    // Output element expressions that are just a variable or nested property of a variable need to
    // be initialised with a nil-value. This makes sure that they are not written at a non-local
    // scope.
    loopCharacteristics
        .getOutputElement()
        .flatMap(Expression::getVariableName)
        .map(BufferUtil::wrapString)
        .ifPresent(
            variableName ->
                variablesState.setVariableLocal(
                    innerInstance.getKey(), context.getWorkflowKey(), variableName, NIL_VALUE));

    variablesState.setVariableLocal(
        innerInstance.getKey(),
        context.getWorkflowKey(),
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

  private void initializeOutputCollection(
      final BpmnElementContext context, final DirectBuffer variableName, final int size) {

    variableWriter.wrap(variableBuffer, 0);

    // initialize the array with nil
    variableWriter.writeArrayHeader(size);
    for (var i = 0; i < size; i++) {
      variableWriter.writeNil();
    }

    final var length = variableWriter.getOffset();

    stateBehavior.setLocalVariable(context, variableName, variableBuffer, 0, length);
  }

  @Override
  public void onChildCompleted(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {
    final var loopCharacteristics = element.getLoopCharacteristics();

    if (loopCharacteristics.isSequential()) {

      final var inputCollectionVariable = readInputCollectionVariable(element, childContext);
      if (inputCollectionVariable.isEmpty()) {
        return;
      }

      final var array = inputCollectionVariable.get();
      final var loopCounter =
          stateBehavior.getFlowScopeInstance(childContext).getMultiInstanceLoopCounter();
      if (loopCounter < array.size()) {

        final var item = array.get(loopCounter);
        createInnerInstance(element, flowScopeContext, item);
      }
    }

    final Optional<Boolean> updatedSuccessfully =
        loopCharacteristics
            .getOutputCollection()
            .map(variableName -> updateOutputCollection(element, childContext, variableName));

    if (updatedSuccessfully.isPresent() && !updatedSuccessfully.get()) {
      // An incident was raised while updating the output collection, stop handling activity
      return;
    }

    if (stateBehavior.isLastActiveExecutionPathInScope(childContext)) {
      stateTransitionBehavior.transitionToCompleting(flowScopeContext);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    if (flowScopeContext.getIntent() == WorkflowInstanceIntent.ELEMENT_TERMINATING
        && stateBehavior.isLastActiveExecutionPathInScope(childContext)) {
      stateTransitionBehavior.transitionToTerminated(flowScopeContext);

    } else {
      eventSubscriptionBehavior.publishTriggeredEventSubProcess(flowScopeContext);
    }
  }

  private boolean updateOutputCollection(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext childContext,
      final DirectBuffer variableName) {

    final var bodyInstanceKey = childContext.getFlowScopeKey();
    final var loopCounter =
        stateBehavior.getElementInstance(childContext).getMultiInstanceLoopCounter();

    final Optional<DirectBuffer> elementVariable = readOutputElementVariable(element, childContext);
    if (elementVariable.isEmpty()) {
      return false;
    }

    // we need to read the output element variable before the current collection is read,
    // because readOutputElementVariable(Context) uses the same buffer as getVariableLocal
    // this could also be avoided by cloning the current collection, but that is slower.
    final var currentCollection = variablesState.getVariableLocal(bodyInstanceKey, variableName);
    final var updatedCollection = insertAt(currentCollection, loopCounter, elementVariable.get());
    variablesState.setVariableLocal(
        bodyInstanceKey, childContext.getWorkflowKey(), variableName, updatedCollection);

    return true;
  }

  private Optional<DirectBuffer> readOutputElementVariable(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    final var expression = element.getLoopCharacteristics().getOutputElement().orElseThrow();
    return expressionBehavior.evaluateAnyExpression(expression, context.toStepContext());
  }

  private DirectBuffer insertAt(
      final DirectBuffer array, final int index, final DirectBuffer element) {

    variableReader.wrap(array, 0, array.capacity());
    variableReader.readArrayHeader();
    variableReader.skipValues((long) index - 1L);

    final var offsetBefore = variableReader.getOffset();
    variableReader.skipValue();
    final var offsetAfter = variableReader.getOffset();

    variableWriter.wrap(variableBuffer, 0);
    variableWriter.writeRaw(array, 0, offsetBefore);
    variableWriter.writeRaw(element);
    variableWriter.writeRaw(array, offsetAfter, array.capacity() - offsetAfter);

    final var length = variableWriter.getOffset();

    resultBuffer.wrap(variableBuffer, 0, length);
    return resultBuffer;
  }
}
