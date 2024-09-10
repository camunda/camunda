/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.context;

import io.camunda.optimize.service.db.report.context.VariableAggregationContext;
import java.util.Map;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

@SuperBuilder
@Data
public class VariableAggregationContextOS extends VariableAggregationContext {
  private final Query baseQueryForMinMaxStats;
  private final Map<String, Aggregation> subAggregations;
}
