/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.template;

import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.element.BpmnElementGenerator;
import io.camunda.process.generator.element.BpmnElementGeneratorFactory;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;

public class BpmnElementSequenceGenerator implements BpmnTemplateGenerator {

  private final GeneratorContext generatorContext;
  private final BpmnElementGeneratorFactory elementGeneratorFactory;

  public BpmnElementSequenceGenerator(
      final GeneratorContext generatorContext,
      final BpmnElementGeneratorFactory elementGeneratorFactory) {
    this.generatorContext = generatorContext;
    this.elementGeneratorFactory = elementGeneratorFactory;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElements(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {
    AbstractFlowNodeBuilder<?, ?> builder = processBuilder;

    final int elementLimit = 10;

    for (int i = 0; i < generatorContext.getRandomNumber(elementLimit); i++) {
      final BpmnElementGenerator elementGenerator = elementGeneratorFactory.getGenerator();
      builder = elementGenerator.addElement(builder, generateExecutionPath);
    }

    return builder;
  }
}
