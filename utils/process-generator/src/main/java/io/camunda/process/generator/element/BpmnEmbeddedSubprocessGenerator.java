/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.element;

import io.camunda.process.generator.BpmnFactories;
import io.camunda.process.generator.BpmnFeature;
import io.camunda.process.generator.GeneratorContext;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BpmnEmbeddedSubprocessGenerator extends BpmnNestingElementGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(BpmnEmbeddedSubprocessGenerator.class);

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

    LOG.debug("Adding embedded subprocess with id {}", elementId);

    AbstractFlowNodeBuilder<?, ?> subprocessBuilder =
        processBuilder.subProcess(elementId).name(elementId).embeddedSubProcess().startEvent();

    final var templateGenerator = bpmnFactories.getTemplateGeneratorFactory().getMiddleGenerator();
    subprocessBuilder = templateGenerator.addElements(subprocessBuilder, generateExecutionPath);

    final var finalTemplateGenerator =
        bpmnFactories.getTemplateGeneratorFactory().getFinalGenerator();
    subprocessBuilder =
        finalTemplateGenerator.addElements(subprocessBuilder, generateExecutionPath);

    generatorContext.decrementCurrentDepth();
    return subprocessBuilder.subProcessDone();
  }

  @Override
  public BpmnFeature getFeature() {
    return BpmnFeature.EMBEDDED_SUBPROCESS;
  }
}
