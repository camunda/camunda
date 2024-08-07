/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.template;

import io.camunda.process.generator.GeneratorContext;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;

public class BpmnEmbeddedSubprocessGenerator implements BpmnTemplateGenerator {

  private final GeneratorContext generatorContext;
  private final BpmnElementSequenceGenerator elementSequenceGenerator;

  public BpmnEmbeddedSubprocessGenerator(
      final GeneratorContext generatorContext,
      final BpmnElementSequenceGenerator elementSequenceGenerator) {
    this.generatorContext = generatorContext;
    this.elementSequenceGenerator = elementSequenceGenerator;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElements(
      final AbstractFlowNodeBuilder<?, ?> processBuilder) {

    return processBuilder.subProcess(
        generatorContext.createNewId(),
        subProcess -> {
          final var builder = subProcess.embeddedSubProcess().startEvent();
          elementSequenceGenerator.addElements(builder).endEvent();
        });
  }
}
