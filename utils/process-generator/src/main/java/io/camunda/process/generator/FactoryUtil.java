/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator;

import java.util.HashSet;
import java.util.List;

public class FactoryUtil {

  public static <T extends BpmnFeatureGenerator> T getGenerator(
      final List<T> generators, final GeneratorContext generatorContext) {

    final List<T> allowedGenerators =
        generators.stream()
            .filter(
                generator ->
                    !generatorContext
                        .getGeneratorConfiguration()
                        .getExcludeFeatures()
                        .contains(generator.getFeature()))
            .toList();

    final HashSet<BpmnFeature> missingFeatures =
        new HashSet<>(generatorContext.getGeneratorConfiguration().getIncludeFeatures());
    missingFeatures.removeAll(generatorContext.getProcessFeatures());

    final List<T> generatorsWithFeature =
        allowedGenerators.stream()
            .filter(generator -> missingFeatures.contains(generator.getFeature()))
            .toList();

    final var possibleGenerators =
        generatorsWithFeature.isEmpty() ? allowedGenerators : generatorsWithFeature;

    final var randomIndex = generatorContext.getRandomNumber(possibleGenerators.size());
    final var selectedGenerator = possibleGenerators.get(randomIndex);

    final BpmnFeature feature = selectedGenerator.getFeature();
    if (feature != BpmnFeature.NONE) {
      generatorContext.addProcessFeature(feature);
    }

    return selectedGenerator;
  }
}
