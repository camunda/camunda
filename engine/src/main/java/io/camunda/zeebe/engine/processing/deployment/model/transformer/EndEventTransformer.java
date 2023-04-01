/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
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

    if (isMessageEvent(element)) {
      endEvent.setEventType(BpmnEventType.MESSAGE);
      if (hasTaskDefinition(element)) {
        jobWorkerElementTransformer.transform(element, context);
      }
    }
  }

  private void transformEventDefinition(
      final EndEvent element,
      final TransformContext context,
      final ExecutableEndEvent executableElement) {

    final var eventDefinition = element.getEventDefinitions().iterator().next();

    if (eventDefinition instanceof ErrorEventDefinition) {
      transformErrorEventDefinition(
          context, executableElement, (ErrorEventDefinition) eventDefinition);

    } else if (eventDefinition instanceof TerminateEventDefinition) {
      executableElement.setTerminateEndEvent(true);
      executableElement.setEventType(BpmnEventType.TERMINATE);
    } else if (eventDefinition instanceof EscalationEventDefinition) {
      transformEscalationEventDefinition(
          context, executableElement, (EscalationEventDefinition) eventDefinition);
    } else if (eventDefinition instanceof SignalEventDefinition) {
      transformSignalEventDefinition(
          context, executableElement, (SignalEventDefinition) eventDefinition);
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

  private boolean isMessageEvent(final EndEvent element) {
    return element.getEventDefinitions().stream()
        .anyMatch(MessageEventDefinition.class::isInstance);
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
}
