/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;

public record ProcessDefinitionFlowNodeStatisticsQuery(ProcessDefinitionStatisticsFilter filter)
    implements TypedSearchAggregationQuery<
        ProcessDefinitionStatisticsFilter, ProcessDefinitionFlowNodeStatisticsAggregation> {

  @Override
  public ProcessDefinitionFlowNodeStatisticsAggregation aggregation() {
    return new ProcessDefinitionFlowNodeStatisticsAggregation();
  }
}
