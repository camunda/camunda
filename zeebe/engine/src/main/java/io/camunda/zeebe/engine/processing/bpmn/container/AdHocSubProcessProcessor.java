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
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AdHocSubProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableAdHocSubProcess> {

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final ExpressionProcessor expressionProcessor;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;

  public AdHocSubProcessProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    jobBehavior = bpmnBehaviors.jobBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    expressionProcessor = bpmnBehaviors.expressionBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
    this.stateTransitionBehavior = stateTransitionBehavior;
  }

  @Override
  public Class<ExecutableAdHocSubProcess> getType() {
    return ExecutableAdHocSubProcess.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {
    return variableMappingBehavior.applyInputMappings(context, element);
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {

    return readActivateElementsCollection(element, context)
        .flatMap(
            elementsToActivate ->
                eventSubscriptionBehavior
                    .subscribeToEvents(element, context)
                    .map(ok -> elementsToActivate))
        .thenDo(
            elementsToActivate -> {
              final var activated =
                  stateTransitionBehavior.transitionToActivated(context, element.getEventType());

              activateElements(element, activated, elementsToActivate);
            });
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {
    return variableMappingBehavior
        .applyOutputMappings(context, element)
        .thenDo(ok -> eventSubscriptionBehavior.unsubscribeFromEvents(context));
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {
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
  public void onTerminate(
      final ExecutableAdHocSubProcess element, final BpmnElementContext terminating) {

    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(terminating);
    }
    eventSubscriptionBehavior.unsubscribeFromEvents(terminating);
    incidentBehavior.resolveIncidents(terminating);
    compensationSubscriptionBehaviour.deleteSubscriptionsOfSubprocess(terminating);

    final boolean noActiveChildInstances =
        stateTransitionBehavior.terminateChildInstances(terminating);
    if (noActiveChildInstances) {
      terminate(element, terminating);
    }
  }

  private void terminate(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {

    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);

    eventSubscriptionBehavior
        .findEventTrigger(context)
        .filter(eventTrigger -> flowScopeInstance.isActive() && !flowScopeInstance.isInterrupted())
        .ifPresentOrElse(
            eventTrigger -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(context, element.getEventType());

              eventSubscriptionBehavior.activateTriggeredEvent(
                  terminated.getElementInstanceKey(),
                  terminated.getFlowScopeKey(),
                  eventTrigger,
                  terminated);
            },
            () -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(context, element.getEventType());

              stateTransitionBehavior.onElementTerminated(element, terminated);
            });
  }

  private Either<Failure, List<String>> readActivateElementsCollection(
      final ExecutableAdHocSubProcess adHocSubProcess, final BpmnElementContext context) {
    final Expression activeElementsCollection = adHocSubProcess.getActiveElementsCollection();
    if (activeElementsCollection == null) {
      // The expression is not defined. No elements to activate.
      return Either.right(Collections.emptyList());

    } else {
      return expressionProcessor
          .evaluateArrayOfStringsExpression(
              activeElementsCollection, context.getElementInstanceKey())
          .mapLeft(
              failure ->
                  new Failure(
                      "Failed to activate ad-hoc elements. " + failure.getMessage(),
                      ErrorType.EXTRACT_VALUE_ERROR))
          .flatMap(elements -> validateActiveElements(adHocSubProcess, elements));
    }
  }

  private static Either<Failure, List<String>> validateActiveElements(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final List<String> activateElementsCollection) {

    final List<String> elementsNotFound =
        activateElementsCollection.stream()
            .filter(elementId -> !adHocSubProcess.getAdHocActivitiesById().containsKey(elementId))
            .toList();

    if (elementsNotFound.isEmpty()) {
      return Either.right(activateElementsCollection);

    } else {
      final String elementIds =
          elementsNotFound.stream().map("'%s'"::formatted).collect(Collectors.joining(", "));
      return Either.left(
          new Failure(
              "Failed to activate ad-hoc elements. No BPMN elements found with ids: %s."
                  .formatted(elementIds),
              ErrorType.EXTRACT_VALUE_ERROR));
    }
  }

  private void activateElements(
      final ExecutableAdHocSubProcess element,
      final BpmnElementContext context,
      final List<String> elementsToActivate) {

    elementsToActivate.stream()
        .map(element.getAdHocActivitiesById()::get)
        .forEach(
            elementToActivate ->
                stateTransitionBehavior.activateChildInstance(context, elementToActivate));
  }

  @Override
  public Either<Failure, ?> beforeExecutionPathCompleted(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final BpmnElementContext adHocSubProcessContext,
      final BpmnElementContext childContext) {
    final Expression completionConditionExpression = adHocSubProcess.getCompletionCondition();
    if (completionConditionExpression == null) {
      return Either.right(null);
    }

    return expressionProcessor
        .evaluateBooleanExpression(
            completionConditionExpression, adHocSubProcessContext.getElementInstanceKey())
        .mapLeft(
            failure ->
                new Failure(
                    "Failed to evaluate completion condition. " + failure.getMessage(),
                    ErrorType.EXTRACT_VALUE_ERROR));
  }

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final BpmnElementContext adHocSubProcessContext,
      final BpmnElementContext childContext,
      final Boolean satisfiesCompletionCondition) {
    if (satisfiesCompletionCondition == null) {
      // completion condition is not set - complete the ad-hoc subprocess if possible (no other
      // activity is active), otherwise skip completion as the same block will be evaluated when
      // the next activity is completed
      if (stateBehavior.canBeCompleted(childContext)) {
        stateTransitionBehavior.completeElement(adHocSubProcessContext);
      }

      return;
    }

    if (satisfiesCompletionCondition) {
      if (adHocSubProcess.isCancelRemainingInstances()) {
        // terminate all remaining child instances & directly complete ad-hoc subprocess if there
        // is no child activity left - otherwise see onChildTerminated
        final boolean hasNoActiveChildren =
            stateTransitionBehavior.terminateChildInstances(adHocSubProcessContext);
        if (hasNoActiveChildren) {
          stateTransitionBehavior.completeElement(adHocSubProcessContext);
        }
      } else {
        // complete ad-hoc subprocess if possible, otherwise skip completion as the same block
        // will be evaluated when the next activity is completed
        if (stateBehavior.canBeCompleted(childContext)) {
          stateTransitionBehavior.completeElement(adHocSubProcessContext);
        }
      }
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final BpmnElementContext adHocSubProcessContext,
      final BpmnElementContext childContext) {
    if (adHocSubProcessContext.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING) {
      // child termination is initiated in onTerminate
      // terminate ad-hoc subprocess as soon as all child instances have been terminated
      if (stateBehavior.canBeTerminated(childContext)) {
        terminate(adHocSubProcess, adHocSubProcessContext);
      }
    } else if (stateBehavior.canBeCompleted(childContext)) {
      // complete the ad-hoc subprocess because its completion condition was met previously and
      // all remaining child instances were terminated.
      stateTransitionBehavior.completeElement(adHocSubProcessContext);
    }
  }
}
