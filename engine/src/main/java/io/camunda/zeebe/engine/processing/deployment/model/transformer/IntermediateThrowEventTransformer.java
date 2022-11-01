/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableIntermediateThrowEvent;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.camunda.zeebe.model.bpmn.instance.LinkEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
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

    if (isMessageEvent(element) && hasTaskDefinition(element)) {
      throwEvent.setEventType(BpmnEventType.MESSAGE);
      jobWorkerElementTransformer.transform(element, context);
    } else if (isLinkEvent(element)) {
      transformLinkEventDefinition(element, context, throwEvent);
    }
  }

  private boolean isMessageEvent(final IntermediateThrowEvent element) {
    return element.getEventDefinitions().stream()
        .anyMatch(MessageEventDefinition.class::isInstance);
  }

  private boolean isLinkEvent(final IntermediateThrowEvent element) {
    return element.getEventDefinitions().stream().anyMatch(LinkEventDefinition.class::isInstance);
  }

  private boolean hasTaskDefinition(final IntermediateThrowEvent element) {
    return element.getSingleExtensionElement(ZeebeTaskDefinition.class) != null;
  }

  private void transformLinkEventDefinition(
      final IntermediateThrowEvent element,
      final TransformContext context,
      final ExecutableIntermediateThrowEvent executableThrowEventElement) {

    final var eventDefinition =
        (LinkEventDefinition) element.getEventDefinitions().iterator().next();

    final var name = eventDefinition.getName();
    final var executableLink = context.getLink(name);
    executableThrowEventElement.setLink(executableLink);
    executableThrowEventElement.setEventType(BpmnEventType.LINK);
  }
}
