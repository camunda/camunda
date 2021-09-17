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
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;

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

    if (!element.getEventDefinitions().isEmpty()) {
      transformEventDefinition(element, context, endEvent);
    }

    if (isMessageEvent(element) && hasTaskDefinition(element)) {
      jobWorkerElementTransformer.transform(element, context);
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
    }
  }

  private void transformErrorEventDefinition(
      final TransformContext context,
      final ExecutableEndEvent executableElement,
      final ErrorEventDefinition errorEventDefinition) {

    final var error = errorEventDefinition.getError();
    final var executableError = context.getError(error.getId());
    executableElement.setError(executableError);
  }

  private boolean isMessageEvent(final EndEvent element) {
    return element.getEventDefinitions().stream()
        .anyMatch(MessageEventDefinition.class::isInstance);
  }

  private boolean hasTaskDefinition(final EndEvent element) {
    return element.getSingleExtensionElement(ZeebeTaskDefinition.class) != null;
  }
}
