/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.element;

import io.camunda.process.generator.BpmnFeatureType;
import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.event.BpmnCatchEventGenerator;
import io.camunda.process.generator.event.BpmnCatchEventGeneratorFactory;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.IntermediateCatchEventBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntermediateCatchEventGenerator implements BpmnElementGenerator {
  private static final Logger LOG = LoggerFactory.getLogger(IntermediateCatchEventGenerator.class);

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
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {
    final String elementId = generatorContext.createNewId();

    LOG.debug("Adding intermediate catch event with id {}", elementId);

    final IntermediateCatchEventBuilder intermediateCatchEventBuilder =
        processBuilder.intermediateCatchEvent();

    intermediateCatchEventBuilder.id(elementId).name(elementId);

    final BpmnCatchEventGenerator catchEventGenerator = catchEventGeneratorFactory.getGenerator();
    catchEventGenerator.addEventDefinition(
        elementId, intermediateCatchEventBuilder, generatorContext, generateExecutionPath);

    return intermediateCatchEventBuilder;
  }

  @Override
  public BpmnFeatureType getFeature() {
    return BpmnFeatureType.INTERMEDIATE_CATCH_EVENT;
  }
}
