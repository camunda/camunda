/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.context;

import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.report.context.DateAggregationContext;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;

@SuperBuilder
@Getter
public class DateAggregationContextES extends DateAggregationContext {
  @NonNull private final List<AggregationBuilder> subAggregations;
  private final DecisionQueryFilterEnhancerES decisionQueryFilterEnhancer;
  private final ProcessQueryFilterEnhancerES processQueryFilterEnhancer;
}
