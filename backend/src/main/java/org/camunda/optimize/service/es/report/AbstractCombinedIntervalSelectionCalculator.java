/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import org.apache.commons.lang3.Range;
import org.elasticsearch.search.aggregations.metrics.Stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class AbstractCombinedIntervalSelectionCalculator<T> {
  protected List<Stats> minMaxCommandStats = new ArrayList<>();

  public void addStat(Stats minMaxStat) {
    minMaxCommandStats.add(minMaxStat);
  }

  public abstract Optional<Range<T>> calculateInterval();
}
