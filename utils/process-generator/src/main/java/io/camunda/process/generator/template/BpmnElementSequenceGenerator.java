/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.template;

import io.camunda.process.generator.BpmnFeatureType;
import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.element.BpmnElementGeneratorFactory;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BpmnElementSequenceGenerator implements BpmnTemplateGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(BpmnElementSequenceGenerator.class);

  private final GeneratorContext generatorContext;
  private final BpmnElementGeneratorFactory elementGeneratorFactory;

  public BpmnElementSequenceGenerator(
      final GeneratorContext generatorContext,
      final BpmnElementGeneratorFactory elementGeneratorFactory) {
    this.generatorContext = generatorContext;
    this.elementGeneratorFactory = elementGeneratorFactory;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElement(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {
    AbstractFlowNodeBuilder<?, ?> builder = processBuilder;

    final int elementLimit = 5;
    final int numberOfElements = 1 + generatorContext.getRandomNumber(elementLimit);

    LOG.debug("Adding sequence of {} elements", numberOfElements);

    for (int i = 0; i < numberOfElements; i++) {
      final var elementGenerator = elementGeneratorFactory.getGenerator();
      builder = elementGenerator.addElement(builder, generateExecutionPath);
    }

    return builder;
  }

  @Override
  public boolean addsBranches() {
    return false;
  }

  @Override
  public boolean addsDepth() {
    return false;
  }

  @Override
  public BpmnFeatureType getFeature() {
    return BpmnFeatureType.NONE;
  }
}
