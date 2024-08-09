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
import java.util.List;

public class BpmnElementGeneratorFactory {

  private final GeneratorContext generatorContext;
  private final List<BpmnGenerator> bpmnElementGenerators;
  private final List<BpmnGenerator> activityForBoundaryEventGenerators;
  private final List<BpmnGenerator> activityForCompensationEventGenerators;
  private final List<BpmnGenerator> compensationEventGenerators;

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

  public BpmnGenerator getGenerator() {
    return FactoryUtil.getGenerator(bpmnElementGenerators, generatorContext);
  }

  public BpmnGenerator getGeneratorForActivityWithBoundaryEvent() {
    return FactoryUtil.getGenerator(activityForBoundaryEventGenerators, generatorContext);
  }

  public BpmnGenerator getGeneratorForActivityWithCompensationEvent() {
    return FactoryUtil.getGenerator(activityForCompensationEventGenerators, generatorContext);
  }

  public BpmnGenerator getGeneratorForCompensationEvent() {
    final var randomIndex = generatorContext.getRandomNumber(compensationEventGenerators.size());
    return compensationEventGenerators.get(randomIndex);
  }
}
