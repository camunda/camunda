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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Activity;
import io.camunda.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.TerminateEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;

public final class EndEventTransformer implements ModelElementTransformer<EndEvent> {

  private final JobWorkerElementTransformer<EndEvent> jobWorkerElementTransformer =
      new JobWorkerElementTransformer<>(EndEvent.class);

  @Override
  public Class<EndEvent> getType() {
    return EndEvent.class;
  }

  @Override
  public void transform(final EndEvent element, final TransformContext context) {
    final var currentProcess = context.getCurrentProcess();
    final var endEvent = currentProcess.getElementById(element.getId(), ExecutableEndEvent.class);

    endEvent.setEventType(BpmnEventType.NONE);

    if (!element.getEventDefinitions().isEmpty()) {
      transformEventDefinition(element, context, endEvent);
    }
  }

  private void transformEventDefinition(
      final EndEvent element,
      final TransformContext context,
      final ExecutableEndEvent executableElement) {

    final var eventDefinition = element.getEventDefinitions().iterator().next();

    if (eventDefinition instanceof MessageEventDefinition) {
      executableElement.setEventType(BpmnEventType.MESSAGE);
      if (hasTaskDefinition(element)) {
        jobWorkerElementTransformer.transform(element, context);
      }

    } else if (eventDefinition instanceof final ErrorEventDefinition errorEventDefinition) {
      transformErrorEventDefinition(context, executableElement, errorEventDefinition);

    } else if (eventDefinition instanceof TerminateEventDefinition) {
      executableElement.setTerminateEndEvent(true);
      executableElement.setEventType(BpmnEventType.TERMINATE);

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

  private void transformErrorEventDefinition(
      final TransformContext context,
      final ExecutableEndEvent executableElement,
      final ErrorEventDefinition errorEventDefinition) {

    final var error = errorEventDefinition.getError();
    final var executableError = context.getError(error.getId());
    executableElement.setError(executableError);
    executableElement.setEventType(BpmnEventType.ERROR);
  }

  private boolean hasTaskDefinition(final EndEvent element) {
    return element.getSingleExtensionElement(ZeebeTaskDefinition.class) != null;
  }

  private void transformEscalationEventDefinition(
      final TransformContext context,
      final ExecutableEndEvent executableElement,
      final EscalationEventDefinition escalationEventDefinition) {

    final var escalation = escalationEventDefinition.getEscalation();
    final var executableEscalation = context.getEscalation(escalation.getId());
    executableElement.setEscalation(executableEscalation);
    executableElement.setEventType(BpmnEventType.ESCALATION);
  }

  private void transformSignalEventDefinition(
      final TransformContext context,
      final ExecutableEndEvent executableElement,
      final SignalEventDefinition signalEventDefinition) {

    final var signal = signalEventDefinition.getSignal();
    final var executableSignal = context.getSignal(signal.getId());
    executableElement.setSignal(executableSignal);
    executableElement.setEventType(BpmnEventType.SIGNAL);
  }

  private void transformCompensationEventDefinition(
      final TransformContext context,
      final ExecutableEndEvent executableElement,
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
