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
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.search.sort.SortOption;

public record ProcessDefinitionFlowNodeStatisticsQuery(ProcessDefinitionStatisticsFilter filter)
    implements TypedSearchAggregationQuery<
        ProcessDefinitionStatisticsFilter,
        SortOption,
        ProcessDefinitionFlowNodeStatisticsAggregation> {

  @Override
  public SortOption sort() {
    return NoSort.NO_SORT;
  }

  @Override
  public ProcessDefinitionFlowNodeStatisticsAggregation aggregation() {
    return new ProcessDefinitionFlowNodeStatisticsAggregation(filter);
  }

  @Override
  public SearchQueryPage page() {
    return SearchQueryPage.NO_ENTITIES_QUERY;
  }
}
