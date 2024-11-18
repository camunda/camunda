/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.range;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.UsageMetricsFilter;
import java.util.List;

public class UsageMetricsFilterTransformer implements FilterTransformer<UsageMetricsFilter> {

  @Override
  public SearchQuery toSearchQuery(final UsageMetricsFilter filter) {
    return range().gte(filter.startTime()).lte(filter.endTime()).build().toSearchQuery();
  }

  @Override
  public List<String> toIndices(final UsageMetricsFilter filter) {
    return List.of("operate-metric-8.3.0_", "tasklist-metric-8.3.0_");
  }
}
