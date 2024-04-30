/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.event;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.util.Either;
import java.util.List;

public class IntermediateCatchEventProcessor
    implements BpmnElementProcessor<ExecutableCatchEventElement> {

  private final List<IntermediateCatchEventBehavior> catchEventBehaviors =
      List.of(
          new DefaultIntermediateCatchEventBehavior(), new LinkIntermediateCatchEventBehavior());

  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;

  public IntermediateCatchEventProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
  }

  @Override
  public Class<ExecutableCatchEventElement> getType() {
    return ExecutableCatchEventElement.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableCatchEventElement element, final BpmnElementContext activating) {
    return eventBehaviorOf(element).onActivate(element, activating);
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableCatchEventElement element, final BpmnElementContext completing) {
    return variableMappingBehavior
        .applyOutputMappings(completing, element)
        .flatMap(
            ok -> {
              eventSubscriptionBehavior.unsubscribeFromEvents(completing);
              return stateTransitionBehavior.transitionToCompleted(element, completing);
            })
        .thenDo(completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
  }

  @Override
  public void onTerminate(
      final ExecutableCatchEventElement element, final BpmnElementContext terminating) {
    eventSubscriptionBehavior.unsubscribeFromEvents(terminating);
    incidentBehavior.resolveIncidents(terminating);

    final var terminated =
        stateTransitionBehavior.transitionToTerminated(terminating, element.getEventType());
    stateTransitionBehavior.onElementTerminated(element, terminated);
  }

  private IntermediateCatchEventBehavior eventBehaviorOf(
      final ExecutableCatchEventElement element) {
    return catchEventBehaviors.stream()
        .filter(behavior -> behavior.isSuitableForEvent(element))
        .findFirst()
        .orElseThrow(
            () ->
                new UnsupportedOperationException(
                    "This kind of intermediate catch event is not supported."));
  }

  private interface IntermediateCatchEventBehavior {

    boolean isSuitableForEvent(final ExecutableCatchEventElement element);

    Either<Failure, ?> onActivate(
        final ExecutableCatchEventElement element, final BpmnElementContext activating);
  }

  private final class DefaultIntermediateCatchEventBehavior
      implements IntermediateCatchEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableCatchEventElement element) {
      return !element.isLink();
    }

    @Override
    public Either<Failure, ?> onActivate(
        final ExecutableCatchEventElement element, final BpmnElementContext activating) {
      return variableMappingBehavior
          .applyInputMappings(activating, element)
          .flatMap(ok -> eventSubscriptionBehavior.subscribeToEvents(element, activating))
          .thenDo(
              ok ->
                  stateTransitionBehavior.transitionToActivated(
                      activating, element.getEventType()));
    }
  }

  private final class LinkIntermediateCatchEventBehavior implements IntermediateCatchEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableCatchEventElement element) {
      return element.isLink();
    }

    @Override
    public Either<Failure, ?> onActivate(
        final ExecutableCatchEventElement element, final BpmnElementContext activating) {
      final var activated =
          stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
      stateTransitionBehavior.completeElement(activated);
      return SUCCESS;
    }
  }
}
