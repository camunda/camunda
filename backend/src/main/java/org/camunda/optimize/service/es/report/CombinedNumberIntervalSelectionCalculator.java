/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import org.apache.commons.lang3.Range;

import java.util.Comparator;
import java.util.Optional;

public class CombinedNumberIntervalSelectionCalculator extends AbstractCombinedIntervalSelectionCalculator<Double> {

  @Override
  public Optional<Range<Double>> calculateInterval() {
    Optional<Double> max =
      minMaxCommandStats.stream()
        .filter(s -> s.getMaxFieldCount() > 0)
        .max(Comparator.comparing(MinMaxStatDto::getMax))
        .map(MinMaxStatDto::getMax);
    Optional<Double> min =
      minMaxCommandStats.stream()
        .filter(s -> s.getMinFieldCount() > 0)
        .min(Comparator.comparing(MinMaxStatDto::getMin))
        .map(MinMaxStatDto::getMin);
    if (max.isPresent() && min.isPresent()) {
      Range<Double> range = Range.between(min.get(), max.get());
      return Optional.of(range);
    }
    return Optional.empty();
  }
}
