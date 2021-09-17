/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;

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

    if (isMessageEvent(element) && hasTaskDefinition(element)) {
      jobWorkerElementTransformer.transform(element, context);
    }
  }

  private boolean isMessageEvent(final IntermediateThrowEvent element) {
    return element.getEventDefinitions().stream()
        .anyMatch(MessageEventDefinition.class::isInstance);
  }

  private boolean hasTaskDefinition(final IntermediateThrowEvent element) {
    return element.getSingleExtensionElement(ZeebeTaskDefinition.class) != null;
  }
}
