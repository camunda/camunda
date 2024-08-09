/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.template;

import io.camunda.process.generator.BpmnFactories;
import io.camunda.process.generator.FactoryUtil;
import io.camunda.process.generator.GeneratorContext;
import java.util.List;

public class BpmnTemplateGeneratorFactory {

  private final GeneratorContext generatorContext;
  private final List<BpmnTemplateGenerator> middleTemplateGenerators;
  private final List<BpmnTemplateGenerator> finalTemplateGenerators;

  public BpmnTemplateGeneratorFactory(
      final GeneratorContext generatorContext, final BpmnFactories bpmnFactories) {
    this.generatorContext = generatorContext;
    final var elementSequenceGenerator =
        new BpmnElementSequenceGenerator(
            generatorContext, bpmnFactories.getElementGeneratorFactory());
    middleTemplateGenerators =
        List.of(
            elementSequenceGenerator,
            new BpmnExclusiveGatewayGenerator(generatorContext, elementSequenceGenerator),
            new ParallelGatewayGenerator(generatorContext, bpmnFactories),
            new BoundaryEventTemplate(generatorContext, bpmnFactories),
            new BpmnEmbeddedSubprocessTemplate(generatorContext, bpmnFactories));
    finalTemplateGenerators =
        List.of(
            new BpmnEndEventTemplate(generatorContext),
            new BpmnTerminateEndEventTemplate(generatorContext, bpmnFactories),
            new BpmnCompensationEventTemplate(generatorContext, bpmnFactories));
  }

  public BpmnTemplateGenerator getMiddleGenerator() {
    final var generator = FactoryUtil.getGenerator(middleTemplateGenerators, generatorContext);

    if (!generatorContext.canAddBranches() && generator.addsBranches()) {
      return getMiddleGenerator();
    } else if (!generatorContext.canGoDeeper() && generator.addsDepth()) {
      return getMiddleGenerator();
    }

    return generator;
  }

  public BpmnTemplateGenerator getFinalGenerator() {
    final var generator = FactoryUtil.getGenerator(finalTemplateGenerators, generatorContext);

    if (!generatorContext.canAddBranches() && generator.addsBranches()) {
      return getFinalGenerator();
    }

    return generator;
  }
}
