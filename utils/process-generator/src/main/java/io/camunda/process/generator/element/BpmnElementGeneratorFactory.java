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
import java.util.List;

public class BpmnElementGeneratorFactory {

  private final GeneratorContext generatorContext;
  private final List<BpmnElementGenerator> bpmnElementGenerators;

  public BpmnElementGeneratorFactory(
      final GeneratorContext generatorContext, final BpmnFactories bpmnFactories) {
    this.generatorContext = generatorContext;
    bpmnElementGenerators =
        List.of(
            new ServiceTaskGenerator(generatorContext),
            new UserTaskGenerator(generatorContext),
            new UndefinedTaskGenerator(generatorContext),
            new IntermediateCatchEventGenerator(
                generatorContext, bpmnFactories.getCatchEventGeneratorFactory()),
            new BpmnEmbeddedSubprocessGenerator(generatorContext, bpmnFactories));
  }

  public BpmnElementGenerator getGenerator() {
    final var randomIndex = generatorContext.getRandomNumber(bpmnElementGenerators.size());
    final var generator = bpmnElementGenerators.get(randomIndex);

    // If we are at the maximum depth we should not go deeper. Instead, we use a different
    // generator.
    if (!generatorContext.canGoDeeper() && generator.addsDepth()) {
      return getGenerator();
    }

    return generator;
  }
}
