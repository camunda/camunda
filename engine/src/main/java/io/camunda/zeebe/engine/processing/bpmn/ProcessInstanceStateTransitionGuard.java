/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.Activity;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Checks the preconditions of a state transition command.
 *
 * <p>A process instance can be have concurrent state transitions if a user command is received
 * (e.g. cancel process instance) or if an internal/external event is triggered (e.g. timer boundary
 * event). In this case, the current process instance processing needs to be interrupted be avoid an
 * inconsistent state.
 */
public final class ProcessInstanceStateTransitionGuard {

  private final BpmnStateBehavior stateBehavior;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;

  public ProcessInstanceStateTransitionGuard(
      final BpmnStateBehavior stateBehavior,
      final ElementInstanceState elementInstanceState,
      final MutableProcessState processState) {
    this.stateBehavior = stateBehavior;
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
  }

  /**
   * Checks if the preconditions of the given command are met.
   *
   * @return {@code true} if the preconditions are met and the transition command is valid.
   */
  public Either<Failure, ?> isValidStateTransition(
      final BpmnElementContext context, final ExecutableFlowElement element) {
    return checkStateTransition(context, element).mapLeft(Failure::new);
  }

  private Either<String, ?> checkStateTransition(
      final BpmnElementContext context, final ExecutableFlowElement element) {
    return switch (context.getIntent()) {
      case ACTIVATE_ELEMENT -> hasActiveFlowScopeInstance(context)
          .flatMap(ok -> canActivateParallelGateway(context, element))
          .flatMap(ok -> canActivateInclusiveGateway(context, element));
      case COMPLETE_ELEMENT ->
      // an incident is resolved by writing a COMPLETE command when the element instance is in
      // state COMPLETING
      hasElementInstanceWithState(
              context,
              ProcessInstanceIntent.ELEMENT_ACTIVATED,
              ProcessInstanceIntent.ELEMENT_COMPLETING)
          .flatMap(ok -> hasActiveFlowScopeInstance(context));
      case TERMINATE_ELEMENT -> hasElementInstanceWithState(
          context,
          ProcessInstanceIntent.ELEMENT_ACTIVATING,
          ProcessInstanceIntent.ELEMENT_ACTIVATED,
          ProcessInstanceIntent.ELEMENT_COMPLETING);
      default -> Either.left(
          String.format(
              "Expected the check of the preconditions of a command with intent [activate,complete,terminate] but the intent was '%s'",
              context.getIntent()));
    };
  }

  private Either<String, ElementInstance> getElementInstance(final BpmnElementContext context) {
    final var elementInstance = stateBehavior.getElementInstance(context);
    if (elementInstance != null) {
      return Either.right(elementInstance);

    } else {
      return Either.left(
          String.format(
              "Expected element instance with key '%d' to be present in state but not found.",
              context.getElementInstanceKey()));
    }
  }

