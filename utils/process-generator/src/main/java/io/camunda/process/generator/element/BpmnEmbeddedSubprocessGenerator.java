/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.element;

import io.camunda.process.generator.BpmnFactories;
import io.camunda.process.generator.GeneratorContext;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;

public class BpmnEmbeddedSubprocessGenerator extends BpmnNestingElementGenerator {

  private final BpmnFactories bpmnFactories;

  public BpmnEmbeddedSubprocessGenerator(
      final GeneratorContext generatorContext, final BpmnFactories bpmnFactories) {
    super(generatorContext);
    this.bpmnFactories = bpmnFactories;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addNestingElement(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {
    generatorContext.incrementCurrentDepth();

    final String elementId = generatorContext.createNewId();

    AbstractFlowNodeBuilder<?, ?> subprocessBuilder =
        processBuilder.subProcess(elementId).name(elementId).embeddedSubProcess().startEvent();

    final var templateGenerator = bpmnFactories.getTemplateGeneratorFactory().getGenerator();
    subprocessBuilder = templateGenerator.addElements(subprocessBuilder, generateExecutionPath);

    generatorContext.decrementCurrentDepth();
    return subprocessBuilder.endEvent().subProcessDone();
  }
}
