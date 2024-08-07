/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.element;

import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.event.BpmnCatchEventGeneratorFactory;
import java.util.List;

public class BpmnElementGeneratorFactory {

  private final GeneratorContext generatorContext;
  private final List<BpmnElementGenerator> bpmnElementGenerators;

  public BpmnElementGeneratorFactory(
      final GeneratorContext generatorContext,
      final BpmnCatchEventGeneratorFactory catchEventGeneratorFactory) {
    this.generatorContext = generatorContext;
    bpmnElementGenerators =
        List.of(
            new ServiceTaskGenerator(generatorContext),
            new UserTaskGenerator(generatorContext),
            new UndefinedTaskGenerator(generatorContext),
            new IntermediateCatchEventGenerator(generatorContext, catchEventGeneratorFactory));
  }

  public BpmnElementGenerator getGenerator() {
    final var randomIndex = generatorContext.getRandomNumber(bpmnElementGenerators.size());
    return bpmnElementGenerators.get(randomIndex);
  }
}
