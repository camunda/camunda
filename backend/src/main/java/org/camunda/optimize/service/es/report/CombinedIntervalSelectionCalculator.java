/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CombinedIntervalSelectionCalculator {
  private final List<MinMaxStatDto> minMaxStats = new ArrayList<>();

  public void addStat(final MinMaxStatDto minMaxStat) {
    minMaxStats.add(minMaxStat);
  }

  public Optional<MinMaxStatDto> getGlobalMinMaxStats() {
    final Optional<MinMaxStatDto> globalMaxStat = minMaxStats.stream()
      .filter(MinMaxStatDto::isMaxValid)
      .max(Comparator.comparing(MinMaxStatDto::getMax));
    final Optional<MinMaxStatDto> globalMinStat = minMaxStats.stream()
      .filter(MinMaxStatDto::isMinValid)
      .min(Comparator.comparing(MinMaxStatDto::getMin));
    if (globalMaxStat.isPresent() && globalMinStat.isPresent()) {
      final MinMaxStatDto minStat = globalMinStat.get();
      final MinMaxStatDto maxStat = globalMaxStat.get();
      return Optional.of(
        new MinMaxStatDto(minStat.getMin(), maxStat.getMax(), minStat.getMinAsString(), maxStat.getMaxAsString())
      );
    }
    return Optional.empty();
  }
}
