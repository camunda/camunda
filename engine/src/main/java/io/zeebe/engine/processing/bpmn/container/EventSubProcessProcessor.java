/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.container;

import io.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.util.Either;

public final class EventSubProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableFlowElementContainer> {

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  public EventSubProcessProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
  }

  @Override
  public Class<ExecutableFlowElementContainer> getType() {
    return ExecutableFlowElementContainer.class;
  }

  @Override
  public void onActivate(
      final ExecutableFlowElementContainer element, final BpmnElementContext activating) {
    // event sub process is activated by the triggered event. No activate command is written.
    throw new BpmnProcessingException(
        activating,
        "Expected an ACTIVATING and ACTIVATED event for the event sub process but found an ACTIVATE command.");
  }

  @Override
  public void onComplete(
      final ExecutableFlowElementContainer element, final BpmnElementContext completing) {

    variableMappingBehavior
        .applyOutputMappings(completing, element)
        .ifRightOrLeft(
            ok ->
                stateTransitionBehavior.transitionToCompletedWithParentNotification(
                    element, completing),
            failure -> incidentBehavior.createIncident(failure, completing));
  }

  @Override
  public void onTerminate(
      final ExecutableFlowElementContainer element, final BpmnElementContext terminating) {

    incidentBehavior.resolveIncidents(terminating);

    final var noActiveChildInstances = stateTransitionBehavior.terminateChildInstances(terminating);
    if (noActiveChildInstances) {
      onChildTerminated(element, terminating, null);
    }
  }

  @Override
  public Either<Failure, ?> onChildActivating(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {
    if (childContext.getBpmnElementType() != BpmnElementType.START_EVENT) {
      return Either.right(null);
    }

    // The input mapping for the event sub process need to happen here, since we immediately moved
    // the event sub process to ACTIVATED on event triggering. This is done to make sure that we
    // copy temporary variables (for messages) to the correct scope already on triggering the event,
    // otherwise we will have issues on concurrent incoming events.
    return variableMappingBehavior.applyInputMappings(flowScopeContext, element);
  }

  @Override
  public void beforeExecutionPathCompleted(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {}

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {
    if (stateBehavior.canBeCompleted(childContext)) {
      stateTransitionBehavior.completeElement(flowScopeContext);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    if (childContext == null
        || (flowScopeContext.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING
            && stateBehavior.canBeTerminated(childContext))) {
      final var terminated = stateTransitionBehavior.transitionToTerminated(flowScopeContext);
      stateTransitionBehavior.onElementTerminated(element, terminated);
    }
  }
}
