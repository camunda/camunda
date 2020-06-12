/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import org.apache.commons.lang3.Range;
import org.elasticsearch.search.aggregations.metrics.Stats;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CombinedNumberIntervalSelectionCalculator extends AbstractCombinedIntervalSelectionCalculator<Double> {

  @Override
  public Optional<Range<Double>> calculateInterval() {
    List<Stats> allStats = minMaxCommandStats.stream()
      .filter(s -> s.getCount() > 1)
      .collect(Collectors.toList());
    Optional<Double> max =
      allStats.stream().max(Comparator.comparing(Stats::getMax))
        .map(Stats::getMax);
    Optional<Double> min = allStats.stream().min(Comparator.comparing(Stats::getMin))
      .map(Stats::getMin);
    if (max.isPresent() && min.isPresent()) {
      Range<Double> range = Range.between(min.get(), max.get());
      return Optional.of(range);
    }
    return Optional.empty();
  }
}
