/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.element;

import io.camunda.process.generator.BpmnFactories;
import io.camunda.process.generator.BpmnGenerator;
import io.camunda.process.generator.FactoryUtil;
import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.template.BpmnEmbeddedSubprocessTemplate;
import io.camunda.process.generator.template.BpmnTemplateGenerator;
import java.util.List;

public class BpmnElementGeneratorFactory {

  private final GeneratorContext generatorContext;
  private final List<BpmnElementGenerator> bpmnElementGenerators;
  private final List<BpmnGenerator> activityForBoundaryEventGenerators;
  private final List<BpmnElementGenerator> activityForCompensationEventGenerators;
  private final List<BpmnElementGenerator> compensationEventGenerators;

  public BpmnElementGeneratorFactory(
      final GeneratorContext generatorContext, final BpmnFactories bpmnFactories) {
    this.generatorContext = generatorContext;
    bpmnElementGenerators =
        List.of(
            new ServiceTaskGenerator(generatorContext),
            new UserTaskGenerator(generatorContext),
            new UndefinedTaskGenerator(generatorContext),
            new IntermediateCatchEventGenerator(
                generatorContext, bpmnFactories.getCatchEventGeneratorFactory()));

    activityForBoundaryEventGenerators =
        List.of(
            new ServiceTaskGenerator(generatorContext),
            new UserTaskGenerator(generatorContext),
            new BpmnEmbeddedSubprocessTemplate(generatorContext, bpmnFactories));

    activityForCompensationEventGenerators =
        List.of(
            new ServiceTaskGenerator(generatorContext), new UserTaskGenerator(generatorContext));

    compensationEventGenerators = List.of(new CompensationThrowEventGenerator(generatorContext));
  }

  public BpmnElementGenerator getGenerator() {
    final var generator = FactoryUtil.getGenerator(bpmnElementGenerators, generatorContext);

    // If we are at the maximum depth we should not go deeper. Instead, we use a different
    // generator.
    if (!generatorContext.canGoDeeper()
        && generator instanceof BpmnTemplateGenerator
        && ((BpmnTemplateGenerator) generator).addsDepth()) {
      return getGenerator();
    }
    return generator;
  }

  public BpmnGenerator getGeneratorForActivityWithBoundaryEvent() {
    return FactoryUtil.getGenerator(activityForBoundaryEventGenerators, generatorContext);
  }

  public BpmnElementGenerator getGeneratorForActivityWithCompensationEvent() {
    return FactoryUtil.getGenerator(activityForCompensationEventGenerators, generatorContext);
  }

  public BpmnElementGenerator getGeneratorForCompensationEvent() {
    final var randomIndex = generatorContext.getRandomNumber(compensationEventGenerators.size());
    return compensationEventGenerators.get(randomIndex);
  }
}
