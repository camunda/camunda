/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.ReportConstants.AVERAGE_AGGREGATION_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MAX_AGGREGATION_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MEDIAN_AGGREGATION_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MIN_AGGREGATION_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.SUM_AGGREGATION_TYPE;

public enum AggregationType {
  AVERAGE(AVERAGE_AGGREGATION_TYPE),
  MIN(MIN_AGGREGATION_TYPE),
  MAX(MAX_AGGREGATION_TYPE),
  MEDIAN(MEDIAN_AGGREGATION_TYPE),
  SUM(SUM_AGGREGATION_TYPE),
  ;

  private final String id;

  public static List<AggregationType> getAggregationTypesAsList() {
    return Arrays.asList(AggregationType.values());
  }

  public static List<AggregationType> getAggregationTypesAsListWithoutSum() {
    return Arrays.stream(AggregationType.values())
      .filter(type -> !AggregationType.SUM.equals(type))
      .collect(Collectors.toList());
  }

  public static List<AggregationType> getAggregationTypesAsListForProcessParts() {
    // process parts does not support the median since it does the result calculation
    // with a script and the script does not allow sorting over all values.
    return Arrays.stream(AggregationType.values())
      .filter(type -> !ImmutableSet.of(AggregationType.SUM, AggregationType.MEDIAN).contains(type))
      .collect(Collectors.toList());
  }

  AggregationType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }
}
