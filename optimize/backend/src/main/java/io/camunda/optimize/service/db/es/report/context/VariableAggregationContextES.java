/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.context;

import io.camunda.optimize.service.db.report.context.VariableAggregationContext;
import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;

@SuperBuilder
@Data
public class VariableAggregationContextES extends VariableAggregationContext {
  private final QueryBuilder baseQueryForMinMaxStats;
  private final List<AggregationBuilder> subAggregations;
}
