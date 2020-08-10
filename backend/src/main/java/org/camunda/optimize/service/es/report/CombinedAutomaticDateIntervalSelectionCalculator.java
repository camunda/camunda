/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import org.apache.commons.lang3.Range;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;

public class CombinedAutomaticDateIntervalSelectionCalculator
  extends AbstractCombinedIntervalSelectionCalculator<OffsetDateTime> {

  private DateTimeFormatter dateTimeFormatter;

  public CombinedAutomaticDateIntervalSelectionCalculator(DateTimeFormatter dateTimeFormatter) {
    super();
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  public Optional<Range<OffsetDateTime>> calculateInterval() {
    Optional<OffsetDateTime> max =
      minMaxCommandStats.stream()
        .filter(MinMaxStatDto::isMaxValid)
        .max(Comparator.comparing(MinMaxStatDto::getMax))
        .map(MinMaxStatDto::getMaxAsString)
        .map(m -> OffsetDateTime.parse(m, dateTimeFormatter));
    Optional<OffsetDateTime> min =
      minMaxCommandStats.stream()
        .filter(MinMaxStatDto::isMinValid)
        .min(Comparator.comparing(MinMaxStatDto::getMin))
        .map(MinMaxStatDto::getMinAsString)
        .map(m -> OffsetDateTime.parse(m, dateTimeFormatter));
    if (max.isPresent() && min.isPresent()) {
      Range<OffsetDateTime> range = Range.between(min.get(), max.get());
      return Optional.of(range);
    }
    return Optional.empty();
  }
}
