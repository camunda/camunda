/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnInclusiveGatewayBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import java.util.Optional;

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
  private final BpmnInclusiveGatewayBehavior inclusiveGatewayBehavior;

  public ProcessInstanceStateTransitionGuard(final BpmnStateBehavior stateBehavior) {
    this.stateBehavior = stateBehavior;
    inclusiveGatewayBehavior = new BpmnInclusiveGatewayBehavior(stateBehavior);
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
      case ACTIVATE_ELEMENT ->
          hasActiveFlowScopeInstance(context)
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
      case TERMINATE_ELEMENT ->
          hasElementInstanceWithState(
              context,
              ProcessInstanceIntent.ELEMENT_ACTIVATING,
              ProcessInstanceIntent.ELEMENT_ACTIVATED,
              ProcessInstanceIntent.ELEMENT_COMPLETING);
      case CONTINUE_TERMINATING_ELEMENT ->
          hasElementInstanceWithState(context, ProcessInstanceIntent.ELEMENT_TERMINATING);
      case COMPLETE_EXECUTION_LISTENER ->
          hasElementInstanceWithState(
              context,
              ProcessInstanceIntent.ELEMENT_ACTIVATING,
              ProcessInstanceIntent.ELEMENT_COMPLETING);
      default ->
          Either.left(
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

  private Either<String, ElementInstance> getCallActivityInstance(
      final BpmnElementContext context) {
    final var callActivityInstanceKey = context.getParentElementInstanceKey();
    final var callActivityInstance = stateBehavior.getElementInstance(callActivityInstanceKey);
    if (callActivityInstance != null) {
      return Either.right(callActivityInstance);
    } else {
      return Either.left(
          String.format(
              "Expected call activity instance with key '%d' to be present in state but not found.",
              callActivityInstanceKey));
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
    if (context.getBpmnElementType() != BpmnElementType.PROCESS) {
      return getFlowScopeInstance(context)
          .flatMap(
              flowScopeInstance ->
                  hasFlowScopeInstanceInState(
                      flowScopeInstance, ProcessInstanceIntent.ELEMENT_ACTIVATED))
          .flatMap(flowScopeInstance -> hasNonInterruptedFlowScope(flowScopeInstance, context));

    } else if (context.getParentProcessInstanceKey() > 0) {
      // a child process has a call activity instance (parentElementInstance) as special flow scope
      return getCallActivityInstance(context)
          .flatMap(
              callActivityInstance ->
                  hasElementInstanceInState(
                      callActivityInstance, ProcessInstanceIntent.ELEMENT_ACTIVATED))
          .flatMap(
              callActivityInstance -> hasNonInterruptedFlowScope(callActivityInstance, context));

    } else {
      // a root process has no flow scope instance
      return Either.right(null);
    }
  }

  private Either<String, ?> canActivateParallelGateway(
      final BpmnElementContext context, final ExecutableFlowElement executableFlowElement) {
    if (context.getBpmnElementType() != BpmnElementType.PARALLEL_GATEWAY) {
      return Either.right(null);
    }

    // Accept after incident resolved
    if (hasElementInstanceWithState(context, ProcessInstanceIntent.ELEMENT_ACTIVATING).isRight()) {
      return Either.right(null);
    }

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

  private Either<String, ?> canActivateInclusiveGateway(
      final BpmnElementContext context, final ExecutableFlowElement executableFlowElement) {
    if (context.getBpmnElementType() != BpmnElementType.INCLUSIVE_GATEWAY) {
      return Either.right(null);
    }

    final var element = (ExecutableFlowNode) executableFlowElement;
    final int numberOfIncomingSequenceFlows = element.getIncoming().size();
    final int numberOfTakenSequenceFlows =
        stateBehavior.getNumberOfTakenSequenceFlows(context.getFlowScopeKey(), element.getId());

    // Accept after incident resolved
    if (hasElementInstanceWithState(context, ProcessInstanceIntent.ELEMENT_ACTIVATING).isRight()) {
      return Either.right(null);
    }

    // Accept if all incoming sequence flows were taken at least once
    if (numberOfTakenSequenceFlows >= numberOfIncomingSequenceFlows) {
      return Either.right(null);
    } else if (numberOfTakenSequenceFlows == 0) {
      return Either.left(
          String.format(
              "Expected to be able to activate inclusive gateway '%s',"
                  + " but the inclusive gateway way already activated.",
              BufferUtil.bufferAsString(element.getId())));
    }

    final Optional<DeployedProcess> deployedProcess =
        stateBehavior.getProcess(context.getProcessDefinitionKey(), context.getTenantId());

    if (deployedProcess.isEmpty()) {
      return Either.left(
          String.format(
              "Expected to find a deployed process for process definition key '%d', but none found.",
              context.getProcessDefinitionKey()));
    }

    if (inclusiveGatewayBehavior.hasActivePathToTheGateway(
        context, element, deployedProcess.get().getProcess())) {
      return Either.left(
          String.format(
              "Expected to be able to activate inclusive gateway '%s',"
                  + " but not all satisfied sequence flows have been taken.",
              BufferUtil.bufferAsString(element.getId())));
    }

    return Either.right(null);
  }
}
