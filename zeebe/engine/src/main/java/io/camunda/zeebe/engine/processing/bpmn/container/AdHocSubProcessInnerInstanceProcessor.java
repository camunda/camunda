/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.util.Either;

public class AdHocSubProcessInnerInstanceProcessor
    implements BpmnElementContainerProcessor<ExecutableFlowElementContainer> {

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;

  public AdHocSubProcessInnerInstanceProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
  }

  @Override
  public Class<ExecutableFlowElementContainer> getType() {
    return ExecutableFlowElementContainer.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {
    // An inner instance is created on the activation of an element inside the ad-hoc subprocess.
    // Since the inner instance doesn't know which element, we can't use the ACTIVATE command.
    // Instead, we create the inner instance together with the element during the activation by
    // writing the activation events.
    throw new BpmnProcessingException(
        context,
        """
        An ACTIVATE command is not supported for an inner instance of an ad-hoc sub-process. \
        Instead, the inner instance should be activated by writing events.""");
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {
    return stateTransitionBehavior.transitionToCompleted(element, context);
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableFlowElementContainer element, final BpmnElementContext terminating) {
    final boolean noActiveChildInstances =
        stateTransitionBehavior.terminateChildInstances(terminating);
    if (noActiveChildInstances) {
      terminate(element, terminating);
    }
    return TransitionOutcome.CONTINUE;
  }

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext,
      final Boolean satisfiesCompletionCondition) {
    if (stateBehavior.canBeCompleted(childContext)) {
      stateTransitionBehavior.completeElement(flowScopeContext);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {
    if (stateBehavior.canBeTerminated(childContext)) {
      terminate(element, flowScopeContext);
    }
  }

  private void terminate(
      final ExecutableFlowElementContainer element, final BpmnElementContext terminating) {
    final var terminated =
        stateTransitionBehavior.transitionToTerminated(terminating, element.getEventType());
    stateTransitionBehavior.onElementTerminated(element, terminated);
  }
}
