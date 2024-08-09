/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator;

import io.camunda.process.generator.template.BpmnTemplateGenerator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

public class FactoryUtil {

  public static <T extends BpmnFeature> T getGenerator(
      final List<T> generators, final GeneratorContext generatorContext) {

    final List<T> allowedGenerators =
        generators.stream()
            .filter(
                generator ->
                    !generatorContext
                        .getGeneratorConfiguration()
                        .getExcludeFeatures()
                        .contains(generator.getFeature()))
            .filter(addsDepthAndIsAllowedTo(generatorContext))
            .filter(addsBranchesAndIsAllowedTo(generatorContext))
            .toList();

    final HashSet<BpmnFeatureType> missingFeatures =
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

    final BpmnFeatureType feature = selectedGenerator.getFeature();
    if (feature != BpmnFeatureType.NONE) {
      generatorContext.addProcessFeature(feature);
    }

    return selectedGenerator;
  }

  private static <T extends BpmnFeature> Predicate<T> addsDepthAndIsAllowedTo(
      final GeneratorContext generatorContext) {
    return generator -> {
      if (!(generator instanceof BpmnTemplateGenerator)) {
        return true;
      }
      return generatorContext.canGoDeeper() || !((BpmnTemplateGenerator) generator).addsDepth();
    };
  }

  private static <T extends BpmnFeature> Predicate<T> addsBranchesAndIsAllowedTo(
      final GeneratorContext generatorContext) {
    return generator -> {
      if (!(generator instanceof BpmnTemplateGenerator)) {
        return true;
      }
      return generatorContext.canAddBranches()
          || !((BpmnTemplateGenerator) generator).addsBranches();
    };
  }
}
