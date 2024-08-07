/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.element;

import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.event.BpmnCatchEventGenerator;
import io.camunda.process.generator.event.BpmnCatchEventGeneratorFactory;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.IntermediateCatchEventBuilder;

public class IntermediateCatchEventGenerator implements BpmnElementGenerator {

  private final GeneratorContext generatorContext;
  private final BpmnCatchEventGeneratorFactory catchEventGeneratorFactory;

  public IntermediateCatchEventGenerator(
      final GeneratorContext generatorContext,
      final BpmnCatchEventGeneratorFactory catchEventGeneratorFactory) {
    this.generatorContext = generatorContext;
    this.catchEventGeneratorFactory = catchEventGeneratorFactory;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElement(
      final AbstractFlowNodeBuilder<?, ?> processBuilder) {
    final String elementId = generatorContext.createNewId();

    final IntermediateCatchEventBuilder intermediateCatchEventBuilder =
        processBuilder.intermediateCatchEvent();

    intermediateCatchEventBuilder.id(elementId).name(elementId);

    final BpmnCatchEventGenerator catchEventGenerator = catchEventGeneratorFactory.getGenerator();
    catchEventGenerator.addEventDefinition(elementId, intermediateCatchEventBuilder);

    return intermediateCatchEventBuilder;
  }
}