  private Either<String, ElementInstance> getFlowScopeInstance(final BpmnElementContext context) {
    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);
    if (flowScopeInstance != null) {
      return Either.right(flowScopeInstance);

    } else {
      return Either.left(
          String.format(
              "Expected flow scope instance with key '%d' to be present in state but not found.",
              context.getFlowScopeKey()));
    }
  }

  private Either<String, ElementInstance> hasElementInstanceInState(
      final ElementInstance elementInstance,
      final ProcessInstanceIntent expectedState,
      final ProcessInstanceIntent... otherExpected) {
    final var currentState = elementInstance.getState();
    if (expectedState != currentState && !Arrays.asList(otherExpected).contains(currentState)) {
      return Either.left(
          String.format(
              "Expected element instance to be in state '%s' or one of '%s' but was '%s'.",
              expectedState, Arrays.toString(otherExpected), currentState));
    } else {
      return Either.right(elementInstance);
    }
  }

  private Either<String, ?> hasElementInstanceWithState(
      final BpmnElementContext context,
      final ProcessInstanceIntent expectedState,
      final ProcessInstanceIntent... otherExpected) {
    // a shortcut to improve readability
    return getElementInstance(context)
        .flatMap(
            elementInstance ->
                hasElementInstanceInState(elementInstance, expectedState, otherExpected));
  }

  private Either<String, ElementInstance> hasFlowScopeInstanceInState(
      final ElementInstance flowScopeInstance, final ProcessInstanceIntent expectedState) {
    final var currentState = flowScopeInstance.getState();
    if (currentState != expectedState) {
      return Either.left(
          String.format(
              "Expected flow scope instance to be in state '%s' but was '%s'.",
              expectedState, currentState));

    } else {
      return Either.right(flowScopeInstance);
    }
  }

  private Either<String, ElementInstance> hasNonInterruptedFlowScope(
      final ElementInstance flowScopeInstance, final BpmnElementContext context) {
    final var interruptingElementId = flowScopeInstance.getInterruptingElementId();
    if (flowScopeInstance.isInterrupted()
        && !interruptingElementId.equals(context.getElementId())) {
      return Either.left(
          String.format(
              "Expected flow scope instance to be not interrupted but was interrupted by an event with id '%s'.",
              BufferUtil.bufferAsString(interruptingElementId)));

    } else {
      return Either.right(flowScopeInstance);
    }
  }

  private Either<String, ?> hasActiveFlowScopeInstance(final BpmnElementContext context) {
    // a shortcut to improve readability
    if (context.getBpmnElementType() == BpmnElementType.PROCESS) {
      // a process has no flow scope instance
      return Either.right(null);

    } else {
      return getFlowScopeInstance(context)
          .flatMap(
              flowScopeInstance ->
                  hasFlowScopeInstanceInState(
                      flowScopeInstance, ProcessInstanceIntent.ELEMENT_ACTIVATED))
          .flatMap(flowScopeInstance -> hasNonInterruptedFlowScope(flowScopeInstance, context));
    }
  }

  private Either<String, ?> canActivateParallelGateway(
      final BpmnElementContext context, final ExecutableFlowElement executableFlowElement) {
    if (context.getBpmnElementType() != BpmnElementType.PARALLEL_GATEWAY) {
      return Either.right(null);
    } else {
      final var element = (ExecutableFlowNode) executableFlowElement;
      final int numberOfIncomingSequenceFlows = element.getIncoming().size();
      final int numberOfTakenSequenceFlows =
          stateBehavior.getNumberOfTakenSequenceFlows(context.getFlowScopeKey(), element.getId());
      return numberOfTakenSequenceFlows >= numberOfIncomingSequenceFlows
          ? Either.right(null)
          : Either.left(
              String.format(
                  "Expected to be able to activate parallel gateway '%s',"
                      + " but not all sequence flows have been taken.",
                  BufferUtil.bufferAsString(element.getId())));
    }
  }

  private Either<String, ?> canActivateInclusiveGateway(
      final BpmnElementContext context, final ExecutableFlowElement executableFlowElement) {
    if (context.getBpmnElementType() != BpmnElementType.INCLUSIVE_GATEWAY) {
      return Either.right(null);
    } else {
      final var element = (ExecutableFlowNode) executableFlowElement;
      final int numberOfIncomingSequenceFlows = element.getIncoming().size();

      final int numberOfTakenSequenceFlows =
          stateBehavior.getNumberOfTakenSequenceFlows(context.getFlowScopeKey(), element.getId());

      final int numberOfActiveElementInstances =
          elementInstanceState
              .getInstance(context.getFlowScopeKey())
              .getNumberOfActiveElementInstances();

      final List<ElementInstance> instanceList =
          elementInstanceState.getChildren(context.getFlowScopeKey());

      /**
       * begin check if the join inclusive gateway is behind the exclusive gateway and the boundary
       * event
       * https://forum.camunda.io/t/tokens-evaluation-in-inclusive-gateway-at-join-process/9354/12
       * https://forum.camunda.io/t/tokens-evaluation-in-inclusive-gateway-at-join-process/9354/14
       */
      if (numberOfTakenSequenceFlows <= numberOfIncomingSequenceFlows
          && numberOfActiveElementInstances >= 1
          && instanceList.size() >= 1) {

        final boolean exist = doesPathExist(instanceList, element);

        if (exist) {
          return Either.right(null);
        }
      }
      // end check

      // others
      return numberOfTakenSequenceFlows >= numberOfIncomingSequenceFlows
              || (numberOfTakenSequenceFlows < numberOfIncomingSequenceFlows
                  && numberOfActiveElementInstances == 0
                  && numberOfTakenSequenceFlows > 0)
          ? Either.right(null)
          : Either.left(
              String.format(
                  "Expected to be able to activate inclusive gateway '%s',"
                      + " but not all sequence flows have been taken.",
                  BufferUtil.bufferAsString(element.getId())));
    }
  }

  private boolean doesPathExist(
      final List<ElementInstance> instanceList, final ExecutableFlowNode toElement) {
    final boolean doesExistPath = false;
    int exclusiveCount = 0;
    int checkedExclusiveCount = 0;

    int boundaryCount = 0;
    int checkedBoundaryCount = 0;

    for (final ElementInstance instance : instanceList) {
      // get the executableFlowNode of the instance
      final var fromElement =
          processState.getFlowElement(
              instance.getValue().getProcessDefinitionKey(),
              instance.getValue().getElementIdBuffer(),
              ExecutableFlowNode.class);

      // get the incoming sequence flows of the instance which is linked to the exclusive gateway
      final List<ExecutableSequenceFlow> exclusiveSequences =
          fromElement.getIncoming().stream()
              .filter(
                  x ->
                      x.getSource().getElementType() == BpmnElementType.EXCLUSIVE_GATEWAY
                          && x.getTarget().getId().equals(fromElement.getId()))
              .toList();

      for (final ExecutableSequenceFlow sequenceFlow : exclusiveSequences) {
        exclusiveCount++;
        for (final ExecutableSequenceFlow outSequenceFlow :
            sequenceFlow.getSource().getOutgoing()) {
          if (!outSequenceFlow.getTarget().getId().equals(fromElement.getId())) {
            if (checkPathExist(outSequenceFlow, toElement)) {
              checkedExclusiveCount++;
            }
          }
        }
      }

      // get the incoming sequence flows of the instance which is linked to the boundary event
      final List<ExecutableSequenceFlow> boundarySequenceFlows =
          fromElement.getIncoming().stream()
              .filter(x -> x.getSource().getElementType() == BpmnElementType.BOUNDARY_EVENT)
              .toList();

      for (final ExecutableSequenceFlow boundarySequenceFlow : boundarySequenceFlows) {
        boundaryCount++;

        // get the activity of which the Boundary Event is attached to
        final var processResource =
            processState
                .getProcessByKey(instance.getValue().getProcessDefinitionKey())
                .getResource();
        // load the model of the boundary event to get the attached to activity
        final InputStream inputStream =
            new ByteArrayInputStream(BufferUtil.bufferAsArray(processResource));
        final BoundaryEvent event =
            Bpmn.readModelFromStream(inputStream)
                .getModelElementById(
                    BufferUtil.bufferAsString(boundarySequenceFlow.getSource().getId()));
        // the attached to activity
        final Activity activity = event.getAttachedTo();

        for (final SequenceFlow outSequenceFlow : activity.getOutgoing()) {
          if (checkPathExist(outSequenceFlow, toElement)) {
            checkedBoundaryCount++;
          }
        }
      }
    }

    // only exist exclusive gateway
    if (exclusiveCount == instanceList.size()
        && exclusiveCount == checkedExclusiveCount
        && boundaryCount == 0) {
      return true;
    }
    // only exist boundary event
    if (boundaryCount == instanceList.size()
        && boundaryCount == checkedBoundaryCount
        && exclusiveCount == 0) {
      return true;
    }
    // both exist exclusive gateway and boundary event
    if (exclusiveCount + boundaryCount == instanceList.size()
        && exclusiveCount == checkedExclusiveCount
        && boundaryCount == checkedBoundaryCount
        && boundaryCount > 0
        && exclusiveCount > 0) {
      return true;
    }

    return doesExistPath;
  }

  private boolean checkPathExist(
      final SequenceFlow sequenceFlow, final ExecutableFlowNode element) {
    boolean pathExist = false;

    if (sequenceFlow.getTarget().getId().equals(BufferUtil.bufferAsString(element.getId()))) {
      return true;
    }

    final FlowNode target = sequenceFlow.getTarget();
    for (final SequenceFlow nextSequenceFlow : target.getOutgoing()) {
      pathExist = checkPathExist(nextSequenceFlow, element);
    }

    return pathExist;
  }

  private boolean checkPathExist(
      final ExecutableSequenceFlow sequenceFlow, final ExecutableFlowNode element) {
    boolean pathExist = false;

    if (sequenceFlow.getTarget().getId().equals(element.getId())) {
      return true;
    }

    final ExecutableFlowNode target = sequenceFlow.getTarget();
    for (final ExecutableSequenceFlow nextSequenceFlow : target.getOutgoing()) {
      pathExist = checkPathExist(nextSequenceFlow, element);
    }

    return pathExist;
  }
}
