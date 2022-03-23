/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.util;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.filter.FilterContext;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Builder
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
  private final QueryBuilder baseQueryForMinMaxStats;
  private final List<AggregationBuilder> subAggregations;
  private MinMaxStatDto variableRangeMinMaxStats;
  private final MinMaxStatDto combinedRangeMinMaxStats;
  @NonNull
  private final FilterContext filterContext;

  public Optional<MinMaxStatDto> getCombinedRangeMinMaxStats() {
    return Optional.ofNullable(combinedRangeMinMaxStats);
  }

  public double getMaxVariableValue() {
    return getCombinedRangeMinMaxStats().orElse(variableRangeMinMaxStats).getMax();
  }
}
