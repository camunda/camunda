/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.event;

import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.execution.PublishMessageStep;
import io.camunda.zeebe.model.bpmn.builder.AbstractCatchEventBuilder;

public class MessageCatchEventGenerator implements BpmnCatchEventGenerator {

  @Override
  public void addEventDefinition(
      final String elementId,
      final AbstractCatchEventBuilder<?, ?> catchEventBuilder,
      final GeneratorContext generatorContext,
      final boolean generateExecutionPath) {

    catchEventBuilder.message(
        messageBuilder ->
            messageBuilder
                .name("message_" + elementId)
                .zeebeCorrelationKeyExpression("message_" + elementId));

    if (generateExecutionPath) {
      generatorContext.addExecutionStep(new PublishMessageStep(elementId));
    }
  }
}
