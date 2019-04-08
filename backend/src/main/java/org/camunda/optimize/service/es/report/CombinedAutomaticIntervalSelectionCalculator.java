/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import org.apache.commons.lang3.Range;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CombinedAutomaticIntervalSelectionCalculator {

  private DateTimeFormatter dateTimeFormatter;

  private List<Stats> minMaxCommandStats = new ArrayList<>();

  public CombinedAutomaticIntervalSelectionCalculator(DateTimeFormatter dateTimeFormatter) {
    this.dateTimeFormatter = dateTimeFormatter;
  }

  public void addStat(Stats minMaxStat) {
    minMaxCommandStats.add(minMaxStat);
  }

  public Optional<Range<OffsetDateTime>> calculateInterval() {
    List<Stats> allStats = minMaxCommandStats.stream()
      .filter(s -> s.getCount() > 1)
      .collect(Collectors.toList());
    Optional<OffsetDateTime> max =
      allStats.stream().max(Comparator.comparing(Stats::getMax))
        .map(Stats::getMaxAsString)
        .map(m -> OffsetDateTime.parse(m, dateTimeFormatter));
    Optional<OffsetDateTime> min = allStats.stream().min(Comparator.comparing(Stats::getMin))
      .map(Stats::getMinAsString)
      .map(m -> OffsetDateTime.parse(m, dateTimeFormatter));
    if (max.isPresent() && min.isPresent()) {
      Range<OffsetDateTime> range = Range.between(min.get(), max.get());
      return Optional.of(range);
    }
    return Optional.empty();
  }


}
