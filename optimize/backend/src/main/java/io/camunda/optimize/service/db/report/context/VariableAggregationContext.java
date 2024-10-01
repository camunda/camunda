/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.context;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import java.time.ZoneId;
import java.util.Optional;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class VariableAggregationContext {
  private final String variableName;
  private final VariableType variableType;
  private final String variablePath;
  private final String nestedVariableNameField;
  private final String nestedVariableValueFieldLabel;
  private final ZoneId timezone;
  private final CustomBucketDto customBucketDto;
  private final AggregateByDateUnit dateUnit;
  private final String[] indexNames;
  private MinMaxStatDto variableRangeMinMaxStats;
  private final MinMaxStatDto combinedRangeMinMaxStats;
  @NonNull private final FilterContext filterContext;

  public Optional<MinMaxStatDto> getCombinedRangeMinMaxStats() {
    return Optional.ofNullable(combinedRangeMinMaxStats);
  }

  public double getMaxVariableValue() {
    return getCombinedRangeMinMaxStats().orElse(variableRangeMinMaxStats).getMax();
  }
}
