/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableIntermediateThrowEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableLink;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class BpmnInclusiveGatewayBehavior {
  private final BpmnStateBehavior stateBehavior;

  public BpmnInclusiveGatewayBehavior(final BpmnStateBehavior stateBehavior) {
    this.stateBehavior = stateBehavior;
  }

  public boolean hasActivePathToTheGateway(
      final BpmnElementContext context,
      final ExecutableFlowElement inclusiveGateway,
      final ExecutableProcess process) {
    final Set<DirectBuffer> takenSequenceFlowIds = stateBehavior.getTakenSequenceFlowIds(context);
    final var gatewayElementId = inclusiveGateway.getId();
    final var activateElementIds = findActivateElementInFlowScope(context, gatewayElementId);
    final boolean hasActiveElementPath =
        activateElementIds.stream()
            .anyMatch(
                elementId ->
                    hasActivePathToTheGateway(
                        process, elementId, gatewayElementId, takenSequenceFlowIds));
    return hasActiveElementPath
        || hasActiveSequenceFlowToTheGateway(
            context, process, gatewayElementId, takenSequenceFlowIds);
  }

  private Set<DirectBuffer> findActivateElementInFlowScope(
      final BpmnElementContext context, final DirectBuffer targetElementId) {
    final var flowScopeContext = stateBehavior.getFlowScopeContext(context);
    final var childInstanceContents = stateBehavior.getChildInstanceContexts(flowScopeContext);

    // activate element in flow scope
    return childInstanceContents.stream()
        .map(BpmnElementContext::getElementId)
        .filter(elementId -> !elementId.equals(targetElementId))
        .collect(Collectors.toSet());
  }

  private boolean hasActiveSequenceFlowToTheGateway(
      final BpmnElementContext context,
      final ExecutableProcess process,
      final DirectBuffer targetElementId,
      final Set<DirectBuffer> takenSequenceFlowIds) {
    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);
    final var activeSequenceFlowIds = flowScopeInstance.getActiveSequenceFlowIds();
    // ignore pending incoming sequence flows to the gateway
    return activeSequenceFlowIds.stream()
        .anyMatch(
            flowId -> {
              final DirectBuffer elementId =
                  process.getElementById(flowId, ExecutableSequenceFlow.class).getTarget().getId();
              return !elementId.equals(targetElementId)
                  && hasActivePathToTheGateway(
                      process, elementId, targetElementId, takenSequenceFlowIds);
            });
  }

  private boolean hasActivePathToTheGateway(
      final ExecutableProcess process,
      final DirectBuffer sourceElementId,
      final DirectBuffer targetElementId,
      final Set<DirectBuffer> takenSequenceFlowIds) {
    final Set<ExecutableFlowElement> visited = new HashSet<>();
    final Deque<ExecutableFlowElement> elementsToVisit = new LinkedList<>();

    elementsToVisit.add(process.getElementById(sourceElementId));

    ExecutableFlowElement currentElement;
    while ((currentElement = elementsToVisit.poll()) != null) {
      // found path from sourceElement to targetElement
      if (currentElement.getId().equals(targetElementId)) {
        return true;
      }

      if (!visited.contains(currentElement)) {
        visited.add(currentElement);
        switch (currentElement) {
          case final ExecutableIntermediateThrowEvent throwEvent -> {
            if (throwEvent.isLinkThrowEvent()) {
              visitLinkEvent(elementsToVisit, throwEvent.getLink(), visited);
            } else {
              visitElement(elementsToVisit, throwEvent, visited, takenSequenceFlowIds);
            }
          }
          case final ExecutableFlowNode element ->
              visitElement(elementsToVisit, element, visited, takenSequenceFlowIds);
          default -> {}
        }
      }
    }
    return false;
  }

  private void visitLinkEvent(
      final Deque<ExecutableFlowElement> elementsToVisit,
      final ExecutableLink linkThrowEvent,
      final Set<ExecutableFlowElement> visited) {
    final ExecutableCatchEventElement catchEventElement = linkThrowEvent.getCatchEventElement();
    if (catchEventElement != null && !visited.contains(catchEventElement)) {
      elementsToVisit.add(catchEventElement);
    }
  }

  private void visitElement(
      final Deque<ExecutableFlowElement> elementsToVisit,
      final ExecutableFlowNode sourceElement,
      final Set<ExecutableFlowElement> visited,
      final Set<DirectBuffer> takenSequenceFlowIds) {
    elementsToVisit.addAll(
        sourceElement.getOutgoing().stream()
            // exclude paths ending on an incoming sequence flow of the gateway that was already
            // taken. All incoming flows need to be taken only once.
            .filter(sequenceFlow -> !takenSequenceFlowIds.contains(sequenceFlow.getId()))
            .map(ExecutableSequenceFlow::getTarget)
            .filter(target -> !visited.contains(target))
            .toList());

    if (sourceElement instanceof ExecutableActivity) {
      final List<ExecutableBoundaryEvent> boundaryEvents =
          ((ExecutableActivity) sourceElement).getBoundaryEvents();
      boundaryEvents.stream().filter(e -> !visited.contains(e)).forEach(elementsToVisit::add);
    }
  }
}
