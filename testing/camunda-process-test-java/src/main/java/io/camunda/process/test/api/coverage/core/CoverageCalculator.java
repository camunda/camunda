/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api.coverage.core;

import io.camunda.process.test.api.coverage.model.Event;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.api.coverage.model.Run;
import io.camunda.process.test.api.coverage.model.Suite;
import java.util.*;
import java.util.stream.Collectors;

public class CoverageCalculator {

  /**
   * Calculates the coverage for the given models.
   *
   * @param run the run to calculate the coverage for
   * @param models the models
   * @return coverage
   */
  public static double calculateCoverage(final Run run, final Collection<Model> models) {
    final List<Model> filteredModels =
        models.stream()
            .filter(
                model -> !run.getEvents(model.getKey()).isEmpty()) // Exclude models with no events
            .collect(Collectors.toList());

    final int totalElementCount =
        filteredModels.stream().mapToInt(Model::getTotalElementCount).sum();

    if (totalElementCount == 0) {
      return 0.0;
    }

    final long coveredElementCount =
        filteredModels.stream()
            .mapToLong(
                model ->
                    run.getEvents(model.getKey()).stream()
                        .map(Event::getDefinitionKey)
                        .distinct()
                        .count())
            .sum();
    return (double) coveredElementCount / totalElementCount;
  }

  /**
   * Calculates the coverage for the given models.
   *
   * @param suite the suite to calculate the coverage for
   * @param models the models
   * @return coverage
   */
  public static double calculateCoverage(final Suite suite, final Collection<Model> models) {
    final Collection<Run> runs = suite.getRuns();
    return runs.stream().mapToDouble(run -> calculateCoverage(run, models)).sum() / runs.size();
  }
}
