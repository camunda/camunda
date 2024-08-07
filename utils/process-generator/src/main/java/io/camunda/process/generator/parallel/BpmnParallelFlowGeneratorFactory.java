/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.parallel;

import io.camunda.process.generator.BpmnFactories;
import io.camunda.process.generator.GeneratorContext;
import java.util.List;

public class BpmnParallelFlowGeneratorFactory {
  private final GeneratorContext generatorContext;
  private final List<BpmnParallelFlowGenerator> parallelFlowGenerators;

  public BpmnParallelFlowGeneratorFactory(
      final GeneratorContext generatorContext, final BpmnFactories bpmnFactories) {
    this.generatorContext = generatorContext;
    parallelFlowGenerators =
        List.of(new BpmnMultipleOutgoingSequenceFlowsGenerator(generatorContext, bpmnFactories));
  }

  public BpmnParallelFlowGenerator getGenerator() {
    final var randomIndex = generatorContext.getRandomNumber(parallelFlowGenerators.size());
    return parallelFlowGenerators.get(randomIndex);
  }
}
