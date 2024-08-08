/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.event;

import io.camunda.process.generator.GeneratorContext;
import java.util.List;

public class BpmnCatchEventGeneratorFactory {

  private final GeneratorContext generatorContext;

  private final List<BpmnCatchEventGenerator> generators =
      List.of(new MessageCatchEventGenerator(), new SignalCatchEventGenerator());
  private final List<BpmnThrowEventGenerator> compensationGenerators =
      List.of(new CompensationCatchEventGenerator());

  public BpmnCatchEventGeneratorFactory(final GeneratorContext generatorContext) {
    this.generatorContext = generatorContext;
  }

  public BpmnCatchEventGenerator getGenerator() {
    final int randomIndex = generatorContext.getRandomNumber(generators.size());
    return generators.get(randomIndex);
  }

  public BpmnThrowEventGenerator getCompensationGenerator() {
    final int randomIndex = generatorContext.getRandomNumber(compensationGenerators.size());
    return compensationGenerators.get(randomIndex);
  }
}
