/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.context;

import io.camunda.optimize.service.db.os.report.filter.DecisionQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.report.context.DateAggregationContext;
import java.util.Map;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

@SuperBuilder
@Getter
public class DateAggregationContextOS extends DateAggregationContext {
  @NonNull private final Map<String, Aggregation> subAggregations;
  private final DecisionQueryFilterEnhancerOS decisionQueryFilterEnhancer;
}
