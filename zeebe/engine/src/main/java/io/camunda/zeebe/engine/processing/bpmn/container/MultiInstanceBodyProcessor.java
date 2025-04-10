/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.MultiInstanceOutputCollectionBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MultiInstanceBodyProcessor
    implements BpmnElementContainerProcessor<ExecutableMultiInstanceBody> {

  private static final DirectBuffer NIL_VALUE = new UnsafeBuffer(MsgPackHelper.NIL);
  private static final DirectBuffer LOOP_COUNTER_VARIABLE = BufferUtil.wrapString("loopCounter");

  private final MutableDirectBuffer loopCounterVariableBuffer =
      new UnsafeBuffer(new byte[Long.BYTES + 1]);
  private final MutableDirectBuffer numberOfInstancesVariableBuffer =
      new UnsafeBuffer(new byte[Long.BYTES + 1]);
  private final MutableDirectBuffer numberOfActiveInstancesVariableBuffer =
      new UnsafeBuffer(new byte[Long.BYTES + 1]);
  private final MutableDirectBuffer numberOfCompletedInstancesVariableBuffer =
      new UnsafeBuffer(new byte[Long.BYTES + 1]);
  private final MutableDirectBuffer numberOfTerminatedInstancesVariableBuffer =
      new UnsafeBuffer(new byte[Long.BYTES + 1]);

  private final DirectBuffer loopCounterVariableView = new UnsafeBuffer(0, 0);
  private final DirectBuffer numberOfInstancesVariableView = new UnsafeBuffer(0, 0);
  private final DirectBuffer numberOfActiveInstancesVariableView = new UnsafeBuffer(0, 0);
  private final DirectBuffer numberOfCompletedInstancesVariableView = new UnsafeBuffer(0, 0);
  private final DirectBuffer numberOfTerminatedInstancesVariableView = new UnsafeBuffer(0, 0);

  private final MsgPackWriter variableWriter = new MsgPackWriter();

  private final ExpressionProcessor expressionBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final MultiInstanceOutputCollectionBehavior multiInstanceOutputCollectionBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;

  public MultiInstanceBodyProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    this.stateTransitionBehavior = stateTransitionBehavior;
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    expressionBehavior = bpmnBehaviors.expressionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    multiInstanceOutputCollectionBehavior = bpmnBehaviors.outputCollectionBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
  }

  @Override
  public Class<ExecutableMultiInstanceBody> getType() {
    return ExecutableMultiInstanceBody.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    // verify that the input collection variable is present and valid
    return readInputCollectionVariable(element, context)
        .flatMap(
            inputCollection ->
                eventSubscriptionBehavior
                    .subscribeToEvents(element, context)
                    .map(ok -> inputCollection))
        .thenDo(inputCollection -> activate(element, context, inputCollection));
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);

    element
        .getLoopCharacteristics()
        .getOutputCollection()
        .ifPresent(variableName -> stateBehavior.propagateVariable(context, variableName));

    compensationSubscriptionBehaviour.createCompensationSubscription(element, context);

    return stateTransitionBehavior
        .transitionToCompleted(element, context)
        .thenDo(
            completed -> {
              compensationSubscriptionBehaviour.completeCompensationHandler(completed);
              stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed);
            });
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);

    final var noActiveChildInstances = stateTransitionBehavior.terminateChildInstances(context);
    if (noActiveChildInstances) {
      terminate(element, context);
    }
    return TransitionOutcome.CONTINUE;
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
        multiInstanceOutputCollectionBehavior.updateOutputCollection(
            element, childContext, flowScopeContext);
    if (updatedOrFailure.isLeft()) {
      return updatedOrFailure;
    }

    // test that completion condition can be evaluated correctly
    final Either<Failure, Boolean> satisfiesCompletionConditionOrFailure =
        satisfiesCompletionCondition(element, childContext);
    if (satisfiesCompletionConditionOrFailure.isLeft()) {
      return satisfiesCompletionConditionOrFailure;
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
    final var inputCollectionOrFailure = readInputCollectionVariable(element, flowScopeContext);
    if (inputCollectionOrFailure.isLeft()) {
      // this incident is un-resolvable
      incidentBehavior.createIncident(inputCollectionOrFailure.getLeft(), childContext);
      return;
    }

    final ElementInstance multiInstanceElementInstance =
        stateBehavior.getElementInstance(flowScopeContext);

    if (loopCharacteristics.isSequential()) {
      final var inputCollection = inputCollectionOrFailure.get();
      final var loopCounter = multiInstanceElementInstance.getMultiInstanceLoopCounter();

      if (loopCounter < inputCollection.size()) {
        createInnerInstance(element, flowScopeContext);

        // canBeCompleted() doesn't take the created child instance into account because
        // it wrote just a ACTIVATE command that create no new instance immediately
        childInstanceCreated = true;
      }
    }

    if (!childInstanceCreated && stateBehavior.canBeCompleted(childContext)) {
      final int inputCollectionSize = inputCollectionOrFailure.get().size();
      if (isAllChildrenHasCompletedOrTerminated(
          multiInstanceElementInstance, inputCollectionSize)) {
        stateTransitionBehavior.completeElement(flowScopeContext);
      }
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

  private static boolean isAllChildrenHasCompletedOrTerminated(
      final ElementInstance multiInstanceElementInstance, final int inputCollectionSize) {
    final int completedOrTerminatedChildren =
        multiInstanceElementInstance.getNumberOfCompletedElementInstances()
            + multiInstanceElementInstance.getNumberOfTerminatedElementInstances();
    return inputCollectionSize == completedOrTerminatedChildren;
  }

  private void activate(
      final ExecutableMultiInstanceBody element,
      final BpmnElementContext context,
      final List<DirectBuffer> inputCollection) {
    final BpmnElementContext activated =
        stateTransitionBehavior.transitionToActivated(context, element.getEventType());
    final var loopCharacteristics = element.getLoopCharacteristics();
    loopCharacteristics
        .getOutputCollection()
        .ifPresent(
            variableName ->
                multiInstanceOutputCollectionBehavior.initializeOutputCollection(
                    activated, variableName, inputCollection.size()));

    if (inputCollection.isEmpty()) {
      // complete the multi-instance body immediately
      stateTransitionBehavior.completeElement(activated);
      return;
    }

    if (loopCharacteristics.isSequential()) {
      createInnerInstance(element, activated);
    } else {
      stateTransitionBehavior.activateChildInstancesInBatches(context, inputCollection.size());
    }
  }

  private void terminate(
      final ExecutableMultiInstanceBody element, final BpmnElementContext flowScopeContext) {

    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(flowScopeContext);

    incidentBehavior.resolveIncidents(flowScopeContext);

    eventSubscriptionBehavior
        .findEventTrigger(flowScopeContext)
        .filter(eventTrigger -> flowScopeInstance.isActive())
        .filter(eventTrigger -> !flowScopeInstance.isInterrupted())
        .ifPresentOrElse(
            eventTrigger -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(
                      flowScopeContext, element.getEventType());
              eventSubscriptionBehavior.activateTriggeredEvent(
                  flowScopeContext.getElementInstanceKey(),
                  terminated.getFlowScopeKey(),
                  eventTrigger,
                  terminated);
              stateTransitionBehavior.onElementTerminated(element, terminated);
            },
            () -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(
                      flowScopeContext, element.getEventType());
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
    // variable need to be initialised with a nil-value. This makes sure that the variable exists at
    // the local scope.
    //
    // We can make an exception for output expressions that refer to the same variable as the input
    // element, because the input variable is already created at the local scope.
    //
    // Likewise, we can make the same exception for the `loopCounter` variable.
    loopCharacteristics
        .getOutputElement()
        .flatMap(Expression::getVariableName)
        .map(BufferUtil::wrapString)
        .filter(
            output ->
                loopCharacteristics
                    .getInputElement()
                    .map(input -> !BufferUtil.equals(input, output))
                    .orElse(true))
        .filter(output -> !BufferUtil.equals(output, LOOP_COUNTER_VARIABLE))
        .ifPresent(
            variableName -> stateBehavior.setLocalVariable(childContext, variableName, NIL_VALUE));

    stateBehavior.setLocalVariable(
        childContext,
        LOOP_COUNTER_VARIABLE,
        wrapVariable(loopCounterVariableBuffer, loopCounterVariableView, loopCounter));
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

  private DirectBuffer wrapVariable(
      final MutableDirectBuffer variableBuffer, final DirectBuffer variableView, final long value) {
    variableWriter.wrap(variableBuffer, 0);

    variableWriter.writeInteger(value);
    final var length = variableWriter.getOffset();

    variableView.wrap(variableBuffer, 0, length);
    return variableView;
  }

  private Either<Failure, Boolean> satisfiesCompletionCondition(
      final ExecutableMultiInstanceBody element, final BpmnElementContext context) {
    final Optional<Expression> completionCondition =
        element.getLoopCharacteristics().getCompletionCondition();

    final ExpressionProcessor primaryContextExpressionProcessor =
        expressionBehavior.withPrimaryContext(
            (variableName -> getVariable(context.getFlowScopeKey(), variableName)));
    if (completionCondition.isPresent()) {
      return primaryContextExpressionProcessor.evaluateBooleanExpression(
          completionCondition.get(), context.getElementInstanceKey());
    }
    return Either.right(false);
  }

  private DirectBuffer getVariable(final long elementInstanceKey, final String variableName) {
    return switch (variableName) {
      case "numberOfInstances" -> getNumberOfInstancesVariable(elementInstanceKey);

      case "numberOfActiveInstances" -> getNumberOfActiveInstancesVariable(elementInstanceKey);

      case "numberOfCompletedInstances" ->
          getNumberOfCompletedInstancesVariable(elementInstanceKey);

      case "numberOfTerminatedInstances" ->
          getNumberOfTerminatedInstancesVariable(elementInstanceKey);

      default -> null;
    };
  }

  private DirectBuffer getNumberOfInstancesVariable(final long elementInstanceKey) {
    return wrapVariable(
        numberOfInstancesVariableBuffer,
        numberOfInstancesVariableView,
        stateBehavior.getElementInstance(elementInstanceKey).getNumberOfElementInstances());
  }

  private DirectBuffer getNumberOfActiveInstancesVariable(final long elementInstanceKey) {
    // The getNumberOfActiveInstancesVariable method is called while the child instance is
    // completing, but the active element instances value has not yet been decremented,
    // which is why this variable has to be lowered by 1
    final int numberOfActiveInstances =
        stateBehavior.getElementInstance(elementInstanceKey).getNumberOfActiveElementInstances()
            - 1;
    return wrapVariable(
        numberOfActiveInstancesVariableBuffer,
        numberOfActiveInstancesVariableView,
        numberOfActiveInstances);
  }

  private DirectBuffer getNumberOfCompletedInstancesVariable(final long elementInstanceKey) {
    // The getNumberOfCompletedInstancesVariable method is called while the child instance is
    // completing, but the completed element instances value has not yet been incremented,
    // which is why this variable has to be incremented by 1
    final int numberOfCompletedInstances =
        stateBehavior.getElementInstance(elementInstanceKey).getNumberOfCompletedElementInstances()
            + 1;
    return wrapVariable(
        numberOfCompletedInstancesVariableBuffer,
        numberOfCompletedInstancesVariableView,
        numberOfCompletedInstances);
  }

  private DirectBuffer getNumberOfTerminatedInstancesVariable(final long elementInstanceKey) {
    final int numberOfTerminatedInstances =
        stateBehavior
            .getElementInstance(elementInstanceKey)
            .getNumberOfTerminatedElementInstances();
    return wrapVariable(
        numberOfTerminatedInstancesVariableBuffer,
        numberOfTerminatedInstancesVariableView,
        numberOfTerminatedInstances);
  }
}
