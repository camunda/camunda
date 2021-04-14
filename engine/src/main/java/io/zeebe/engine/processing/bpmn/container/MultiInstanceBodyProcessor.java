/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.container;

import io.zeebe.el.Expression;
import io.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.util.Either;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
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

  public MultiInstanceBodyProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
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
    readInputCollectionVariable(element, context)
        .flatMap(ok -> eventSubscriptionBehavior.subscribeToEvents(element, context))
        .ifRightOrLeft(
            ok -> stateTransitionBehavior.transitionToActivated(context),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onActivated(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    final var inputCollectionOrFailure = readInputCollectionVariable(element, context);
    if (inputCollectionOrFailure.isLeft()) {
      incidentBehavior.createIncident(inputCollectionOrFailure.getLeft(), context);
      return;
    }

    final var inputCollection = inputCollectionOrFailure.get();

    final var loopCharacteristics = element.getLoopCharacteristics();
    loopCharacteristics
        .getOutputCollection()
        .ifPresent(
            variableName ->
                initializeOutputCollection(context, variableName, inputCollection.size()));

    if (inputCollection.isEmpty()) {
      // complete the multi-instance body immediately
      stateTransitionBehavior.transitionToCompleting(context);
      return;
    }

    if (loopCharacteristics.isSequential()) {
      final var firstItem = inputCollection.get(0);
      createInnerInstance(element, context, firstItem);

    } else {
      inputCollection.forEach(item -> createInnerInstance(element, context, item));
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

    stateTransitionBehavior.transitionToCompletedWithParentNotification(element, context);
  }

  @Override
  public void onCompleted(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    if (element.getOutgoing().isEmpty()) {
      /* can be dropped during migration; after migration this is done as part of
      stateTransitionBehavior.transitionToCompletedWithParentNotification(...)*/
      stateTransitionBehavior.afterExecutionPathCompleted(element, context);
    }
    stateTransitionBehavior.takeOutgoingSequenceFlows(element, context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onTerminating(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);

    final var noActiveChildInstances = stateTransitionBehavior.terminateChildInstances(context);
    if (noActiveChildInstances) {
      stateTransitionBehavior.transitionToTerminated(context);
    }
  }

  @Override
  public void onTerminated(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    eventSubscriptionBehavior.publishTriggeredBoundaryEvent(context);

    incidentBehavior.resolveIncidents(context);

    stateTransitionBehavior.onElementTerminated(element, context);

    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onEventOccurred(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    eventSubscriptionBehavior.triggerBoundaryEvent(element, context);
  }

  @Override
  public void onChildActivating(
      final ExecutableMultiInstanceBody multiInstanceBody,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    final var bodyInstance = stateBehavior.getElementInstance(flowScopeContext);
    final var loopCounter = bodyInstance.getMultiInstanceLoopCounter();

    readInputCollectionVariable(multiInstanceBody, childContext)
        .flatMap(
            collection -> {
              // the loop counter starts by 1
              final var index = loopCounter - 1;
              if (index < collection.size()) {
                final var item = collection.get(index);
                return Either.right(item);
              } else {
                final var incidentMessage =
                    String.format(
                        "Expected to read item at index %d of the multiInstanceBody input collection but it contains only %d elements. The input collection might be modified while iterating over it.",
                        index, collection.size());
                final var failure = new Failure(incidentMessage, ErrorType.EXTRACT_VALUE_ERROR);
                return Either.left(failure);
              }
            })
        .ifRightOrLeft(
            inputElement ->
                setLoopVariables(multiInstanceBody, childContext, loopCounter, inputElement),
            failure -> incidentBehavior.createIncident(failure, childContext));
  }

  @Override
  public void beforeExecutionPathCompleted(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {
    final var updatedOrFailure = updateOutputCollection(element, childContext, flowScopeContext);
    if (updatedOrFailure.isLeft()) {
      incidentBehavior.createIncident(updatedOrFailure.getLeft(), childContext);
      return;
    }
  }

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    boolean childInstanceCreated = false;

    final var loopCharacteristics = element.getLoopCharacteristics();
    if (loopCharacteristics.isSequential()) {

      final var inputCollectionOrFailure = readInputCollectionVariable(element, flowScopeContext);
      if (inputCollectionOrFailure.isLeft()) {
        incidentBehavior.createIncident(inputCollectionOrFailure.getLeft(), childContext);
        return;
      }

      final var inputCollection = inputCollectionOrFailure.get();

      final var loopCounter =
          stateBehavior.getElementInstance(flowScopeContext).getMultiInstanceLoopCounter();

      if (loopCounter < inputCollection.size()) {

        final var item = inputCollection.get(loopCounter);
        createInnerInstance(element, flowScopeContext, item);

        // canBeCompleted() doesn't take the created child instance into account because
        // it wrote just a ACTIVATE command that create no new instance immediately
        childInstanceCreated = true;
      }
    }

    if (!childInstanceCreated && stateBehavior.canBeCompleted(childContext)) {
      stateTransitionBehavior.transitionToCompleting(flowScopeContext);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    if (flowScopeContext.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING
        && stateBehavior.canBeTerminated(childContext)) {
      stateTransitionBehavior.transitionToTerminated(flowScopeContext);

    } else {
      eventSubscriptionBehavior.publishTriggeredEventSubProcess(
          MigratedStreamProcessors.isMigrated(childContext.getBpmnElementType()), flowScopeContext);
    }
  }

  private void setLoopVariables(
      final ExecutableMultiInstanceBody multiInstanceBody,
      final BpmnElementContext childContext,
      final int loopCounter,
      final DirectBuffer inputElement) {

    final var loopCharacteristics = multiInstanceBody.getLoopCharacteristics();
    loopCharacteristics
        .getInputElement()
        .ifPresent(
            variableName ->
                stateBehavior.setLocalVariable(childContext, variableName, inputElement));

    // Output multiInstanceBody expressions that are just a variable or nested property of a
    // variable need to
    // be initialised with a nil-value. This makes sure that they are not written at a non-local
    // scope.
    loopCharacteristics
        .getOutputElement()
        .flatMap(Expression::getVariableName)
        .map(BufferUtil::wrapString)
        .ifPresent(
            variableName -> stateBehavior.setLocalVariable(childContext, variableName, NIL_VALUE));

    stateBehavior.setLocalVariable(
        childContext, LOOP_COUNTER_VARIABLE, wrapLoopCounter(loopCounter));
  }

  private Either<Failure, List<DirectBuffer>> readInputCollectionVariable(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    final Expression inputCollection = element.getLoopCharacteristics().getInputCollection();
    return expressionBehavior.evaluateArrayExpression(
        inputCollection, context.getElementInstanceKey());
  }

  private void createInnerInstance(
      final ExecutableMultiInstanceBody multiInstanceBody,
      final BpmnElementContext context,
      final DirectBuffer item) {

    final var innerInstanceKey =
        stateTransitionBehavior.activateChildInstanceWithKey(
            context, multiInstanceBody.getInnerActivity());

    // for migrated processors, the state changes happens on the ACTIVATING event applier
    // and on processing the ACTIVATE command by calling onChildActivating()
    if (!MigratedStreamProcessors.isMigrated(
        multiInstanceBody.getInnerActivity().getElementType())) {

      final var innerInstance = stateBehavior.getElementInstance(innerInstanceKey);
      final var innerInstanceContext =
          context.copy(innerInstanceKey, innerInstance.getValue(), innerInstance.getState());

      // update loop counters
      final var bodyInstance = stateBehavior.getElementInstance(context);
      bodyInstance.incrementMultiInstanceLoopCounter();
      stateBehavior.updateElementInstance(bodyInstance);

      final var loopCounter = bodyInstance.getMultiInstanceLoopCounter();
      innerInstance.setMultiInstanceLoopCounter(loopCounter);
      stateBehavior.updateElementInstance(innerInstance);

      setLoopVariables(multiInstanceBody, innerInstanceContext, loopCounter, item);
    }
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

  private Either<Failure, Void> updateOutputCollection(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext childContext,
      final BpmnElementContext flowScopeContext) {

    return element
        .getLoopCharacteristics()
        .getOutputCollection()
        .map(
            variableName ->
                updateOutputCollection(element, childContext, flowScopeContext, variableName))
        .orElse(Either.right(null));
  }

  private Either<Failure, Void> updateOutputCollection(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext childContext,
      final BpmnElementContext flowScopeContext,
      final DirectBuffer variableName) {

    final var loopCounter =
        stateBehavior.getElementInstance(childContext).getMultiInstanceLoopCounter();

    return readOutputElementVariable(element, childContext)
        .map(
            elementVariable -> {
              // we need to read the output element variable before the current collection
              // is read, because readOutputElementVariable(Context) uses the same
              // buffer as getVariableLocal this could also be avoided by cloning the current
              // collection, but that is slower.
              final var currentCollection =
                  stateBehavior.getLocalVariable(flowScopeContext, variableName);
              final var updatedCollection =
                  insertAt(currentCollection, loopCounter, elementVariable);
              stateBehavior.setLocalVariable(flowScopeContext, variableName, updatedCollection);

              return null;
            });
  }

  private Either<Failure, DirectBuffer> readOutputElementVariable(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    final var expression = element.getLoopCharacteristics().getOutputElement().orElseThrow();
    return expressionBehavior.evaluateAnyExpression(expression, context.getElementInstanceKey());
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
