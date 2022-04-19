/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
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

  private final MsgPackWriter variableWriter = new MsgPackWriter();

  private final ExpressionProcessor expressionBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final OutputCollectionBehavior outputCollectionBehavior;

  public MultiInstanceBodyProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    expressionBehavior = bpmnBehaviors.expressionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    outputCollectionBehavior =
        new OutputCollectionBehavior(stateBehavior, bpmnBehaviors.expressionBehavior());
  }

  @Override
  public Class<ExecutableMultiInstanceBody> getType() {
    return ExecutableMultiInstanceBody.class;
  }

  @Override
  public void onActivate(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    // verify that the input collection variable is present and valid
    readInputCollectionVariable(element, context)
        .flatMap(
            inputCollection ->
                eventSubscriptionBehavior
                    .subscribeToEvents(element, context)
                    .map(ok -> inputCollection))
        .ifRightOrLeft(
            inputCollection -> activate(element, context, inputCollection),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onComplete(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);

    element
        .getLoopCharacteristics()
        .getOutputCollection()
        .ifPresent(variableName -> stateBehavior.propagateVariable(context, variableName));

    stateTransitionBehavior
        .transitionToCompleted(element, context)
        .ifRightOrLeft(
            completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onTerminate(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);

    final var noActiveChildInstances = stateTransitionBehavior.terminateChildInstances(context);
    if (noActiveChildInstances) {
      terminate(element, context);
    }
  }

  @Override
  public Either<Failure, ?> onChildActivating(
      final ExecutableMultiInstanceBody multiInstanceBody,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    // loop counter is already set on the inner instance on activating
    final int loopCounter =
        stateBehavior.getElementInstance(childContext).getMultiInstanceLoopCounter();

    return readInputCollectionVariable(multiInstanceBody, childContext)
        .flatMap(
            collection -> {
              // the loop counter starts at 1
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
        .map(
            inputElement -> {
              setLoopVariables(multiInstanceBody, childContext, loopCounter, inputElement);
              return null;
            });
  }

  @Override
  public Either<Failure, ?> beforeExecutionPathCompleted(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {
    final var updatedOrFailure =
        outputCollectionBehavior.updateOutputCollection(element, childContext, flowScopeContext);
    if (updatedOrFailure.isLeft()) {
      return updatedOrFailure;
    }

    // test that completion condition can be evaluated correctly
    final Either<Failure, Boolean> satisfiesCompletionConditionOrFailure =
        satisfiesCompletionCondition(element, childContext);
    if (satisfiesCompletionConditionOrFailure.isLeft()) {
      return satisfiesCompletionConditionOrFailure;
    }

    if (!element.getLoopCharacteristics().isSequential()) {
      return Either.right(satisfiesCompletionConditionOrFailure.get());
    }

    // test that input collection variable can be evaluated correctly
    return readInputCollectionVariable(element, flowScopeContext)
        .map(ok -> satisfiesCompletionConditionOrFailure.get());
  }

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext,
      final Boolean satisfiesCompletionCondition) {

    boolean childInstanceCreated = false;

    final var loopCharacteristics = element.getLoopCharacteristics();

    if (satisfiesCompletionCondition) {
      // terminate all remaining child instances because the completion condition evaluates to true.
      final boolean hasNoActiveChildren =
          stateTransitionBehavior.terminateChildInstances(flowScopeContext);

      if (hasNoActiveChildren || loopCharacteristics.isSequential()) {
        // complete the multi-instance body immediately
        stateTransitionBehavior.completeElement(flowScopeContext);
      }
      return;
    }

    if (loopCharacteristics.isSequential()) {

      final var inputCollectionOrFailure = readInputCollectionVariable(element, flowScopeContext);
      if (inputCollectionOrFailure.isLeft()) {
        // this incident is un-resolvable
        incidentBehavior.createIncident(inputCollectionOrFailure.getLeft(), childContext);
        return;
      }

      final var inputCollection = inputCollectionOrFailure.get();
      final var loopCounter =
          stateBehavior.getElementInstance(flowScopeContext).getMultiInstanceLoopCounter();

      if (loopCounter < inputCollection.size()) {
        createInnerInstance(element, flowScopeContext);

        // canBeCompleted() doesn't take the created child instance into account because
        // it wrote just a ACTIVATE command that create no new instance immediately
        childInstanceCreated = true;
      }
    }

    if (!childInstanceCreated && stateBehavior.canBeCompleted(childContext)) {
      stateTransitionBehavior.completeElement(flowScopeContext);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {
    if (flowScopeContext.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING) {
      if (stateBehavior.canBeTerminated(childContext)) {
        terminate(element, flowScopeContext);
      }
    } else if (stateBehavior.canBeCompleted(childContext)) {
      // complete the multi-instance body because it's completion condition was met previously and
      // all remaining child instances were terminated.
      stateTransitionBehavior.completeElement(flowScopeContext);
    }
  }

  private void activate(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext context,
      final List<DirectBuffer> inputCollection) {
    final BpmnElementContext activated = stateTransitionBehavior.transitionToActivated(context);
    final var loopCharacteristics = element.getLoopCharacteristics();
    loopCharacteristics
        .getOutputCollection()
        .ifPresent(
            variableName ->
                outputCollectionBehavior.initializeOutputCollection(
                    activated, variableName, inputCollection.size()));

    if (inputCollection.isEmpty()) {
      // complete the multi-instance body immediately
      stateTransitionBehavior.completeElement(activated);
      return;
    }

    if (loopCharacteristics.isSequential()) {
      createInnerInstance(element, activated);
    } else {
      inputCollection.forEach(item -> createInnerInstance(element, activated));
    }
  }

  private void terminate(
      final ExecutableMultiInstanceBody element, final BpmnElementContext flowScopeContext) {
    incidentBehavior.resolveIncidents(flowScopeContext);

    eventSubscriptionBehavior
        .findEventTrigger(flowScopeContext)
        .ifPresentOrElse(
            eventTrigger -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(flowScopeContext);
              eventSubscriptionBehavior.activateTriggeredEvent(
                  flowScopeContext.getElementInstanceKey(),
                  terminated.getFlowScopeKey(),
                  eventTrigger,
                  terminated);
              stateTransitionBehavior.onElementTerminated(element, terminated);
            },
            () -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(flowScopeContext);
              stateTransitionBehavior.onElementTerminated(element, terminated);
            });
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
      final ExecutableMultiInstanceBody multiInstanceBody, final BpmnElementContext context) {
    stateTransitionBehavior.activateChildInstanceWithKey(
        context, multiInstanceBody.getInnerActivity());
  }

  private DirectBuffer wrapLoopCounter(final int loopCounter) {
    variableWriter.wrap(loopCounterVariableBuffer, 0);

    variableWriter.writeInteger(loopCounter);
    final var length = variableWriter.getOffset();

    loopCounterVariableView.wrap(loopCounterVariableBuffer, 0, length);
    return loopCounterVariableView;
  }

  private Either<Failure, Boolean> satisfiesCompletionCondition(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    final Optional<Expression> completionCondition =
        element.getLoopCharacteristics().getCompletionCondition();

    if (completionCondition.isPresent()) {
      return expressionBehavior.evaluateBooleanExpression(
          completionCondition.get(), context.getElementInstanceKey());
    }
    return Either.right(false);
  }

  private static final class OutputCollectionBehavior {

    private final MsgPackReader variableReader = new MsgPackReader();
    private final MsgPackWriter variableWriter = new MsgPackWriter();
    private final ExpandableArrayBuffer variableBuffer = new ExpandableArrayBuffer();
    private final DirectBuffer resultBuffer = new UnsafeBuffer(0, 0);

    private final BpmnStateBehavior stateBehavior;
    private final ExpressionProcessor expressionProcessor;

    private OutputCollectionBehavior(
        final BpmnStateBehavior stateBehavior, final ExpressionProcessor expressionProcessor) {
      this.stateBehavior = stateBehavior;
      this.expressionProcessor = expressionProcessor;
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
      return expressionProcessor.evaluateAnyExpression(expression, context.getElementInstanceKey());
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
}
