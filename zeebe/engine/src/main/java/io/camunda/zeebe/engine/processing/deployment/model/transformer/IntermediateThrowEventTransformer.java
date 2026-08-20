/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCompensation;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableIntermediateThrowEvent;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Activity;
import io.camunda.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.camunda.zeebe.model.bpmn.instance.LinkEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;

public final class IntermediateThrowEventTransformer
    implements ModelElementTransformer<IntermediateThrowEvent> {

  private final JobWorkerElementTransformer<IntermediateThrowEvent> jobWorkerElementTransformer =
      new JobWorkerElementTransformer<>(IntermediateThrowEvent.class);

  @Override
  public Class<IntermediateThrowEvent> getType() {
    return IntermediateThrowEvent.class;
  }

  @Override
  public void transform(final IntermediateThrowEvent element, final TransformContext context) {
    final var process = context.getCurrentProcess();
    final var throwEvent =
        process.getElementById(element.getId(), ExecutableIntermediateThrowEvent.class);

    throwEvent.setEventType(BpmnEventType.NONE);

    if (!element.getEventDefinitions().isEmpty()) {
      transformEventDefinition(element, context, throwEvent);
    }
  }

  private void transformEventDefinition(
      final IntermediateThrowEvent element,
      final TransformContext context,
      final ExecutableIntermediateThrowEvent executableElement) {

    final var eventDefinition = element.getEventDefinitions().iterator().next();

    if (eventDefinition instanceof MessageEventDefinition) {
      executableElement.setEventType(BpmnEventType.MESSAGE);
      if (hasTaskDefinition(element)) {
        jobWorkerElementTransformer.transform(element, context);
      }

    } else if (eventDefinition instanceof final LinkEventDefinition linkEventDefinition) {
      transformLinkEventDefinition(context, executableElement, linkEventDefinition);

    } else if (eventDefinition
        instanceof final EscalationEventDefinition escalationEventDefinition) {
      transformEscalationEventDefinition(context, executableElement, escalationEventDefinition);

    } else if (eventDefinition instanceof final SignalEventDefinition signalEventDefinition) {
      transformSignalEventDefinition(context, executableElement, signalEventDefinition);

    } else if (eventDefinition
        instanceof final CompensateEventDefinition compensateEventDefinition) {
      transformCompensationEventDefinition(context, executableElement, compensateEventDefinition);
    }
  }

  private boolean hasTaskDefinition(final IntermediateThrowEvent element) {
    return element.getSingleExtensionElement(ZeebeTaskDefinition.class) != null;
  }

  private void transformLinkEventDefinition(
      final TransformContext context,
      final ExecutableIntermediateThrowEvent executableThrowEventElement,
      final LinkEventDefinition eventDefinition) {

    final var name = eventDefinition.getName();
    final var executableLink = context.getLink(name);
    executableThrowEventElement.setLink(executableLink);
    executableThrowEventElement.setEventType(BpmnEventType.LINK);
  }

  private void transformEscalationEventDefinition(
      final TransformContext context,
      final ExecutableIntermediateThrowEvent executableElement,
      final EscalationEventDefinition eventDefinition) {
    final var escalation = eventDefinition.getEscalation();

    final var executableEscalation = context.getEscalation(escalation.getId());
    executableElement.setEscalation(executableEscalation);
    executableElement.setEventType(BpmnEventType.ESCALATION);
  }

  private void transformSignalEventDefinition(
      final TransformContext context,
      final ExecutableIntermediateThrowEvent executableElement,
      final SignalEventDefinition eventDefinition) {

    final var signal = eventDefinition.getSignal();
    final var executableSignal = context.getSignal(signal.getId());
    executableElement.setSignal(executableSignal);
    executableElement.setEventType(BpmnEventType.SIGNAL);
  }

  private void transformCompensationEventDefinition(
      final TransformContext context,
      final ExecutableIntermediateThrowEvent executableElement,
      final CompensateEventDefinition eventDefinition) {

    final ExecutableCompensation compensation = new ExecutableCompensation(eventDefinition.getId());

    final Activity activityRef = eventDefinition.getActivity();
    if (activityRef != null) {
      final ExecutableActivity activity =
          context.getCurrentProcess().getElementById(activityRef.getId(), ExecutableActivity.class);
      compensation.setReferenceCompensationActivity(activity);
    }
    executableElement.setCompensation(compensation);
    executableElement.setEventType(BpmnEventType.COMPENSATION);
  }
}
